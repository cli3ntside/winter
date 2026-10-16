package dev.luxnet.winter.client.assistance;

import dev.luxnet.winter.client.WinterConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.glfw.GLFW;

/** Smooth, frame-driven aim assistance for nearby mobs while the use button is held. */
public final class AimAssist {
	private static final double MAX_DISTANCE_SQUARED = 64.0 * 64.0;
	private static final int MIN_FOV = 5;
	private static final int MAX_FOV = 60;
	private static boolean enabled;
	private static int fov = 30;
	private static boolean fovCircleEnabled;
	private static int bindCode = 1000 + GLFW.GLFW_MOUSE_BUTTON_RIGHT;
	private static long previousFrameNanos;
	private static Prediction prediction;
	private static Object motionLevel;
	private static long nextMotionCleanup;
	private static final Map<Integer, MotionSample> MOTION_SAMPLES = new HashMap<>();

	private AimAssist() { }

	public static boolean isEnabled() { return enabled; }
	public static int getBindCode() { return bindCode; }
	public static int getFov() { return fov; }
	public static void setFov(int value) { fov = Math.max(MIN_FOV, Math.min(MAX_FOV, value)); WinterConfig.save(); }
	public static double getFovProgress() { return (fov - MIN_FOV) / (double) (MAX_FOV - MIN_FOV); }
	public static boolean isFovCircleEnabled() { return fovCircleEnabled; }
	public static void setFovCircleEnabled(boolean value) { fovCircleEnabled = value; WinterConfig.save(); }
	public static boolean isMouseBind(int code) { return code >= 1000; }
	public static int getMouseButton(int code) { return code - 1000; }
	public static void setBindCode(int value) { bindCode = value; WinterConfig.save(); }
	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		previousFrameNanos = 0L;
		prediction = null;
		WinterConfig.save();
	}
	public static void toggle() { setEnabled(!enabled); }
	public static Prediction getPrediction() { return prediction; }
	public static void registerWorldRender() {
		WorldRenderEvents.END_MAIN.register(context -> onFrame(Minecraft.getInstance()));
	}

	public static void onFrame(Minecraft minecraft) {
		long now = System.nanoTime();
		double deltaSeconds = previousFrameNanos == 0L ? 1.0 / 60.0
			: Math.min(0.05, Math.max(0.0, (now - previousFrameNanos) / 1_000_000_000.0));
		previousFrameNanos = now;
		if (!enabled || minecraft.player == null || minecraft.level == null || minecraft.screen != null
			|| !isBindDown(minecraft)) {
			prediction = null;
			return;
		}

		var player = minecraft.player;
		if (motionLevel != minecraft.level) {
			motionLevel = minecraft.level;
			MOTION_SAMPLES.clear();
		}
		if (now >= nextMotionCleanup) {
			MOTION_SAMPLES.values().removeIf(sample -> now - sample.lastSeen > 5_000_000_000L);
			nextMotionCleanup = now + 1_000_000_000L;
		}
		Vec3 eye = player.getEyePosition();
		Mob bestTarget = null;
		Vec3 bestPredictedPosition = null;
		float bestYaw = 0.0F;
		float bestPitch = 0.0F;
		double bestAngle = fov;
		double bestDistance = MAX_DISTANCE_SQUARED;

		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof Mob mob) || mob.isRemoved() || !mob.isAlive() || mob.isInvisible()) continue;
			double distanceSquared = player.distanceToSqr(mob);
			if (distanceSquared > MAX_DISTANCE_SQUARED) continue;

			double distance = Math.sqrt(distanceSquared);
			Vec3 predictedPosition = predictPosition(mob, distance, now);
			Vec3 aimPoint = predictedPosition.add(0.0, mob.getBbHeight() * 0.82, 0.0);
			Vec3 direction = aimPoint.subtract(eye);
			double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
			float targetYaw = (float) (Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
			float targetPitch = (float) -Math.toDegrees(Math.atan2(direction.y, horizontal));
			double yawError = wrapDegrees(targetYaw - player.getYRot());
			double pitchError = targetPitch - player.getXRot();
			double angle = Math.sqrt(yawError * yawError + pitchError * pitchError);
			if (angle < bestAngle || (Math.abs(angle - bestAngle) < 0.01 && distanceSquared < bestDistance)) {
				bestTarget = mob;
				bestPredictedPosition = predictedPosition;
				bestYaw = targetYaw;
				bestPitch = targetPitch;
				bestAngle = angle;
				bestDistance = distanceSquared;
			}
		}

		if (bestTarget == null) {
			prediction = null;
			return;
		}
		prediction = new Prediction(bestTarget, bestPredictedPosition);
		if (deltaSeconds <= 0.0) return;
		double response = 7.0 + ThreadLocalRandom.current().nextDouble(-0.18, 0.18);
		float factor = (float) (1.0 - Math.exp(-response * deltaSeconds));
		float yawDelta = (float) wrapDegrees(bestYaw - player.getYRot());
		float pitchDelta = bestPitch - player.getXRot();
		player.setYRot(player.getYRot() + yawDelta * factor);
		player.setXRot(Math.max(-90.0F, Math.min(90.0F, player.getXRot() + pitchDelta * factor)));
	}

	private static Vec3 predictPosition(Mob mob, double distance, long now) {
		Vec3 observedVelocity = getObservedVelocity(mob, now);
		Vec3 packetVelocity = mob.getDeltaMovement();
		Vec3 tickVelocity = new Vec3(mob.getX() - mob.xo, 0.0, mob.getZ() - mob.zo);
		Vec3 velocity;
		if (tickVelocity.lengthSqr() > 0.0004) {
			// Position deltas are available even when the server does not send a useful motion vector.
			velocity = tickVelocity;
			if (observedVelocity.lengthSqr() > 0.0004)
				velocity = velocity.scale(0.7).add(observedVelocity.scale(0.3));
		} else if (observedVelocity.lengthSqr() > 0.0004) {
			velocity = observedVelocity;
		} else {
			velocity = new Vec3(packetVelocity.x, 0.0, packetVelocity.z);
		}

		Path path = mob.getNavigation().getPath();
		if (path != null && !path.isDone()) {
			Vec3 nextPoint = path.getNextEntityPos(mob).subtract(mob.position());
			Vec3 pathDirection = new Vec3(nextPoint.x, 0.0, nextPoint.z);
			if (pathDirection.lengthSqr() > 0.0025) {
				pathDirection = pathDirection.normalize();
				double speed = velocity.length();
				if (speed < 0.02) {
					speed = Math.max(0.04, Math.min(0.32, mob.getSpeed()));
					velocity = pathDirection.scale(speed);
				}
			}
		}

		// Include the smooth-turn response delay plus a small distance-based hit-registration delay.
		double leadTicks = Math.min(10.0, 3.5 + distance * 0.09);
		Vec3 displacement = velocity.scale(leadTicks);
		// Clamp only extreme network spikes; ordinary fast movement keeps its full lead.
		double maxLead = Math.min(5.0, 0.75 + distance * 0.11);
		if (displacement.length() > maxLead) displacement = displacement.normalize().scale(maxLead);
		return mob.position().add(displacement);
	}

	private static Vec3 getObservedVelocity(Mob mob, long now) {
		int id = mob.getId();
		Vec3 position = mob.position();
		MotionSample sample = MOTION_SAMPLES.get(id);
		if (sample == null) {
			MOTION_SAMPLES.put(id, new MotionSample(position, now));
			return Vec3.ZERO;
		}
		sample.lastSeen = now;
		long elapsed = now - sample.sampledAt;
		if (elapsed < 60_000_000L) return sample.velocity;

		double seconds = elapsed / 1_000_000_000.0;
		Vec3 delta = position.subtract(sample.position);
		Vec3 measured = new Vec3(delta.x / seconds * 0.05, 0.0, delta.z / seconds * 0.05);
		double speed = measured.length();
		if (speed > 0.75) measured = measured.scale(0.75 / speed);
		sample.position = position;
		sample.sampledAt = now;
		sample.velocity = sample.velocity.scale(0.35).add(measured.scale(0.65));
		return sample.velocity;
	}

	public record Prediction(Mob target, Vec3 position) { }

	private static final class MotionSample {
		private Vec3 position;
		private long sampledAt;
		private long lastSeen;
		private Vec3 velocity = Vec3.ZERO;

		private MotionSample(Vec3 position, long now) {
			this.position = position;
			this.sampledAt = now;
			this.lastSeen = now;
		}
	}

	private static double wrapDegrees(double angle) {
		angle %= 360.0;
		if (angle >= 180.0) angle -= 360.0;
		if (angle < -180.0) angle += 360.0;
		return angle;
	}

	private static boolean isBindDown(Minecraft minecraft) {
		if (isMouseBind(bindCode)) {
			return minecraft.getWindow() != null && GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), getMouseButton(bindCode)) == GLFW.GLFW_PRESS;
		}
		return minecraft.getWindow() != null && InputConstants.isKeyDown(minecraft.getWindow(), bindCode);
	}
}

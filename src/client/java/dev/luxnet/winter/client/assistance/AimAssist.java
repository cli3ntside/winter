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
		Vec3 eye = player.getEyePosition();
		Mob bestTarget = null;
		float bestYaw = 0.0F;
		float bestPitch = 0.0F;
		double bestAngle = fov;
		double bestDistance = MAX_DISTANCE_SQUARED;

		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof Mob mob) || mob.isRemoved() || !mob.isAlive() || mob.isInvisible()) continue;
			double distanceSquared = player.distanceToSqr(mob);
			if (distanceSquared > MAX_DISTANCE_SQUARED) continue;

			double distance = Math.sqrt(distanceSquared);
			Vec3 velocity = mob.getDeltaMovement();
			double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
			double halfMobWidth = Math.max(0.15, mob.getBbWidth() * 0.5);
			Vec3 moveDirection = horizontalSpeed > 0.015
				? new Vec3(velocity.x / horizontalSpeed, 0.0, velocity.z / horizontalSpeed) : Vec3.ZERO;
			Path path = mob.getNavigation().getPath();
			if (path != null && !path.isDone()) {
				Vec3 nextPoint = path.getNextEntityPos(mob).subtract(mob.position());
				Vec3 pathDirection = new Vec3(nextPoint.x, 0.0, nextPoint.z);
				if (pathDirection.lengthSqr() > 0.0025) moveDirection = pathDirection.normalize();
			}
			double leadDistance = moveDirection.lengthSqr() > 0.0
				? Math.min(1.25, Math.max(halfMobWidth, halfMobWidth + distance * 0.035)) : 0.0;
			Vec3 predictedPosition = mob.position().add(moveDirection.scale(leadDistance));
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
		Vec3 velocity = bestTarget.getDeltaMovement();
		double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		Vec3 moveDirection = speed > 0.015 ? new Vec3(velocity.x / speed, 0.0, velocity.z / speed) : Vec3.ZERO;
		Path path = bestTarget.getNavigation().getPath();
		if (path != null && !path.isDone()) {
			Vec3 nextPoint = path.getNextEntityPos(bestTarget).subtract(bestTarget.position());
			Vec3 pathDirection = new Vec3(nextPoint.x, 0.0, nextPoint.z);
			if (pathDirection.lengthSqr() > 0.0025) moveDirection = pathDirection.normalize();
		}
		double predictedLead = moveDirection.lengthSqr() > 0.0
			? Math.min(1.25, Math.max(bestTarget.getBbWidth() * 0.5,
				bestTarget.getBbWidth() * 0.5 + bestTarget.distanceTo(player) * 0.035)) : 0.0;
		prediction = new Prediction(bestTarget, bestTarget.position().add(moveDirection.scale(predictedLead)));
		if (deltaSeconds <= 0.0) return;
		double response = 7.0 + ThreadLocalRandom.current().nextDouble(-0.18, 0.18);
		float factor = (float) (1.0 - Math.exp(-response * deltaSeconds));
		float yawDelta = (float) wrapDegrees(bestYaw - player.getYRot());
		float pitchDelta = bestPitch - player.getXRot();
		player.setYRot(player.getYRot() + yawDelta * factor);
		player.setXRot(Math.max(-90.0F, Math.min(90.0F, player.getXRot() + pitchDelta * factor)));
	}

	public record Prediction(Mob target, Vec3 position) { }

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

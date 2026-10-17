package dev.luxnet.winter.client.assistance;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luxnet.winter.client.WinterConfig;
import dev.luxnet.winter.client.WeaponCatalog;
import dev.luxnet.winter.client.mixin.KeyMappingAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.TimeUnit;

/** Emulates holding the normal use key while aiming at a hostile mob with a ranged weapon. */
public final class Triggerbot {
	private static boolean enabled;
	private static boolean emulatingUse;
	private static boolean onKeyMode;
	private static boolean throughWalls;
	private static int activationBind = GLFW.GLFW_KEY_UNKNOWN;
	private static int delayMs;
	private static int candidateEntityId = -1;
	private static long candidateSince;

	private Triggerbot() { }

	public static boolean isEnabled() { return enabled; }
	public static boolean isEmulatingUse() { return emulatingUse; }
	public static boolean isOnKeyMode() { return onKeyMode; }
	public static boolean isThroughWalls() { return throughWalls; }
	public static void setThroughWalls(boolean value) {
		if (throughWalls == value) return;
		throughWalls = value;
		resetCandidate();
		WinterConfig.save();
	}
	public static void setOnKeyMode(boolean value) {
		if (onKeyMode == value) return;
		onKeyMode = value;
		resetCandidate();
		WinterConfig.save();
	}
	public static int getActivationBind() { return activationBind; }
	public static void setActivationBind(int value) { activationBind = value; resetCandidate(); WinterConfig.save(); }
	public static int getDelayMs() { return delayMs; }
	public static void setDelayMs(int value) { delayMs = Math.max(0, Math.min(500, value)); resetCandidate(); WinterConfig.save(); }
	public static double getDelayProgress() { return delayMs / 500.0; }
	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		resetCandidate();
		if (!value) releaseUse(Minecraft.getInstance());
		WinterConfig.save();
	}
	public static void toggle() { setEnabled(!enabled); }

	public static void register() {
		WorldRenderEvents.END_MAIN.register(context -> update(Minecraft.getInstance()));
		ClientTickEvents.END_CLIENT_TICK.register(Triggerbot::releaseIfUnavailable);
	}

	private static void update(Minecraft minecraft) {
		if (!enabled || minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
			releaseUse(minecraft);
			return;
		}
		ItemStack heldItem = minecraft.player.getMainHandItem();
		boolean hasRangedWeapon = WeaponCatalog.isKnownWeapon(heldItem) && !WeaponCatalog.isMeleeWeapon(heldItem);
		Monster target = findTarget(minecraft);
		boolean aimedAtMob = target != null;
		boolean activationHeld = !onKeyMode || isActivationBindDown(minecraft);
		if (!hasRangedWeapon || !aimedAtMob || !activationHeld) {
			resetCandidate();
			releaseUse(minecraft);
			return;
		}

		long now = System.nanoTime();
		if (candidateEntityId != target.getId()) {
			candidateEntityId = target.getId();
			candidateSince = now;
		}
		if (now - candidateSince >= TimeUnit.MILLISECONDS.toNanos(delayMs)) holdUse(minecraft);
		else releaseUse(minecraft);
	}

	private static Monster findTarget(Minecraft minecraft) {
		var player = minecraft.player;
		if (player == null || minecraft.level == null) return null;
		final double maxDistance = 256.0;
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getViewVector(1.0F).scale(maxDistance));
		AABB searchBox = new AABB(start, end).inflate(1.0);
		Monster nearest = null;
		double nearestDistance = maxDistance;
		for (Entity entity : minecraft.level.getEntities(player, searchBox,
			candidate -> candidate instanceof Monster monster && monster.isAlive() && !monster.isRemoved())) {
			AABB targetBox = entity.getBoundingBox().inflate(0.3);
			var intersection = targetBox.clip(start, end);
			if (intersection.isEmpty()) continue;
			double distance = start.distanceToSqr(intersection.get());
			if (distance < nearestDistance * nearestDistance) {
				nearestDistance = Math.sqrt(distance);
				nearest = (Monster) entity;
			}
		}
		if (nearest == null || throughWalls) return nearest;

		HitResult blockHit = player.pick(maxDistance, 1.0F, false);
		if (blockHit.getType() != HitResult.Type.MISS
			&& start.distanceToSqr(blockHit.getLocation()) < nearestDistance * nearestDistance) return null;
		return nearest;
	}

	private static void releaseIfUnavailable(Minecraft minecraft) {
		if (!enabled || minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
			resetCandidate();
			releaseUse(minecraft);
		}
	}

	private static void holdUse(Minecraft minecraft) {
		if (minecraft.options == null) return;
		minecraft.options.keyUse.setDown(true);
		emulatingUse = true;
	}

	private static boolean isActivationBindDown(Minecraft minecraft) {
		if (activationBind == GLFW.GLFW_KEY_UNKNOWN || minecraft.getWindow() == null) return false;
		if (isMouseBind(activationBind)) {
			return GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), getMouseButton(activationBind)) == GLFW.GLFW_PRESS;
		}
		return InputConstants.isKeyDown(minecraft.getWindow(), activationBind);
	}

	public static boolean isMouseBind(int code) { return code >= 1000; }
	public static int getMouseButton(int code) { return code - 1000; }

	private static void resetCandidate() {
		candidateEntityId = -1;
		candidateSince = 0L;
	}

	private static void releaseUse(Minecraft minecraft) {
		if (!emulatingUse || minecraft.options == null) return;
		KeyMapping use = minecraft.options.keyUse;
		use.setDown(isPhysicallyDown(minecraft, use));
		emulatingUse = false;
	}

	private static boolean isPhysicallyDown(Minecraft minecraft, KeyMapping mapping) {
		if (minecraft.getWindow() == null) return false;
		InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).winter$getBoundKey();
		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
		}
		return InputConstants.isKeyDown(minecraft.getWindow(), key.getValue());
	}
}

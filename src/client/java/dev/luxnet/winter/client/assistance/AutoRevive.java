package dev.luxnet.winter.client.assistance;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luxnet.winter.client.WinterConfig;
import dev.luxnet.winter.client.visuals.TeamEsp;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Holds the regular sneak key while a nearby teammate has a revive hologram. */
public final class AutoRevive {
	private static final double REVIVE_RADIUS = 1.5;
	private static final double REVIVE_RADIUS_SQUARED = REVIVE_RADIUS * REVIVE_RADIUS;
	private static boolean enabled;
	private static boolean automatingShift;
	private static boolean shiftWasDown;

	private AutoRevive() { }

	public static boolean isEnabled() { return enabled; }
	public static void toggle() { setEnabled(!enabled); }
	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		if (!enabled) releaseShift(Minecraft.getInstance());
		WinterConfig.save();
	}

	public static void tick(Minecraft minecraft) {
		if (!enabled || minecraft.player == null || minecraft.level == null || minecraft.screen != null
			|| !TeamEsp.isZombiesGame(minecraft)) {
			releaseShift(minecraft);
			return;
		}

		boolean shouldHold = shouldRevive(minecraft);
		if (shouldHold) pressShift(minecraft);
		else releaseShift(minecraft);
	}

	private static boolean shouldRevive(Minecraft minecraft) {
		List<Vec3> markers = new ArrayList<>();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (isReviveMarker(entity)) markers.add(entity.position());
		}
		if (markers.isEmpty()) return false;

		Player self = minecraft.player;
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof Player target) || target == self || target.isRemoved() || target.isSpectator()) continue;
			if (target.position().distanceToSqr(self.position()) > REVIVE_RADIUS_SQUARED) continue;
			for (Vec3 marker : markers) {
				double dx = target.getX() - marker.x;
				double dz = target.getZ() - marker.z;
				double markerHeight = marker.y - target.getY();
				if (dx * dx + dz * dz <= 2.25 && markerHeight >= 0.0 && markerHeight <= 4.5) return true;
			}
		}
		return false;
	}

	private static boolean isReviveMarker(Entity entity) {
		String text = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
		if (entity instanceof Display.TextDisplay display) text += " " + display.getText().getString();
		text = text.replaceAll("§.", "").toLowerCase(Locale.ROOT);
		return text.contains("hold sneak to revive") || text.contains("hold shift to revive")
			|| text.contains("sneak to revive") || text.contains("reviving");
	}

	private static void pressShift(Minecraft minecraft) {
		KeyMapping shift = minecraft.options.keyShift;
		if (!automatingShift) {
			shiftWasDown = shift.isDown();
			automatingShift = true;
		}
		shift.setDown(true);
	}

	private static void releaseShift(Minecraft minecraft) {
		if (!automatingShift) return;
		boolean physicallyDown = minecraft.getWindow() != null
			&& (InputConstants.isKeyDown(minecraft.getWindow(), 340)
			|| InputConstants.isKeyDown(minecraft.getWindow(), 344));
		minecraft.options.keyShift.setDown(shiftWasDown || physicallyDown);
		automatingShift = false;
	}
}

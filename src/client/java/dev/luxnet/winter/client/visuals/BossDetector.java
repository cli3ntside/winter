package dev.luxnet.winter.client.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Identifies Zombies bosses by entity type, recognizable names, and Hypixel boss-bar markers. */
final class BossDetector {
	private static final Pattern BAR_TAG = Pattern.compile("\\[\\s*[|｜│]+\\s*]");
	private static final double BAR_XZ_RADIUS_SQUARED = 1.5 * 1.5;
	private static final double BAR_Y_MIN = -0.5;
	private static final double BAR_Y_MAX = 3.5;
	private static final long RESCAN_INTERVAL_MS = 500L;
	private static final Set<Integer> BOSSES = new HashSet<>();
	private static Object scannedLevel;
	private static long nextScanTime;

	private BossDetector() { }

	static boolean isBoss(Entity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != scannedLevel) {
			scannedLevel = minecraft.level;
			BOSSES.clear();
			nextScanTime = 0L;
		}
		if (BOSSES.contains(entity.getId())) return true;
		if (entity instanceof Giant || isOldOne(entity) || hasBossName(entity)) {
			BOSSES.add(entity.getId());
			return true;
		}
		if (System.currentTimeMillis() >= nextScanTime) scanBossBars(minecraft);
		return BOSSES.contains(entity.getId());
	}

	private static boolean isOldOne(Entity entity) {
		if (!(entity instanceof Zombie zombie)) return false;
		return zombie.getMainHandItem().is(Items.DIAMOND_SWORD)
			&& (zombie.isBaby() || zombie.getMaxHealth() >= 100.0F);
	}

	private static boolean hasBossName(Entity entity) {
		String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
		String normalized = name.replaceAll("§.", "").toLowerCase(Locale.ROOT);
		return normalized.contains("boss") || normalized.contains("bombie")
			|| normalized.contains("brood mother") || normalized.contains("old one");
	}

	private static void scanBossBars(Minecraft minecraft) {
		nextScanTime = System.currentTimeMillis() + RESCAN_INTERVAL_MS;
		if (minecraft.level == null) return;
		Set<ArmorStand> bars = new HashSet<>();
		Set<LivingEntity> candidates = new HashSet<>();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof ArmorStand stand && stand.isMarker() && stand.getCustomName() != null
				&& BAR_TAG.matcher(stand.getCustomName().getString().replaceAll("§.", "")).find()) {
				bars.add(stand);
			} else if ((entity instanceof Monster || entity instanceof Wolf) && entity instanceof LivingEntity living) {
				candidates.add(living);
			}
		}

		for (ArmorStand bar : bars) {
			LivingEntity owner = null;
			float highestHealth = -1.0F;
			for (LivingEntity candidate : candidates) {
				double dx = candidate.getX() - bar.getX();
				double dz = candidate.getZ() - bar.getZ();
				if (dx * dx + dz * dz > BAR_XZ_RADIUS_SQUARED) continue;
				double dy = bar.getY() - candidate.getY();
				if (dy < BAR_Y_MIN || dy > BAR_Y_MAX) continue;
				if (candidate.getMaxHealth() > highestHealth) {
					highestHealth = candidate.getMaxHealth();
					owner = candidate;
				}
			}
			if (owner != null) BOSSES.add(owner.getId());
		}
	}
}

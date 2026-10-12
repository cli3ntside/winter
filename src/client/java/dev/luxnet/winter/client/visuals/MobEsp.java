package dev.luxnet.winter.client.visuals;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class MobEsp {
	private static final double SEARCH_RADIUS = 128.0;
	private static final int REFRESH_TICKS = 10;
	private static final int MOB_COLOR = 0xB84DFF88;
	private static final int BOSS_COLOR = 0xB8FF9A35;
	private static final List<Entity> TARGETS = new ArrayList<>();
	private static boolean enabled;
	private static int renderDistance = 64;
	private static int ticksSinceScan;
	private static Object scannedLevel;

	private MobEsp() { }

	public static void registerWorldRender() {
		WorldRenderEvents.END_MAIN.register(MobEsp::render);
	}

	public static boolean isEnabled() { return enabled; }
	public static int getRenderDistance() { return renderDistance; }
	public static void setRenderDistance(int value) { renderDistance = Math.max(16, Math.min(128, value)); }

	public static void toggle() {
		enabled = !enabled;
		if (!enabled) TARGETS.clear();
	}

	public static void tick(Minecraft minecraft) {
		if (!enabled || minecraft.level == null || minecraft.player == null) {
			TARGETS.clear();
			scannedLevel = null;
			return;
		}
		if (scannedLevel != minecraft.level || ++ticksSinceScan >= REFRESH_TICKS) {
			scannedLevel = minecraft.level;
			ticksSinceScan = 0;
			scan(minecraft);
		}
	}

	private static void scan(Minecraft minecraft) {
		TARGETS.clear();
		Vec3 center = minecraft.player.position();
		double distance = Math.min(SEARCH_RADIUS, renderDistance);
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (isMobTarget(entity) && entity.position().distanceToSqr(center) <= distance * distance) {
				TARGETS.add(entity);
			}
		}
	}

	private static boolean isMobTarget(Entity entity) {
		return !entity.isRemoved() && (entity instanceof Monster || entity instanceof Wolf || entity instanceof IronGolem);
	}

	private static void render(WorldRenderContext context) {
		if (!enabled || TARGETS.isEmpty()) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) return;
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		double distance = Math.min(SEARCH_RADIUS, renderDistance);
		for (Entity entity : TARGETS) {
			if (entity.level() != minecraft.level || !isMobTarget(entity)
				|| entity.position().distanceToSqr(camera) > distance * distance) continue;
			int color = BossDetector.isBoss(entity) ? BOSS_COLOR : MOB_COLOR;
			EspBoxRenderer.draw(context, entity.getBoundingBox(), color, 2.0F);
		}
	}
}

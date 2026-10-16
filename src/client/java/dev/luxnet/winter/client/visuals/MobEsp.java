package dev.luxnet.winter.client.visuals;

import dev.luxnet.winter.client.WinterConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class MobEsp {
	private static final double SEARCH_RADIUS = 128.0;
	private static final int REFRESH_TICKS = 10;
	private static final int MOB_COLOR = 0xB84DFF88;
	private static final int BOSS_COLOR = 0xB8FF9A35;
	private static final List<Entity> TARGETS = new ArrayList<>();
	private static final Map<Entity, DamageState> DAMAGE_STATES = new IdentityHashMap<>();
	private static final long DAMAGE_BLINK_DURATION_NS = 500_000_000L;
	private static boolean enabled;
	private static boolean damageBlinkEnabled = true;
	private static int renderDistance = 64;
	private static int ticksSinceScan;
	private static Object scannedLevel;

	private MobEsp() { }

	public static void registerWorldRender() {
		WorldRenderEvents.END_MAIN.register(MobEsp::render);
	}

	public static boolean isEnabled() { return enabled; }
	public static boolean isDamageBlinkEnabled() { return damageBlinkEnabled; }
	public static void setDamageBlinkEnabled(boolean value) {
		if (damageBlinkEnabled == value) return;
		damageBlinkEnabled = value;
		if (!value) DAMAGE_STATES.clear();
		WinterConfig.save();
	}
	public static int getRenderDistance() { return renderDistance; }
	public static void setRenderDistance(int value) {
		int updated = Math.max(16, Math.min(128, value));
		if (renderDistance != updated) { renderDistance = updated; WinterConfig.save(); }
	}

	public static void toggle() {
		setEnabled(!enabled);
	}

	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		if (!enabled) { TARGETS.clear(); DAMAGE_STATES.clear(); }
		WinterConfig.save();
	}

	public static void tick(Minecraft minecraft) {
		if (!enabled || minecraft.level == null || minecraft.player == null) {
			TARGETS.clear();
			DAMAGE_STATES.clear();
			scannedLevel = null;
			return;
		}
		if (scannedLevel != minecraft.level || ++ticksSinceScan >= REFRESH_TICKS) {
			if (scannedLevel != minecraft.level) DAMAGE_STATES.clear();
			scannedLevel = minecraft.level;
			ticksSinceScan = 0;
			scan(minecraft);
		}
		updateDamageStates();
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
		if (entity.isRemoved() || !(entity instanceof LivingEntity living)
			|| living.isDeadOrDying() || living.deathTime > 0) return false;
		return entity instanceof Monster || entity instanceof Wolf || entity instanceof IronGolem;
	}

	private static void updateDamageStates() {
		if (!damageBlinkEnabled) return;
		long now = System.nanoTime();
		for (Entity entity : TARGETS) {
			if (!(entity instanceof LivingEntity living)) continue;
			DamageState state = DAMAGE_STATES.get(entity);
			if (state == null) {
				DAMAGE_STATES.put(entity, new DamageState(living.getHealth(), living.hurtTime));
				continue;
			}
			float health = living.getHealth();
			if (health < state.health || living.hurtTime > state.hurtTime) state.damagedAt = now;
			state.health = health;
			state.hurtTime = living.hurtTime;
		}
		DAMAGE_STATES.keySet().removeIf(entity -> !TARGETS.contains(entity));
	}

	private static void render(WorldRenderContext context) {
		if (!enabled || TARGETS.isEmpty()) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) return;
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		double distance = Math.min(SEARCH_RADIUS, renderDistance);
		float tickDelta = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		for (Entity entity : TARGETS) {
			if (entity.level() != minecraft.level || !isMobTarget(entity)
				|| entity.position().distanceToSqr(camera) > distance * distance) continue;
			int color = BossDetector.isBoss(entity) ? BOSS_COLOR : MOB_COLOR;
			double interpolatedX = entity.xo + (entity.getX() - entity.xo) * tickDelta;
			double interpolatedY = entity.yo + (entity.getY() - entity.yo) * tickDelta;
			double interpolatedZ = entity.zo + (entity.getZ() - entity.zo) * tickDelta;
			var box = entity.getBoundingBox().move(interpolatedX - entity.getX(),
				interpolatedY - entity.getY(), interpolatedZ - entity.getZ());
			if (damageBlinkEnabled && entity instanceof LivingEntity) {
				DamageState state = DAMAGE_STATES.get(entity);
				if (state != null && state.damagedAt > 0) {
					long remaining = DAMAGE_BLINK_DURATION_NS - (System.nanoTime() - state.damagedAt);
					if (remaining > 0) {
						int alpha = (int) (0x80 * (remaining / (double) DAMAGE_BLINK_DURATION_NS));
						EspBoxRenderer.drawFilled(context, box, alpha << 24 | 0x00FF3030);
					}
				}
			}
			EspBoxRenderer.draw(context, box, color, 2.0F);
		}
	}

	private static final class DamageState {
		private float health;
		private int hurtTime;
		private long damagedAt;

		private DamageState(float health, int hurtTime) {
			this.health = health;
			this.hurtTime = hurtTime;
		}
	}
}

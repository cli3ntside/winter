package dev.luxnet.winter.client.visuals;

import dev.luxnet.winter.client.WinterConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

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
	private static int bossNametagDistance = 64;
	private static int normalNametagDistance = 32;
	private static HealthNametagMode healthNametagMode = HealthNametagMode.NONE;
	private static int ticksSinceScan;
	private static Object scannedLevel;

	private MobEsp() { }

	public static void registerWorldRender() {
		WorldRenderEvents.END_MAIN.register(MobEsp::render);
	}

	public static void registerHud() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("winter", "mob_esp_health_labels"), MobEsp::renderHealthNametags);
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
	public static int getBossNametagDistance() { return bossNametagDistance; }
	public static int getNormalNametagDistance() { return normalNametagDistance; }
	public static HealthNametagMode getHealthNametagMode() { return healthNametagMode; }
	public static void cycleHealthNametagMode() {
		HealthNametagMode[] modes = HealthNametagMode.values();
		setHealthNametagMode(modes[(healthNametagMode.ordinal() + 1) % modes.length]);
	}
	public static void setHealthNametagMode(HealthNametagMode value) {
		if (value == null || healthNametagMode == value) return;
		healthNametagMode = value;
		WinterConfig.save();
	}
	public static void loadHealthNametagMode(String value) {
		try { healthNametagMode = HealthNametagMode.valueOf(value); }
		catch (RuntimeException ignored) { healthNametagMode = HealthNametagMode.NONE; }
	}
	public static void setBossNametagDistance(int value) {
		int updated = Math.max(0, Math.min(128, value));
		if (bossNametagDistance != updated) { bossNametagDistance = updated; WinterConfig.save(); }
	}
	public static void setNormalNametagDistance(int value) {
		int updated = Math.max(0, Math.min(128, value));
		if (normalNametagDistance != updated) { normalNametagDistance = updated; WinterConfig.save(); }
	}
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
		double distance = Math.min(SEARCH_RADIUS, Math.max(renderDistance,
			Math.max(bossNametagDistance, normalNametagDistance)));
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
		double boxDistance = Math.min(SEARCH_RADIUS, renderDistance);
		float tickDelta = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		for (Entity entity : TARGETS) {
			if (entity.level() != minecraft.level || !isMobTarget(entity)) continue;
			double cameraDistanceSquared = entity.position().distanceToSqr(camera);
			boolean boss = BossDetector.isBoss(entity);
			double interpolatedX = entity.xo + (entity.getX() - entity.xo) * tickDelta;
			double interpolatedY = entity.yo + (entity.getY() - entity.yo) * tickDelta;
			double interpolatedZ = entity.zo + (entity.getZ() - entity.zo) * tickDelta;
			var box = entity.getBoundingBox().move(interpolatedX - entity.getX(),
				interpolatedY - entity.getY(), interpolatedZ - entity.getZ());
			if (cameraDistanceSquared <= boxDistance * boxDistance && damageBlinkEnabled && entity instanceof LivingEntity) {
				DamageState state = DAMAGE_STATES.get(entity);
				if (state != null && state.damagedAt > 0) {
					long remaining = DAMAGE_BLINK_DURATION_NS - (System.nanoTime() - state.damagedAt);
					if (remaining > 0) {
						int alpha = (int) (0x80 * (remaining / (double) DAMAGE_BLINK_DURATION_NS));
						EspBoxRenderer.drawFilled(context, box, alpha << 24 | 0x00FF3030);
					}
				}
			}
			if (cameraDistanceSquared <= boxDistance * boxDistance)
				EspBoxRenderer.draw(context, box, boss ? BOSS_COLOR : MOB_COLOR, 2.0F);

		}
	}

	/** Projects health tags into the HUD layer so they render through world geometry. */
	private static void renderHealthNametags(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (!enabled || healthNametagMode == HealthNametagMode.NONE || TARGETS.isEmpty()) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().position();
		Matrix4f view = new Matrix4f().rotation(minecraft.gameRenderer.getMainCamera().rotation()).invert();
		Matrix4f projection = minecraft.gameRenderer.getProjectionMatrix(minecraft.options.fov().get());
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		for (Entity entity : TARGETS) {
			if (entity.level() != minecraft.level || !(entity instanceof LivingEntity living) || !isMobTarget(entity)) continue;
			boolean boss = BossDetector.isBoss(entity);
			if (!shouldRenderHealthNametag(boss, entity.position().distanceToSqr(cameraPosition))) continue;
			Vec3 position = entity.getPosition(partialTick);
			Vector4f point = new Vector4f((float) (position.x - cameraPosition.x),
				(float) (position.y + entity.getBbHeight() + 0.3 - cameraPosition.y),
				(float) (position.z - cameraPosition.z), 1.0F);
			view.transform(point);
			projection.transform(point);
			if (point.w <= 0.0F) continue;
			float ndcX = point.x / point.w;
			float ndcY = point.y / point.w;
			float ndcZ = point.z / point.w;
			if (ndcZ < -1.0F || ndcZ > 1.0F) continue;
			int centerX = Math.round((ndcX + 1.0F) * 0.5F * screenWidth);
			int topY = Math.round((1.0F - ndcY) * 0.5F * screenHeight);
			if (centerX < 0 || centerX >= screenWidth || topY < 0 || topY >= screenHeight) continue;
			float maxHealth = living.getMaxHealth();
			int percent = maxHealth <= 0.0F ? 0
				: Math.max(0, Math.min(100, Math.round(living.getHealth() / maxHealth * 100.0F)));
			drawHealthTag(graphics, minecraft, centerX, topY, percent + "%", boss ? 0xFFFFB347 : 0xFF9BFF9B);
		}
	}

	private static void drawHealthTag(GuiGraphics graphics, Minecraft minecraft, int centerX, int topY,
			String text, int color) {
		int textWidth = minecraft.font.width(text);
		int left = centerX - (textWidth + 8) / 2;
		int top = topY - minecraft.font.lineHeight - 4;
		graphics.fill(left, top, left + textWidth + 8, top + minecraft.font.lineHeight + 4, 0x990A0D12);
		graphics.drawString(minecraft.font, text, centerX - textWidth / 2, top + 2, color, true);
	}

	private static boolean shouldRenderHealthNametag(boolean boss, double distanceSquared) {
		if (healthNametagMode == HealthNametagMode.NONE) return false;
		if (healthNametagMode == HealthNametagMode.BOSS && !boss) return false;
		int maxDistance = boss ? bossNametagDistance : normalNametagDistance;
		return distanceSquared <= (double) maxDistance * maxDistance;
	}

	public enum HealthNametagMode { BOSS, ALL, NONE }

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

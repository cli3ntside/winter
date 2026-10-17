package dev.luxnet.winter.client.visuals;

import dev.luxnet.winter.client.WinterConfig;
import dev.luxnet.winter.client.PowerupTracker;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Outlines spawned Zombies power-ups recognized by their armor stand names. */
public final class PowerupEsp {
	private static final int REFRESH_TICKS = 4;
	private static final double MAX_RANGE = 128.0;
	private static final int POWERUP_COLOR = 0xDFFFFFFF;
	private static final List<ArmorStand> TARGETS = new ArrayList<>();
	private static boolean enabled;
	private static int renderDistance = 64;
	private static int ticksSinceScan;
	private static Object scannedLevel;

	private PowerupEsp() { }

	public static void registerWorldRender() {
		WorldRenderEvents.END_MAIN.register(PowerupEsp::render);
	}

	public static void registerHud() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("winter", "powerup_esp_labels"), PowerupEsp::renderLabels);
	}

	public static boolean isEnabled() { return enabled; }
	public static int getRenderDistance() { return renderDistance; }
	public static void setRenderDistance(int value) {
		int updated = Math.max(16, Math.min(128, value));
		if (renderDistance != updated) { renderDistance = updated; WinterConfig.save(); }
	}
	public static void toggle() { setEnabled(!enabled); }
	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		if (!enabled) TARGETS.clear();
		WinterConfig.save();
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
		double distance = Math.min(MAX_RANGE, renderDistance);
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof ArmorStand stand && isPowerup(stand)
				&& stand.position().distanceToSqr(center) <= distance * distance) {
				TARGETS.add(stand);
			}
		}
	}

	private static boolean isPowerup(ArmorStand stand) {
		if (stand.isRemoved() || stand.getCustomName() == null) return false;
		String name = stand.getCustomName().getString().replaceAll("§.", "").trim().toLowerCase(Locale.ROOT);
		return name.contains("insta kill") || name.contains("max ammo") || name.contains("double gold")
			|| name.contains("bonus gold") || name.contains("carpenter") || name.contains("shopping spree")
			|| name.contains("shop spree") || name.contains("秒杀") || name.contains("一擊必殺")
			|| name.contains("瞬间击杀") || name.contains("瞬間擊殺") || name.contains("满弹药")
			|| name.contains("滿彈藥") || name.contains("弹药满载") || name.contains("彈藥滿載")
			|| name.contains("双倍金币") || name.contains("雙倍金幣") || name.contains("双倍金钱")
			|| name.contains("雙倍金錢") || name.contains("额外金币") || name.contains("額外金幣")
			|| name.contains("木匠") || name.contains("购物狂潮") || name.contains("購物狂潮");
	}

	private static void render(WorldRenderContext context) {
		if (!enabled || TARGETS.isEmpty()) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) return;
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		double distance = Math.min(MAX_RANGE, renderDistance);
		for (ArmorStand stand : TARGETS) {
			if (stand.level() != minecraft.level || !isPowerup(stand)
				|| stand.position().distanceToSqr(camera) > distance * distance) continue;
			AABB bounds = stand.getBoundingBox();
			double centerX = (bounds.minX + bounds.maxX) * 0.5;
			double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
			double height = Math.max(1.0, bounds.maxY - bounds.minY);
			AABB box = new AABB(centerX - 0.3, bounds.minY, centerZ - 0.3,
				centerX + 0.3, bounds.minY + height, centerZ + 0.3);
			EspBoxRenderer.draw(context, box, POWERUP_COLOR, 2.0F);
		}
	}

	/** HUD projection keeps the tags visible through walls, like Youkai's Nametags module. */
	private static void renderLabels(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (!enabled || TARGETS.isEmpty()) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().position();
		Matrix4f view = new Matrix4f().rotation(minecraft.gameRenderer.getMainCamera().rotation()).invert();
		// In 1.21.11 this method takes FOV in degrees, not the frame's partial tick.
		Matrix4f projection = minecraft.gameRenderer.getProjectionMatrix(minecraft.options.fov().get());
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		for (ArmorStand stand : TARGETS) {
			if (stand.level() != minecraft.level || !isPowerup(stand)) continue;
			Vec3 position = stand.getPosition(partialTick);
			if (position.distanceToSqr(cameraPosition) > (double) renderDistance * renderDistance) continue;
			Vector4f point = new Vector4f((float) (position.x - cameraPosition.x),
				(float) (position.y + stand.getBbHeight() + 0.3 - cameraPosition.y),
				(float) (position.z - cameraPosition.z), 1.0F);
			view.transform(point);
			projection.transform(point);
			if (point.w <= 0.0F) continue;
			float ndcX = point.x / point.w;
			float ndcY = point.y / point.w;
			float ndcZ = point.z / point.w;
			if (ndcZ < -1.0F || ndcZ > 1.0F) continue;
			int x = Math.round((ndcX + 1.0F) * 0.5F * screenWidth);
			int y = Math.round((1.0F - ndcY) * 0.5F * screenHeight);
			if (x < 0 || x >= screenWidth || y < 0 || y >= screenHeight) continue;
			drawTag(graphics, minecraft, x, y, powerupLabel(stand),
				formatLifetime(PowerupTracker.droppedSeconds(stand.getId())));
		}
	}

	private static void drawTag(GuiGraphics graphics, Minecraft minecraft, int centerX, int topY, String name, String timer) {
		int nameWidth = minecraft.font.width(name);
		int timerWidth = minecraft.font.width(timer);
		int width = Math.max(nameWidth, timerWidth) + 8;
		int left = centerX - width / 2;
		int top = topY - minecraft.font.lineHeight - 4;
		graphics.fill(left, top, left + width, top + minecraft.font.lineHeight * 2 + 6, 0x990A0D12);
		graphics.drawString(minecraft.font, name, centerX - nameWidth / 2, top + 1, 0xFFFFFFFF, true);
		graphics.drawString(minecraft.font, timer, centerX - timerWidth / 2,
			top + minecraft.font.lineHeight + 2, 0xFFB8EFFF, true);
	}

	private static String formatLifetime(int seconds) {
		int minutes = seconds / 60;
		int remainder = seconds % 60;
		return String.format(Locale.ROOT, "%02d:%02d", minutes, remainder);
	}

	private static String powerupLabel(ArmorStand stand) {
		String name = stand.getCustomName().getString().replaceAll("§.", "").toLowerCase(Locale.ROOT);
		if (name.contains("insta kill") || name.contains("秒杀") || name.contains("一擊必殺")
			|| name.contains("瞬间击杀") || name.contains("瞬間擊殺")) return "Insta Kill";
		if (name.contains("max ammo") || name.contains("满弹药") || name.contains("滿彈藥")
			|| name.contains("弹药满载") || name.contains("彈藥滿載")) return "Max Ammo";
		if (name.contains("double gold") || name.contains("双倍金币") || name.contains("雙倍金幣")
			|| name.contains("双倍金钱") || name.contains("雙倍金錢")) return "Double Gold";
		if (name.contains("bonus gold") || name.contains("额外金币") || name.contains("額外金幣")) return "Bonus Gold";
		if (name.contains("carpenter") || name.contains("木匠")) return "Carpenter";
		return "Shopping Spree";
	}

}

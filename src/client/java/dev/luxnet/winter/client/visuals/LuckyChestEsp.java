package dev.luxnet.winter.client.visuals;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class LuckyChestEsp {
	private static boolean enabled;
	private static int renderDistance = 64;

	private LuckyChestEsp() { }

	public static void registerWorldRender() {
		WorldRenderEvents.END_MAIN.register(LuckyChestEsp::render);
	}

	public static boolean isEnabled() { return enabled; }
	public static int getRenderDistance() { return renderDistance; }
	public static void setRenderDistance(int value) { renderDistance = Math.max(16, Math.min(128, value)); }
	public static void toggle() { enabled = !enabled; }

	private static void render(WorldRenderContext context) {
		if (!enabled) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) return;

		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		Set<BlockPos> renderedPositions = new HashSet<>();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
		if (!hasLuckyChestName(entity) || entity.position().distanceToSqr(camera) > renderDistance * renderDistance) continue;
			BlockPos markerPos = entity.blockPosition();
			if (!renderedPositions.add(markerPos)) continue;

			// The hologram entity sits above the chest; anchor the 3x3 footprint one block lower.
			double minX = markerPos.getX() - 1.0;
			double maxX = markerPos.getX() + 2.0;
			double minZ = markerPos.getZ() - 1.0;
			double maxZ = markerPos.getZ() + 2.0;
			double minY = markerPos.getY() - 1.0 + 0.003;
			double maxY = markerPos.getY() + 1.0 + 0.003;
			EspBoxRenderer.draw(context, new AABB(minX, minY, minZ, maxX, maxY, maxZ), 0xB875DFFF, 2.0F);
		}
	}

	private static boolean hasLuckyChestName(Entity entity) {
		String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
		if (entity instanceof Display.TextDisplay textDisplay) {
			name += " " + textDisplay.getText().getString();
		}
		return name.toLowerCase(Locale.ROOT).contains("lucky chest");
	}
}

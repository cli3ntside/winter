package dev.luxnet.winter.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Small configurable Winter watermark rendered over the in-game HUD. */
public final class WinterHud {
	private static boolean watermarkEnabled = true;

	private WinterHud() { }

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("winter", "watermark"), WinterHud::render);
	}

	public static boolean isWatermarkEnabled() { return watermarkEnabled; }
	public static void setWatermarkEnabled(boolean enabled) { watermarkEnabled = enabled; }
	public static void toggleWatermark() { watermarkEnabled = !watermarkEnabled; }

	private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!watermarkEnabled || minecraft.player == null || minecraft.options.hideGui) return;

		String title = "Winter";
		int x = 8;
		int y = 8;
		long time = System.currentTimeMillis();
		for (int i = 0; i < title.length(); i++) {
			float wave = (float) (Math.sin(time / 520.0 + i * 0.65) + 1.0) / 2.0F;
			int color = blend(0xFF75DFFF, 0xFFFFFFFF, wave);
			String character = title.substring(i, i + 1);
			graphics.drawString(minecraft.font, character, x, y, color, true);
			x += minecraft.font.width(character);
		}
	}

	private static int blend(int colorA, int colorB, float amount) {
		int red = (int) (((colorA >> 16 & 0xFF) * (1.0F - amount)) + ((colorB >> 16 & 0xFF) * amount));
		int green = (int) (((colorA >> 8 & 0xFF) * (1.0F - amount)) + ((colorB >> 8 & 0xFF) * amount));
		int blue = (int) (((colorA & 0xFF) * (1.0F - amount)) + ((colorB & 0xFF) * amount));
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}
}

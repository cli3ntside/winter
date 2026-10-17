package dev.luxnet.winter.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import dev.luxnet.winter.client.visuals.TeamEsp;

import java.util.List;

/** Watermark and compact draggable power-up HUD elements. */
public final class WinterHud {
	public static final int WATERMARK = 0;
	public static final int POWERUP_TIMER = 1;
	public static final int POWERUP_PREDICTOR = 2;
	private static final float HUD_SCALE = 0.82F;
	private static final double MIN_ELEMENT_SCALE = 0.5;
	private static final double MAX_ELEMENT_SCALE = 2.0;
	private static final int TIMER_ROW_HEIGHT = 19;
	private static final double[] X = { 0.0, 0.0, 0.0 };
	private static final double[] Y = { 0.0, 0.075, 0.155 };
	private static final double[] ELEMENT_SCALE = { 1.0, 1.0, 1.0 };
	private static boolean watermarkEnabled = true;

	private WinterHud() { }

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("winter", "watermark"), WinterHud::render);
	}

	public static boolean isWatermarkEnabled() { return watermarkEnabled; }
	public static void setWatermarkEnabled(boolean enabled) {
		if (watermarkEnabled != enabled) { watermarkEnabled = enabled; WinterConfig.save(); }
	}
	public static void toggleWatermark() { setWatermarkEnabled(!watermarkEnabled); }
	public static double getX(int element) { return X[element]; }
	public static double getY(int element) { return Y[element]; }
	public static double getElementScale(int element) { return ELEMENT_SCALE[element]; }
	public static void setElementScale(int element, double scale) {
		ELEMENT_SCALE[element] = Math.max(MIN_ELEMENT_SCALE, Math.min(MAX_ELEMENT_SCALE, scale));
	}
	public static void loadElementScale(int element, double scale) { setElementScale(element, scale); }
	public static void setPosition(int element, double x, double y) {
		X[element] = clamp(x); Y[element] = clamp(y);
	}
	public static void savePositions() { WinterConfig.save(); }
	public static void loadPosition(int element, double x, double y) { X[element] = clamp(x); Y[element] = clamp(y); }

	public static int getWidth(int element, Font font) {
		int rawWidth;
		if (element == WATERMARK) rawWidth = font.width("Winter");
		else if (element == POWERUP_TIMER) rawWidth = font.width("Shopping Spree") + font.width("  30s") + 14;
		else rawWidth = lines(element, true).stream().mapToInt(font::width).max().orElse(0) + 10;
		return (int) Math.ceil(rawWidth * effectiveScale(element));
	}
	public static int getHeight(int element, Font font) {
		int rawHeight;
		if (element == WATERMARK) rawHeight = font.lineHeight;
		else if (element == POWERUP_TIMER) rawHeight = TIMER_ROW_HEIGHT * 3 + 8;
		else rawHeight = lines(element, true).size() * (font.lineHeight + 1) + 6;
		return (int) Math.ceil(rawHeight * effectiveScale(element));
	}
	public static int getScreenX(int element, int screenWidth, Font font) {
		return (int) Math.round(X[element] * Math.max(0, screenWidth - getWidth(element, font)));
	}
	public static int getScreenY(int element, int screenHeight, Font font) {
		return (int) Math.round(Y[element] * Math.max(0, screenHeight - getHeight(element, font)));
	}

	/** Draws a fixed sample of a HUD element on the editor canvas. */
	public static void renderEditorPreview(GuiGraphics graphics, int element, int x, int y) {
		drawElement(graphics, element, x, y, true);
	}

	private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;
		if (watermarkEnabled) drawElement(graphics, WATERMARK,
			getScreenX(WATERMARK, graphics.guiWidth(), minecraft.font),
			getScreenY(WATERMARK, graphics.guiHeight(), minecraft.font), false);
		if (!PowerupTracker.isEnabled() || minecraft.level == null || !TeamEsp.isZombiesGame(minecraft)) return;
		if (PowerupTracker.isTimerEnabled() && !PowerupTracker.activeTimerRows().isEmpty()) drawElement(graphics, POWERUP_TIMER,
			getScreenX(POWERUP_TIMER, graphics.guiWidth(), minecraft.font),
			getScreenY(POWERUP_TIMER, graphics.guiHeight(), minecraft.font), false);
		if (PowerupTracker.isPredictorEnabled()) drawElement(graphics, POWERUP_PREDICTOR,
			getScreenX(POWERUP_PREDICTOR, graphics.guiWidth(), minecraft.font),
			getScreenY(POWERUP_PREDICTOR, graphics.guiHeight(), minecraft.font), false);
	}

	private static void drawElement(GuiGraphics graphics, int element, int x, int y, boolean preview) {
		Minecraft minecraft = Minecraft.getInstance();
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		float scale = (float) effectiveScale(element);
		graphics.pose().scale(scale, scale);
		if (element == WATERMARK) {
			drawGradientTitle(graphics, 0, 0, minecraft.font);
			graphics.pose().popMatrix();
			return;
		}
		if (element == POWERUP_TIMER) {
			drawTimer(graphics, minecraft.font, preview);
		} else {
			List<String> content = lines(element, preview);
			int width = rawWidth(element, minecraft.font);
			int height = content.size() * (minecraft.font.lineHeight + 1) + 6;
			graphics.fill(0, 0, width, height, 0x2B0A0D12);
			for (int i = 0; i < content.size(); i++) {
				int color = i == 0 ? 0xFF9FEAFF : 0xFFE5EDF5;
				graphics.drawString(minecraft.font, content.get(i), 5, 3 + i * (minecraft.font.lineHeight + 1), color, true);
			}
		}
		graphics.pose().popMatrix();
	}

	private static void drawTimer(GuiGraphics graphics, Font font, boolean preview) {
		List<PowerupTracker.TimerRow> rows = preview ? List.of(
			new PowerupTracker.TimerRow("Insta Kill", 24, 0.65),
			new PowerupTracker.TimerRow("Double Gold", 11, 0.30),
			new PowerupTracker.TimerRow("Shopping Spree", 3, 0.08)) : PowerupTracker.activeTimerRows();
		int width = rawWidth(POWERUP_TIMER, font);
		int height = TIMER_ROW_HEIGHT * 3 + 8;
		graphics.fill(0, 0, width, height, 0x2B0A0D12);
		for (int i = 0; i < rows.size(); i++) {
			PowerupTracker.TimerRow row = rows.get(i);
			int y = 4 + i * TIMER_ROW_HEIGHT;
			String seconds = row.seconds() + "s";
			graphics.drawString(font, row.label(), 5, y, 0xFFEAF7FC, false);
			graphics.drawString(font, seconds, width - 5 - font.width(seconds), y, 0xFF9FEAFF, false);
			int barY = y + font.lineHeight + 1;
			int barWidth = width - 10;
			graphics.fill(5, barY, 5 + barWidth, barY + 2, 0x55404A56);
			graphics.fill(5, barY, 5 + (int) Math.round(barWidth * row.progress()), barY + 2, 0xDD70D8F5);
		}
	}

	private static int rawWidth(int element, Font font) {
		if (element == POWERUP_TIMER) return font.width("Shopping Spree") + font.width("  30s") + 14;
		return lines(element, true).stream().mapToInt(font::width).max().orElse(0) + 10;
	}

	private static double effectiveScale(int element) {
		return ELEMENT_SCALE[element] * (element == WATERMARK ? 1.0 : HUD_SCALE);
	}

	private static List<String> lines(int element, boolean preview) {
		if (element == POWERUP_TIMER) return List.of();
		return preview
			? List.of("Powerup Predictor", "Insta Kill  R08", "Max Ammo  R100", "Shopping Spree  R15") : PowerupTracker.predictorLines();
	}

	private static void drawGradientTitle(GuiGraphics graphics, int x, int y, Font font) {
		long time = System.currentTimeMillis();
		for (int i = 0; i < "Winter".length(); i++) {
			float wave = (float) (Math.sin(time / 520.0 + i * 0.65) + 1.0) / 2.0F;
			int color = blend(0xFF75DFFF, 0xFFFFFFFF, wave);
			String character = "Winter".substring(i, i + 1);
			graphics.drawString(font, character, x, y, color, true);
			x += font.width(character);
		}
	}

	private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
	private static int blend(int a, int b, float t) {
		int r = (int) (((a >> 16 & 255) * (1 - t)) + ((b >> 16 & 255) * t));
		int g = (int) (((a >> 8 & 255) * (1 - t)) + ((b >> 8 & 255) * t));
		int bl = (int) (((a & 255) * (1 - t)) + ((b & 255) * t));
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}
}

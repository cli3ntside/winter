package dev.luxnet.winter.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import dev.luxnet.winter.client.visuals.TeamEsp;
import dev.luxnet.winter.client.mixin.BossHealthOverlayAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

/** Watermark and compact draggable power-up HUD elements. */
public final class WinterHud {
	public static final int WATERMARK = 0;
	public static final int POWERUP_TIMER = 1;
	public static final int POWERUP_PREDICTOR = 2;
	public static final int SCOREBOARD = 3;
	public static final int BOSSBAR = 4;
	public static final int BOSS_WARNING = 5;
	private static final float HUD_SCALE = 0.82F;
	private static final double MIN_ELEMENT_SCALE = 0.5;
	private static final double MAX_ELEMENT_SCALE = 2.0;
	private static final int TIMER_ROW_HEIGHT = 19;
	private static final double[] X = { 0.0, 0.0, 0.0, 1.0, 0.5, 0.5 };
	private static final double[] Y = { 0.0, 0.075, 0.155, 0.5, 0.02, 0.12 };
	private static final double[] ELEMENT_SCALE = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0 };
	private static final boolean[] POSITION_SET = { true, true, true, false, false, true };
	private static boolean watermarkEnabled = true;
	private static boolean scoreboardEnabled = true;
	private static boolean bossbarEnabled = true;

	private WinterHud() { }

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("winter", "watermark"), WinterHud::render);
	}

	public static boolean isWatermarkEnabled() { return watermarkEnabled; }
	public static void setWatermarkEnabled(boolean enabled) {
		if (watermarkEnabled != enabled) { watermarkEnabled = enabled; WinterConfig.save(); }
	}
	public static void toggleWatermark() { setWatermarkEnabled(!watermarkEnabled); }
	public static boolean isScoreboardEnabled() { return scoreboardEnabled; }
	public static boolean isBossbarEnabled() { return bossbarEnabled; }
	public static void setScoreboardEnabled(boolean value) { if (scoreboardEnabled != value) { scoreboardEnabled = value; WinterConfig.save(); } }
	public static void setBossbarEnabled(boolean value) { if (bossbarEnabled != value) { bossbarEnabled = value; WinterConfig.save(); } }
	public static void toggleScoreboard() { setScoreboardEnabled(!scoreboardEnabled); }
	public static void toggleBossbar() { setBossbarEnabled(!bossbarEnabled); }
	public static double getX(int element) { return X[element]; }
	public static double getY(int element) { return Y[element]; }
	public static boolean hasCustomPosition(int element) { return POSITION_SET[element]; }
	public static double getElementScale(int element) { return ELEMENT_SCALE[element]; }
	public static void setElementScale(int element, double scale) {
		ELEMENT_SCALE[element] = Math.max(MIN_ELEMENT_SCALE, Math.min(MAX_ELEMENT_SCALE, scale));
	}
	public static void loadElementScale(int element, double scale) { setElementScale(element, scale); }
	public static void setPosition(int element, double x, double y) {
		X[element] = clamp(x); Y[element] = clamp(y); POSITION_SET[element] = true;
	}
	public static void savePositions() { WinterConfig.save(); }
	public static void loadPosition(int element, double x, double y) { loadPosition(element, x, y, true); }
	public static void loadPosition(int element, double x, double y, boolean positioned) {
		X[element] = clamp(x); Y[element] = clamp(y); POSITION_SET[element] = positioned;
	}

	public static int getWidth(int element, Font font) {
		int rawWidth;
		if (element == WATERMARK) rawWidth = font.width("Winter");
		else if (element == POWERUP_TIMER) rawWidth = font.width("Shopping Spree") + font.width("  30s") + 14;
		else if (element == SCOREBOARD) rawWidth = scoreboardLayout(0, 0, font, true).width;
		else if (element == BOSSBAR) rawWidth = 182;
		else if (element == BOSS_WARNING) {
			String liveLabel = BossWarning.getLiveLabel();
			rawWidth = font.width(liveLabel == null ? BossWarning.getPreviewLabel() : liveLabel) + 12;
		}
		else rawWidth = lines(element, true).stream().mapToInt(font::width).max().orElse(0) + 10;
		return (int) Math.ceil(rawWidth * effectiveScale(element));
	}
	public static int getHeight(int element, Font font) {
		int rawHeight;
		if (element == WATERMARK) rawHeight = font.lineHeight;
		else if (element == POWERUP_TIMER) rawHeight = TIMER_ROW_HEIGHT * 3 + 8;
		else if (element == SCOREBOARD) rawHeight = scoreboardLayout(0, 0, font, true).height;
		else if (element == BOSSBAR) rawHeight = bossbarHeight(true);
		else if (element == BOSS_WARNING) rawHeight = Minecraft.getInstance().font.lineHeight + 10;
		else rawHeight = lines(element, true).size() * (font.lineHeight + 1) + 6;
		return (int) Math.ceil(rawHeight * effectiveScale(element));
	}
	public static int getScreenX(int element, int screenWidth, Font font) {
		if (isNativeElement(element) && !POSITION_SET[element]) return nativeBounds(element, screenWidth, 0, font).x;
		return (int) Math.round(X[element] * Math.max(0, screenWidth - getWidth(element, font)));
	}
	public static int getScreenY(int element, int screenHeight, Font font) {
		if (isNativeElement(element) && !POSITION_SET[element]) return nativeBounds(element, 0, screenHeight, font).y;
		return (int) Math.round(Y[element] * Math.max(0, screenHeight - getHeight(element, font)));
	}
	public static int getEditorScreenY(int element, int screenHeight, Font font) {
		if (element == BOSSBAR && !POSITION_SET[element]) return 62;
		return getScreenY(element, screenHeight, font);
	}

	public static void beginNativeElement(GuiGraphics graphics, int element) {
		if (!isNativeElement(element)) return;
		var bounds = nativeBounds(element, graphics.guiWidth(), graphics.guiHeight(), Minecraft.getInstance().font);
		float scale = (float) ELEMENT_SCALE[element];
		int x = POSITION_SET[element] ? getScreenX(element, graphics.guiWidth(), Minecraft.getInstance().font) : bounds.x;
		int y = POSITION_SET[element] ? getScreenY(element, graphics.guiHeight(), Minecraft.getInstance().font) : bounds.y;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x - bounds.x * scale, y - bounds.y * scale);
		graphics.pose().scale(scale, scale);
	}

	public static void endNativeElement(GuiGraphics graphics, int element) {
		if (isNativeElement(element)) graphics.pose().popMatrix();
	}

	private static boolean isNativeElement(int element) { return element == SCOREBOARD || element == BOSSBAR; }

	/** Draws a fixed sample of a HUD element on the editor canvas. */
	public static void renderEditorPreview(GuiGraphics graphics, int element, int x, int y) {
		if (element == SCOREBOARD) { renderScoreboardPreview(graphics, x, y); return; }
		if (element == BOSSBAR) { renderBossbarPreview(graphics, x, y); return; }
		drawElement(graphics, element, x, y, true);
	}

	private static void renderScoreboardPreview(GuiGraphics graphics, int x, int y) {
		Minecraft minecraft = Minecraft.getInstance();
		int width = scoreboardLayout(0, 0, minecraft.font, true).width;
		int height = scoreboardLayout(0, 0, minecraft.font, true).height;
		graphics.pose().pushMatrix(); graphics.pose().translate(x, y); graphics.pose().scale((float) ELEMENT_SCALE[SCOREBOARD], (float) ELEMENT_SCALE[SCOREBOARD]);
		graphics.fill(0, 0, width, height, 0x99505050);
		graphics.fill(0, 0, width, 9, 0x99606060);
		graphics.drawString(minecraft.font, "Scoreboard", 3, 1, 0xFFFFFFFF, false);
		String[] sample = { "Round 12", "Kills: 24", "Gold: 1,250", "Doors: 3/8", "Players: 4" };
		for (int i = 0; i < sample.length; i++) graphics.drawString(minecraft.font, sample[i], 3, 10 + i * 9, 0xFFE0E0E0, false);
		graphics.pose().popMatrix();
	}

	private static void renderBossbarPreview(GuiGraphics graphics, int x, int y) {
		Minecraft minecraft = Minecraft.getInstance();
		int width = 182;
		int count = Math.max(1, currentBossbarCount());
		int height = count * 19;
		graphics.pose().pushMatrix(); graphics.pose().translate(x, y); graphics.pose().scale((float) ELEMENT_SCALE[BOSSBAR], (float) ELEMENT_SCALE[BOSSBAR]);
		for (int i = 0; i < count; i++) {
			int barY = i * 19;
			graphics.fill(0, barY, width, barY + 19, 0x99505050);
			String label = i == 0 ? "Bossbar" : "Bossbar " + (i + 1);
			graphics.drawString(minecraft.font, label, (width - minecraft.font.width(label)) / 2, barY + 1, 0xFFFFFFFF, false);
			graphics.fill(0, barY + 10, width, barY + 16, 0xFF303030);
			graphics.fill(0, barY + 10, width * (3 + i % 2) / 4, barY + 16, 0xFF8A1F1F);
		}
		graphics.pose().popMatrix();
	}

	private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;
		if (watermarkEnabled) drawElement(graphics, WATERMARK,
			getScreenX(WATERMARK, graphics.guiWidth(), minecraft.font),
			getScreenY(WATERMARK, graphics.guiHeight(), minecraft.font), false);
		String bossWarning = BossWarning.getLiveLabel();
		if (bossWarning != null) drawElement(graphics, BOSS_WARNING,
			getScreenX(BOSS_WARNING, graphics.guiWidth(), minecraft.font),
			getScreenY(BOSS_WARNING, graphics.guiHeight(), minecraft.font), false);
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
		} else if (element == BOSS_WARNING) {
			String label = preview ? BossWarning.getPreviewLabel() : BossWarning.getLiveLabel();
			if (label != null) {
				int width = minecraft.font.width(label) + 12;
				graphics.fill(0, 0, width, minecraft.font.lineHeight + 10, 0x990A0D12);
				graphics.fill(0, 0, 2, minecraft.font.lineHeight + 10, 0xFFFFB74D);
				graphics.drawString(minecraft.font, label, 7, 5, 0xFFFFD27A, true);
			}
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
		return ELEMENT_SCALE[element] * (element <= POWERUP_PREDICTOR && element != WATERMARK ? HUD_SCALE : 1.0);
	}

	private static Bounds nativeBounds(int element, int screenWidth, int screenHeight, Font font) {
		if (element == BOSSBAR) return new Bounds((screenWidth - 182) / 2, 3, 182, bossbarHeight(false));
		ScoreboardLayout layout = scoreboardLayout(screenWidth, screenHeight, font, false);
		return new Bounds(layout.x, layout.y, layout.width, layout.height);
	}

	private static int bossbarHeight(boolean preview) {
		int count = preview ? Math.max(1, currentBossbarCount()) : currentBossbarCount();
		return Math.max(19, count * 19);
	}

	private static int currentBossbarCount() {
		try {
			return ((BossHealthOverlayAccessor) Minecraft.getInstance().gui.getBossOverlay()).winter$getEvents().size();
		} catch (RuntimeException ignored) { return 0; }
	}

	private static ScoreboardLayout scoreboardLayout(int screenWidth, int screenHeight, Font font, boolean preview) {
		Objective objective = currentSidebarObjective();
		List<PlayerScoreEntry> entries = objective == null ? List.of() : objective.getScoreboard().listPlayerScores(objective)
			.stream().filter(entry -> !entry.isHidden())
			.sorted(Comparator.comparingInt(PlayerScoreEntry::value).thenComparing(PlayerScoreEntry::owner))
			.skip(Math.max(0, objective == null ? 0 : objective.getScoreboard().listPlayerScores(objective).stream().filter(e -> !e.isHidden()).count() - 15))
			.toList();
		int rowCount = preview && entries.isEmpty() ? 5 : entries.size();
		int contentWidth = objective == null ? font.width("Scoreboard") : font.width(objective.getDisplayName());
		if (objective != null) {
			var numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
			for (PlayerScoreEntry entry : entries) {
				Component display = entry.display() == null ? entry.ownerName() : entry.display();
				int rowWidth = font.width(display);
				if (numberFormat != null) rowWidth += font.width(entry.formatValue(numberFormat)) + font.width(":");
				contentWidth = Math.max(contentWidth, rowWidth);
			}
		}
		if (preview && entries.isEmpty()) contentWidth = Math.max(contentWidth, font.width("Players: 4"));
		int width = contentWidth + 7;
		int height = rowCount * 9 + 10;
		int x = screenWidth == 0 ? 0 : screenWidth - width;
		int y = screenHeight == 0 ? 0 : screenHeight / 2 - rowCount * 6 - 10;
		return new ScoreboardLayout(x, y, width, height);
	}

	private static Objective currentSidebarObjective() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) return null;
		Scoreboard scoreboard = minecraft.level.getScoreboard();
		Team team = scoreboard.getPlayersTeam(minecraft.player.getScoreboardName());
		if (team != null && team.getColor() != null) {
			DisplaySlot teamSlot = DisplaySlot.teamColorToSlot(team.getColor());
			Objective teamObjective = teamSlot == null ? null : scoreboard.getDisplayObjective(teamSlot);
			if (teamObjective != null) return teamObjective;
		}
		return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	public record Bounds(int x, int y, int width, int height) { }
	private record ScoreboardLayout(int x, int y, int width, int height) { }

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

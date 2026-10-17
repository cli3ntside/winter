package dev.luxnet.winter.client;

import dev.luxnet.winter.client.debug.MobDebugModule;
import dev.luxnet.winter.client.debug.DrawPredict;
import dev.luxnet.winter.client.debug.AreaSelector;
import dev.luxnet.winter.client.assistance.AutoRevive;
import dev.luxnet.winter.client.assistance.AimAssist;
import dev.luxnet.winter.client.assistance.Triggerbot;
import dev.luxnet.winter.client.assistance.Freecam;
import dev.luxnet.winter.client.assistance.SkillBind;
import dev.luxnet.winter.client.assistance.RightClicker;
import dev.luxnet.winter.client.debug.ShotDebugModule;
import dev.luxnet.winter.client.visuals.BulletTracers;
import dev.luxnet.winter.client.visuals.LuckyChestEsp;
import dev.luxnet.winter.client.visuals.MobEsp;
import dev.luxnet.winter.client.visuals.TeamEsp;
import dev.luxnet.winter.client.visuals.PowerupEsp;
import dev.luxnet.winter.client.visuals.BrokenWindowEsp;
import dev.luxnet.winter.client.visuals.PlayerVisibility;
import dev.luxnet.winter.client.visuals.ChatFilter;
import dev.luxnet.winter.client.visuals.WallbangTransparency;
import dev.luxnet.winter.client.WinterHud;
import dev.luxnet.winter.client.PowerupTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public class WinterScreen extends Screen {
	private static final int PANEL_WIDTH = 320;
	private static final int PANEL_HEIGHT = 320;
	private static final int SIDEBAR_WIDTH = 92;
	private static final int HEADER_HEIGHT = 58;
	private static final int CONTENT_PADDING = 14;
	private static final int EVENT_ROW_HEIGHT = 13;
	private static final int DEBUG_AREA_EXPANSION = 10;
	private static Category savedCategory = Category.ASSISTANCE;
	private static int savedExpandedModule = -1;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int scrollOffset;
	private int activeSlider = -1;
	private int expandedModule;
	private boolean waitingForFreecamBind;
	private boolean waitingForSkillBind;
	private boolean waitingForAimBind;
	private boolean waitingForTriggerbotBind;
	private Category selectedCategory;

	private enum Category { ASSISTANCE, VISUALS, HUD, DEBUG }

	public WinterScreen() {
		super(Component.literal("Winter"));
		selectedCategory = savedCategory;
		expandedModule = savedExpandedModule;
	}

	public static void loadMenuState(String categoryName, int module) {
		try { savedCategory = Category.valueOf(categoryName); }
		catch (RuntimeException ignored) { savedCategory = Category.ASSISTANCE; }
		savedExpandedModule = module == DEBUG_AREA_EXPANSION || module == 30 || module >= 19 && module <= 24 || module >= 0 && module <= 8
			? module : -1;
	}

	public static String getSelectedCategoryName() { return savedCategory.name(); }
	public static int getExpandedModule() { return savedExpandedModule; }

	private void setSelectedCategory(Category category) {
		selectedCategory = category;
		savedCategory = category;
		WinterConfig.save();
	}

	private void setExpandedModule(int module) {
		expandedModule = module;
		savedExpandedModule = module;
		scrollOffset = 0;
		WinterConfig.save();
	}

	@Override
	protected void init() {
		panelWidth = Math.min(PANEL_WIDTH, width - 24);
		panelHeight = Math.min(PANEL_HEIGHT, height - 24);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		scrollOffset = 0;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0x66000000);
		graphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0x667E8794);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE612151B);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + HEADER_HEIGHT, 0xF0181D25);

		drawGradientTitle(graphics);
		graphics.fill(panelX + 12, panelY + HEADER_HEIGHT, panelX + panelWidth - 12, panelY + HEADER_HEIGHT + 1, 0xFF626A76);

		int sidebarX = panelX + 10;
		int sidebarY = panelY + HEADER_HEIGHT + 12;
		int sidebarRight = panelX + SIDEBAR_WIDTH;
		drawCategoryTab(graphics, mouseX, mouseY, sidebarX, sidebarY, sidebarRight, "Assistance", Category.ASSISTANCE);
		drawCategoryTab(graphics, mouseX, mouseY, sidebarX, sidebarY + 30, sidebarRight, "Visuals", Category.VISUALS);
		drawCategoryTab(graphics, mouseX, mouseY, sidebarX, sidebarY + 60, sidebarRight, "HUD", Category.HUD);
		drawCategoryTab(graphics, mouseX, mouseY, sidebarX, sidebarY + 90, sidebarRight, "Debug", Category.DEBUG);

		int dividerX = panelX + SIDEBAR_WIDTH + 4;
		graphics.fill(dividerX, panelY + HEADER_HEIGHT + 10, dividerX + 1, panelY + panelHeight - 12, 0x665E6672);
		int contentX = dividerX + CONTENT_PADDING;
		int contentRight = panelX + panelWidth - 12;
		int contentTop = panelY + HEADER_HEIGHT + 14;
		int contentBottom = panelY + panelHeight - 12;
		graphics.enableScissor(contentX, contentTop, contentRight, contentBottom);
		if (selectedCategory == Category.DEBUG) {
			renderDebug(graphics, mouseX, mouseY, contentX, contentTop, contentBottom, contentRight);
		} else if (selectedCategory == Category.VISUALS) {
			renderVisuals(graphics, contentX, contentTop, contentRight);
		} else if (selectedCategory == Category.HUD) {
			renderHud(graphics, contentX, contentTop, contentRight);
		} else {
			renderAssistance(graphics, contentX, contentTop, contentRight);
		}
		graphics.disableScissor();
		super.render(graphics, mouseX, mouseY, delta);
	}

	private void drawCategoryTab(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int right,
								 String label, Category category) {
		boolean selected = selectedCategory == category;
		boolean hovered = mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + 24;
		graphics.fill(x, y, right, y + 24, selected ? 0xCC303946 : hovered ? 0xAA303640 : 0xAA242A33);
		if (selected) graphics.fill(x, y, x + 2, y + 24, 0xFF70D8F5);
		graphics.drawString(font, label, x + 10, y + 8, 0xFFEAF7FC, false);
	}

	private void renderDebug(GuiGraphics graphics, int mouseX, int mouseY, int contentX, int contentTop,
							 int contentBottom, int contentRight) {
		boolean captureEnabled = ShotDebugModule.isEnabled();
		drawModuleRow(graphics, contentX, contentRight, debugRowY(0, contentTop), "Capture: " + (captureEnabled ? "ON" : "OFF"), captureEnabled);

		boolean mobsEnabled = MobDebugModule.isEnabled();
		int mobsY = debugRowY(1, contentTop);
		String mobsLabel = "Detect Mobs: " + (mobsEnabled ? "ON" : "OFF");
		if (mobsEnabled) mobsLabel += " (" + MobDebugModule.getSnapshots().size() + ")";
		drawModuleRow(graphics, contentX, contentRight, mobsY, mobsLabel, mobsEnabled);
		int areaY = debugRowY(2, contentTop);
		boolean areaEnabled = AreaSelector.isEnabled();
		drawModuleRow(graphics, contentX, contentRight, areaY,
			"Area Selector: " + (areaEnabled ? "ON" : "OFF"), areaEnabled);
		boolean drawPredictEnabled = DrawPredict.isEnabled();
		drawModuleRow(graphics, contentX, contentRight, debugRowY(3, contentTop),
			"Draw Predict: " + (drawPredictEnabled ? "ON" : "OFF"), drawPredictEnabled);

		int eventsTop = debugEventsTop();
		if (expandedModule == DEBUG_AREA_EXPANSION) {
			int settingsY = areaY + 27;
			BlockPos first = AreaSelector.getFirstCorner();
			BlockPos second = AreaSelector.getSecondCorner();
			String firstText = first == null ? "Point 1: right click a block" : "Point 1: " + coordinates(first);
			String secondText = second == null ? (first == null ? "Point 2: —" : "Point 2: right click a block")
				: "Point 2: " + coordinates(second);
			graphics.drawString(font, trimToWidth(firstText, contentRight - contentX), contentX + 2,
				settingsY, 0xFFD5DCE5, false);
			graphics.drawString(font, trimToWidth(secondText, contentRight - contentX), contentX + 2,
				settingsY + 13, 0xFFD5DCE5, false);
			int saveY = settingsY + 28;
			boolean canSave = AreaSelector.canSave();
			boolean hovered = mouseX >= contentX && mouseX <= contentRight && mouseY >= saveY && mouseY < saveY + 20;
			graphics.fill(contentX, saveY, contentRight, saveY + 20,
				canSave ? (hovered ? 0xCC337D8E : 0xAA285E6A) : 0xAA303640);
			String saveLabel = "Save area  ·  " + AreaSelector.getSavedAreaCount() + " saved";
			graphics.drawString(font, saveLabel, contentX + 8, saveY + 6,
				canSave ? 0xFFEAF7FC : 0xFF9AA3AF, false);
			if (!AreaSelector.getSaveStatus().isEmpty()) {
				graphics.drawString(font, trimToWidth(AreaSelector.getSaveStatus(), contentRight - contentX),
					contentX + 2, settingsY + 53, 0xFF9AA3AF, false);
			}
		}

		List<String> events = ShotDebugModule.getEvents();
		if (events.isEmpty()) {
			graphics.drawString(font, "Enable capture, then test", contentX, eventsTop - scrollOffset, 0xFF9AA3AF, false);
			return;
		}
		for (int i = 0; i < events.size(); i++) {
			int eventY = eventsTop + i * EVENT_ROW_HEIGHT - scrollOffset;
			if (eventY + EVENT_ROW_HEIGHT >= contentTop && eventY <= contentBottom) {
				String fullEvent = events.get(events.size() - 1 - i);
				graphics.drawString(font, trimToWidth(fullEvent, contentRight - contentX - 6), contentX + 3, eventY,
					0xFFD5DCE5, false);
				if (mouseX >= contentX && mouseX <= contentRight && mouseY >= eventY && mouseY < eventY + EVENT_ROW_HEIGHT) {
					graphics.setTooltipForNextFrame(Component.literal(fullEvent), mouseX, mouseY);
				}
			}
		}
	}

	private int debugEventsTop() {
		return contentTop() + 106 + (expandedModule == DEBUG_AREA_EXPANSION ? debugExpansionHeight() : 0);
	}

	private int debugRowY(int index, int top) {
		int y = top + index * 26;
		if (expandedModule == DEBUG_AREA_EXPANSION && index > 2) y += debugExpansionHeight();
		return y;
	}

	private int debugExpansionHeight() { return 82; }

	private static String coordinates(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}

	private void renderVisuals(GuiGraphics graphics, int contentX, int contentTop, int contentRight) {
		for (int module = 0; module < 9; module++) {
			int y = visualRowY(module, contentTop);
			boolean enabled = visualModuleEnabled(module);
			drawModuleRow(graphics, contentX, contentRight, y - scrollOffset,
				visualModuleName(module) + ": " + (enabled ? "ON" : "OFF"), enabled);
			if (expandedModule == module) renderVisualSettings(graphics, module, contentX, contentRight, y - scrollOffset);
		}
	}

	private void renderVisualSettings(GuiGraphics graphics, int module, int left, int right, int y) {
		switch (module) {
			case 0 -> {
				drawSettingLabel(graphics, left, right, y + 27, "Lifetime",
					String.format(Locale.ROOT, "%.2fs", BulletTracers.getLifetimeMs() / 1000.0));
				drawSlider(graphics, left, right, y + 42, lifetimeProgress());
				drawSettingLabel(graphics, left, right, y + 58, "Thickness",
					String.format(Locale.ROOT, "%.2f", BulletTracers.getThickness()));
				drawSlider(graphics, left, right, y + 73, thicknessProgress());
			}
			case 1 -> drawRangeSetting(graphics, left, right, y, LuckyChestEsp.getRenderDistance());
			case 2 -> {
				drawRangeSetting(graphics, left, right, y, MobEsp.getRenderDistance());
				drawModuleRow(graphics, left, right, y + 56,
					"Damage Blink: " + (MobEsp.isDamageBlinkEnabled() ? "ON" : "OFF"), MobEsp.isDamageBlinkEnabled());
				drawModuleRow(graphics, left, right, y + 82,
					"Health Nametag: " + MobEsp.getHealthNametagMode().name(),
					MobEsp.getHealthNametagMode() != MobEsp.HealthNametagMode.NONE);
				drawSettingLabel(graphics, left, right, y + 109, "Boss Nametag",
					MobEsp.getBossNametagDistance() + " blocks");
				drawSlider(graphics, left, right, y + 124, nametagDistanceProgress(MobEsp.getBossNametagDistance()));
				drawSettingLabel(graphics, left, right, y + 143, "Normal Nametag",
					MobEsp.getNormalNametagDistance() + " blocks");
				drawSlider(graphics, left, right, y + 158, nametagDistanceProgress(MobEsp.getNormalNametagDistance()));
			}
			case 3 -> drawRangeSetting(graphics, left, right, y, TeamEsp.getRenderDistance());
			case 4 -> drawRangeSetting(graphics, left, right, y, PowerupEsp.getRenderDistance());
			case 5 -> {
				int mapY = y + 27;
				graphics.fill(left, mapY, right, mapY + 22, 0xAA303640);
				graphics.drawString(font, "Map", left + 8, mapY + 7, 0xFFEAF7FC, false);
				String mapName = BrokenWindowEsp.getSelectedMapName();
				graphics.drawString(font, mapName + "  ‹ ›", right - font.width(mapName + "  ‹ ›") - 8,
					mapY + 7, 0xFF9FFFD7, false);
			}
			case 6 -> drawSettingLabel(graphics, left, right, y + 27, "Distance",
				PlayerVisibility.getRenderDistance() + " blocks");
			case 7 -> {
				ChatFilter.Filter[] filters = ChatFilter.Filter.values();
				for (int i = 0; i < filters.length; i++) {
					ChatFilter.Filter filter = filters[i];
					drawModuleRow(graphics, left, right, y + 27 + i * 24,
						filter.label() + ": " + (ChatFilter.isFilterEnabled(filter) ? "ON" : "OFF"),
						ChatFilter.isFilterEnabled(filter));
				}
			}
			case 8 -> {
				graphics.drawString(font, "Slabs, stairs, panes and iron bars", left + 2, y + 31, 0xFFD5DCE5, false);
				graphics.drawString(font, "are hidden locally; entities remain visible.", left + 2, y + 44, 0xFF9AA3AF, false);
			}
			default -> { }
		}
		if (module == 6) drawSlider(graphics, left, right, y + 42, (PlayerVisibility.getRenderDistance() - 4) / 60.0);
	}

	private void drawRangeSetting(GuiGraphics graphics, int left, int right, int y, int distance) {
		drawSettingLabel(graphics, left, right, y + 27, "Range", distance + " blocks");
		drawSlider(graphics, left, right, y + 42, rangeProgress(distance));
	}

	private int visualRowY(int module, int top) {
		int y = top;
		for (int previous = 0; previous < module; previous++) {
			y += 27;
			if (expandedModule == previous) y += visualExpansionHeight(previous);
		}
		return y;
	}

	private int visualExpansionHeight(int module) {
		return switch (module) { case 0 -> 84; case 2 -> 178; case 5 -> 55; case 7 -> 174; default -> 53; };
	}

	private boolean visualModuleEnabled(int module) {
		return switch (module) {
			case 0 -> BulletTracers.isEnabled(); case 1 -> LuckyChestEsp.isEnabled(); case 2 -> MobEsp.isEnabled();
			case 3 -> TeamEsp.isEnabled(); case 4 -> PowerupEsp.isEnabled(); case 5 -> BrokenWindowEsp.isEnabled();
			case 6 -> PlayerVisibility.isEnabled(); case 7 -> ChatFilter.isEnabled(); case 8 -> WallbangTransparency.isEnabled(); default -> false;
		};
	}

	private String visualModuleName(int module) {
		return switch (module) {
			case 0 -> "BulletTracers"; case 1 -> "Lucky Chest ESP"; case 2 -> "Mob ESP";
			case 3 -> "Team ESP"; case 4 -> "Powerup ESP"; case 5 -> "Broken Window ESP";
			case 6 -> "Player Visibility"; case 7 -> "Chat Filter"; case 8 -> "Wallbang Transparency"; default -> "";
		};
	}
	private void toggleVisualModule(int module) {
			switch (module) {
			case 0 -> BulletTracers.toggle(); case 1 -> LuckyChestEsp.toggle(); case 2 -> MobEsp.toggle();
			case 3 -> TeamEsp.toggle(); case 4 -> PowerupEsp.toggle(); case 5 -> BrokenWindowEsp.toggle();
			case 6 -> PlayerVisibility.toggle(); case 7 -> ChatFilter.toggle(); case 8 -> WallbangTransparency.toggle(); default -> { }
		}
	}

	private void renderHud(GuiGraphics graphics, int contentX, int contentTop, int contentRight) {
		boolean enabled = WinterHud.isWatermarkEnabled();
		drawModuleRow(graphics, contentX, contentRight, contentTop,
			"Watermark: " + (enabled ? "ON" : "OFF"), enabled);
		int trackerY = contentTop + 26;
		drawModuleRow(graphics, contentX, contentRight, trackerY,
			"Powerup Tracker: " + (PowerupTracker.isEnabled() ? "ON" : "OFF"), PowerupTracker.isEnabled());
		if (expandedModule == 30) {
			drawModuleRow(graphics, contentX, contentRight, trackerY + 27,
				"Powerup Timer: " + (PowerupTracker.isTimerEnabled() ? "ON" : "OFF"), PowerupTracker.isTimerEnabled());
			drawModuleRow(graphics, contentX, contentRight, trackerY + 53,
				"Powerup Predictor: " + (PowerupTracker.isPredictorEnabled() ? "ON" : "OFF"), PowerupTracker.isPredictorEnabled());
		}
		int scoreboardY = trackerY + 27 + (expandedModule == 30 ? 55 : 0);
		drawModuleRow(graphics, contentX, contentRight, scoreboardY,
			"Scoreboard: " + (WinterHud.isScoreboardEnabled() ? "ON" : "OFF"), WinterHud.isScoreboardEnabled());
		int bossbarY = scoreboardY + 26;
		drawModuleRow(graphics, contentX, contentRight, bossbarY,
			"Bossbar: " + (WinterHud.isBossbarEnabled() ? "ON" : "OFF"), WinterHud.isBossbarEnabled());
		drawModuleRow(graphics, contentX, contentRight, bossbarY + 26, "HUD Editor", false);
	}

	private void renderAssistance(GuiGraphics graphics, int contentX, int contentTop, int contentRight) {
		for (int index = 0; index < 6; index++) {
			int module = assistanceModuleId(index);
			int y = assistanceRowY(index, contentTop);
			boolean enabled = assistanceModuleEnabled(index);
			drawModuleRow(graphics, contentX, contentRight, y - scrollOffset,
				assistanceModuleName(index) + ": " + (enabled ? "ON" : "OFF"), enabled);
			if (expandedModule == module) renderAssistanceSettings(graphics, module, contentX, contentRight, y - scrollOffset);
		}
	}

	private void renderAssistanceSettings(GuiGraphics graphics, int module, int left, int right, int y) {
		if (module == 20) {
			drawBindRow(graphics, left, right, y + 27, "Bind", waitingForFreecamBind,
				Freecam.getBindKey() == GLFW.GLFW_KEY_UNKNOWN ? "None" : keyName(Freecam.getBindKey()));
			drawSettingLabel(graphics, left, right, y + 56, "Speed", String.format(Locale.ROOT, "%.2f blocks/tick", Freecam.getSpeed()));
			drawSlider(graphics, left, right, y + 72, (Freecam.getSpeed() - 0.1) / 1.9);
		} else if (module == 21) {
			drawBindRow(graphics, left, right, y + 27, "Bind", waitingForSkillBind, skillBindName(SkillBind.getBindCode()));
		} else if (module == 22) {
			drawBindRow(graphics, left, right, y + 27, "Aim key", waitingForAimBind, aimBindName(AimAssist.getBindCode()));
			drawSettingLabel(graphics, left, right, y + 56, "FOV", AimAssist.getFov() + "°");
			drawSlider(graphics, left, right, y + 72, AimAssist.getFovProgress());
			drawModuleRow(graphics, left, right, y + 92,
				"FOV Circle: " + (AimAssist.isFovCircleEnabled() ? "ON" : "OFF"), AimAssist.isFovCircleEnabled());
		} else if (module == 23) {
			int modeY = y + 27;
			graphics.fill(left, modeY, right, modeY + 22, 0xAA303640);
			graphics.drawString(font, "Mode", left + 8, modeY + 7, 0xFFEAF7FC, false);
			String mode = Triggerbot.isOnKeyMode() ? "OnKey  ‹ ›" : "Always  ‹ ›";
			graphics.drawString(font, mode, right - font.width(mode) - 8, modeY + 7, 0xFF9FFFD7, false);
			drawSettingLabel(graphics, left, right, y + 56, "Delay", Triggerbot.getDelayMs() + " ms");
			drawSlider(graphics, left, right, y + 72, Triggerbot.getDelayProgress());
			drawModuleRow(graphics, left, right, y + 92,
				"Through Walls: " + (Triggerbot.isThroughWalls() ? "ON" : "OFF"), Triggerbot.isThroughWalls());
			if (Triggerbot.isOnKeyMode()) {
				String bind = waitingForTriggerbotBind ? "Press a key or mouse button..." : triggerbotBindName();
				drawBindRow(graphics, left, right, y + 119, "Activation key", waitingForTriggerbotBind, bind);
			}
		} else if (module == 24) {
			drawSettingLabel(graphics, left, right, y + 27, "CPS range",
				RightClicker.getMinimumCps() + " - " + RightClicker.getMaximumCps());
			drawRangeSlider(graphics, left, right, y + 47,
				cpsProgress(RightClicker.getMinimumCps()), cpsProgress(RightClicker.getMaximumCps()));
		}
	}

	private void drawBindRow(GuiGraphics graphics, int left, int right, int y, String label, boolean waiting, String bind) {
		graphics.fill(left, y, right, y + 22, waiting ? 0xAA245A50 : 0xAA303640);
		graphics.drawString(font, label, left + 8, y + 7, 0xFFEAF7FC, false);
		String shown = trimToWidth(bind, right - left - 16);
		graphics.drawString(font, shown, right - font.width(shown) - 8, y + 7, 0xFF9AA3AF, false);
	}

	private String keyName(int code) {
		return code == GLFW.GLFW_KEY_UNKNOWN ? "None" : InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
	}

	private String triggerbotBindName() {
		int code = Triggerbot.getActivationBind();
		if (code == GLFW.GLFW_KEY_UNKNOWN) return "None";
		if (Triggerbot.isMouseBind(code)) return "Mouse " + (Triggerbot.getMouseButton(code) + 1);
		return keyName(code);
	}

	private int assistanceModuleId(int index) { return 19 + index; }
	private int assistanceRowY(int index, int top) {
		int y = top;
		for (int previous = 0; previous < index; previous++) {
			int module = assistanceModuleId(previous);
			y += 27;
			if (expandedModule == module) y += assistanceExpansionHeight(module);
		}
		return y;
	}
	private int assistanceExpansionHeight(int module) {
		return switch (module) {
			case 20 -> 82; case 21 -> 55; case 22 -> 120;
			case 23 -> Triggerbot.isOnKeyMode() ? 148 : 120; case 24 -> 67; default -> 0;
		};
	}
	private int categoryContentHeight() {
		if (selectedCategory == Category.ASSISTANCE) {
			int height = 6 * 27;
			if (expandedModule >= 20 && expandedModule <= 24) height += assistanceExpansionHeight(expandedModule);
			return height;
		}
		if (selectedCategory == Category.VISUALS) {
			int height = 9 * 27;
			if (expandedModule >= 0 && expandedModule <= 8) height += visualExpansionHeight(expandedModule);
			return height;
		}
		return 0;
	}
	private boolean assistanceModuleEnabled(int index) {
		return switch (index) {
			case 0 -> AutoRevive.isEnabled(); case 1 -> Freecam.isEnabled(); case 2 -> SkillBind.isEnabled();
			case 3 -> AimAssist.isEnabled(); case 4 -> Triggerbot.isEnabled(); case 5 -> RightClicker.isEnabled(); default -> false;
		};
	}
	private String assistanceModuleName(int index) {
		return switch (index) {
			case 0 -> "Auto Revive"; case 1 -> "Freecam"; case 2 -> "Skill Bind";
			case 3 -> "Aim Assist"; case 4 -> "Triggerbot"; case 5 -> "Right Clicker"; default -> "";
		};
	}
	private void toggleAssistanceModule(int index) {
		switch (index) {
			case 0 -> AutoRevive.toggle(); case 1 -> Freecam.toggle(); case 2 -> SkillBind.toggle();
			case 3 -> AimAssist.toggle(); case 4 -> Triggerbot.toggle(); case 5 -> RightClicker.toggle(); default -> { }
		}
	}
	private static boolean inside(int value, int start, int end) { return value >= start && value <= end; }

	private String aimBindName(int code) {
		if (AimAssist.isMouseBind(code)) return "Mouse " + (AimAssist.getMouseButton(code) + 1);
		if (code == GLFW.GLFW_KEY_UNKNOWN) return "None";
		return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
	}

	private String skillBindName(int code) {
		if (code == GLFW.GLFW_KEY_UNKNOWN) return "None";
		if (SkillBind.isMouseBind(code)) return "Mouse " + (SkillBind.getMouseButton(code) + 1);
		return InputConstants.Type.KEYSYM.getOrCreate(code).getDisplayName().getString();
	}

	private void drawSettingLabel(GuiGraphics graphics, int left, int right, int y, String name, String value) {
		graphics.drawString(font, name, left + 2, y, 0xFFEAF7FC, false);
		graphics.drawString(font, value, right - font.width(value), y, 0xFF9AA3AF, false);
	}

	private void drawModuleRow(GuiGraphics graphics, int left, int right, int y, String label, boolean enabled) {
		graphics.fill(left, y, right, y + 21, enabled ? 0xAA245A50 : 0xAA303640);
		graphics.drawString(font, label, left + 8, y + 6, enabled ? 0xFF9FFFD7 : 0xFFD3D9E0, false);
	}

	private void drawSlider(GuiGraphics graphics, int left, int right, int y, double progress) {
		graphics.fill(left, y, right, y + 4, 0xFF38414D);
		int knobX = left + (int) Math.round((right - left) * progress);
		graphics.fill(left, y, knobX, y + 4, 0xFF70D8F5);
		graphics.fill(knobX - 3, y - 3, knobX + 4, y + 7, 0xFFEAF7FC);
	}

	private void drawRangeSlider(GuiGraphics graphics, int left, int right, int y, double minProgress, double maxProgress) {
		graphics.fill(left, y, right, y + 4, 0xFF38414D);
		int minX = left + (int) Math.round((right - left) * minProgress);
		int maxX = left + (int) Math.round((right - left) * maxProgress);
		graphics.fill(minX, y, maxX, y + 4, 0xFF70D8F5);
		graphics.fill(minX - 3, y - 3, minX + 4, y + 7, 0xFFEAF7FC);
		graphics.fill(maxX - 3, y - 3, maxX + 4, y + 7, 0xFFEAF7FC);
	}

	private double lifetimeProgress() { return (BulletTracers.getLifetimeMs() - 200) / 2800.0; }
	private double thicknessProgress() { return (BulletTracers.getThickness() - 0.08) / 0.56; }
	private double rangeProgress(int distance) { return (distance - 16) / 112.0; }
	private double nametagDistanceProgress(int distance) { return distance / 128.0; }
	private double cpsProgress(int cps) { return (cps - 1) / 19.0; }
	private int contentLeft() { return panelX + SIDEBAR_WIDTH + 4 + CONTENT_PADDING; }
	private int contentRight() { return panelX + panelWidth - 12; }
	private int contentTop() { return panelY + HEADER_HEIGHT + 14; }

	private void updateSlider(double mouseX) {
		double progress = Math.max(0.0, Math.min(1.0, (mouseX - contentLeft()) / (contentRight() - contentLeft())));
		if (activeSlider == 0) {
			int value = 200 + (int) Math.round(progress * 2800.0);
			BulletTracers.setLifetimeMs((int) Math.round(value / 50.0) * 50);
		} else if (activeSlider == 1) {
			BulletTracers.setThickness((float) (0.08 + progress * 0.56));
		} else if (activeSlider == 2) {
			LuckyChestEsp.setRenderDistance(16 + (int) Math.round(progress * 112.0));
		} else if (activeSlider == 3) {
			MobEsp.setRenderDistance(16 + (int) Math.round(progress * 112.0));
		} else if (activeSlider == 4) {
			TeamEsp.setRenderDistance(16 + (int) Math.round(progress * 112.0));
		} else if (activeSlider == 5) {
			PowerupEsp.setRenderDistance(16 + (int) Math.round(progress * 112.0));
		} else if (activeSlider == 6) {
			Freecam.setSpeed(0.1 + progress * 1.9);
		} else if (activeSlider == 7) {
			PlayerVisibility.setRenderDistance(4 + (int) Math.round(progress * 60.0));
		} else if (activeSlider == 8) {
			AimAssist.setFov(5 + (int) Math.round(progress * 55.0));
		} else if (activeSlider == 9) {
			Triggerbot.setDelayMs((int) Math.round(progress * 50.0) * 10);
		} else if (activeSlider == 10) {
			MobEsp.setBossNametagDistance((int) Math.round(progress * 128.0));
		} else if (activeSlider == 11) {
			MobEsp.setNormalNametagDistance((int) Math.round(progress * 128.0));
		} else if (activeSlider == 12) {
			RightClicker.setMinimumCps(1 + (int) Math.round(progress * 19.0));
		} else if (activeSlider == 13) {
			RightClicker.setMaximumCps(1 + (int) Math.round(progress * 19.0));
		}
	}

	private void drawGradientTitle(GuiGraphics graphics) {
		String title = "Winter";
		float scale = 2.0F;
		int titleWidth = font.width(title);
		float x = (width - titleWidth * scale) / 2.0F;
		float y = panelY + 16;
		long time = System.currentTimeMillis();
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		float drawX = x / scale;
		for (int i = 0; i < title.length(); i++) {
			float wave = (float) (Math.sin((time / 520.0) + i * 0.65) + 1.0) / 2.0F;
			int color = blend(0xFF75DFFF, 0xFFFFFFFF, wave);
			String character = title.substring(i, i + 1);
			graphics.drawString(font, character, Math.round(drawX), Math.round(y / scale), color, false);
			drawX += font.width(character);
		}
		graphics.pose().popMatrix();
	}

	private static int blend(int colorA, int colorB, float amount) {
		int red = (int) (((colorA >> 16) & 0xFF) * (1.0F - amount) + ((colorB >> 16) & 0xFF) * amount);
		int green = (int) (((colorA >> 8) & 0xFF) * (1.0F - amount) + ((colorB >> 8) & 0xFF) * amount);
		int blue = (int) ((colorA & 0xFF) * (1.0F - amount) + (colorB & 0xFF) * amount);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private String trimToWidth(String text, int maxWidth) {
		String result = text;
		while (!result.isEmpty() && font.width(result) > maxWidth) result = result.substring(0, result.length() - 1);
		return result.equals(text) ? result : result + "…";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (waitingForTriggerbotBind) {
			if (event.button() >= GLFW.GLFW_MOUSE_BUTTON_1 && event.button() <= GLFW.GLFW_MOUSE_BUTTON_8) {
				Triggerbot.setActivationBind(1000 + event.button());
				waitingForTriggerbotBind = false;
			}
			return true;
		}
		if (waitingForAimBind) {
			if (event.button() >= GLFW.GLFW_MOUSE_BUTTON_1 && event.button() <= GLFW.GLFW_MOUSE_BUTTON_8) {
				AimAssist.setBindCode(1000 + event.button());
				waitingForAimBind = false;
			}
			return true;
		}
		if (waitingForSkillBind) {
			if (event.button() >= GLFW.GLFW_MOUSE_BUTTON_4 && event.button() <= GLFW.GLFW_MOUSE_BUTTON_8) {
				SkillBind.setBindCode(1000 + event.button());
				waitingForSkillBind = false;
			}
			return true;
		}
		int sidebarX = panelX + 10;
		int sidebarY = panelY + HEADER_HEIGHT + 12;
		if (event.button() == 0 && event.x() >= sidebarX && event.x() < panelX + SIDEBAR_WIDTH) {
			if (event.y() >= sidebarY && event.y() < sidebarY + 24) {
				setSelectedCategory(Category.ASSISTANCE);
				scrollOffset = 0;
				return true;
			}
			if (event.y() >= sidebarY + 30 && event.y() < sidebarY + 54) {
				setSelectedCategory(Category.VISUALS);
				scrollOffset = 0;
				return true;
			}
			if (event.y() >= sidebarY + 60 && event.y() < sidebarY + 84) {
				setSelectedCategory(Category.HUD);
				scrollOffset = 0;
				return true;
			}
			if (event.y() >= sidebarY + 90 && event.y() < sidebarY + 114) {
				setSelectedCategory(Category.DEBUG);
				scrollOffset = 0;
				return true;
			}
		}

		int left = contentLeft();
		int right = contentRight();
		int top = contentTop();
		if (event.x() >= left && event.x() <= right) {
			if (selectedCategory == Category.DEBUG) {
				if (event.button() == 0 && inside((int) event.y(), debugRowY(0, top), debugRowY(0, top) + 21)) {
					ShotDebugModule.toggle();
					return true;
				}
				if (event.button() == 0 && inside((int) event.y(), debugRowY(1, top), debugRowY(1, top) + 21)) {
					MobDebugModule.toggle();
					return true;
				}
				int areaY = debugRowY(2, top);
				if (inside((int) event.y(), areaY, areaY + 21)) {
					if (event.button() == 0) AreaSelector.toggle();
					else if (event.button() == 1)
						setExpandedModule(expandedModule == DEBUG_AREA_EXPANSION ? -1 : DEBUG_AREA_EXPANSION);
					return true;
				}
				int saveY = areaY + 55;
				if (expandedModule == DEBUG_AREA_EXPANSION && event.button() == 0
					&& event.y() >= saveY && event.y() < saveY + 20) {
					AreaSelector.saveCurrentArea();
					return true;
				}
				int drawPredictY = debugRowY(3, top);
				if (event.button() == 0 && inside((int) event.y(), drawPredictY, drawPredictY + 21)) {
					DrawPredict.toggle();
					return true;
				}
			} else if (selectedCategory == Category.HUD && event.button() == 0) {
				if (event.y() >= top && event.y() <= top + 21) {
					WinterHud.toggleWatermark();
					return true;
				}
				int trackerY = top + 26;
				if (inside((int) event.y(), trackerY, trackerY + 21)) {
					PowerupTracker.setEnabled(!PowerupTracker.isEnabled());
					return true;
				}
				if (expandedModule == 30 && inside((int) event.y(), trackerY + 27, trackerY + 48)) {
					PowerupTracker.setTimerEnabled(!PowerupTracker.isTimerEnabled());
					return true;
				}
				if (expandedModule == 30 && inside((int) event.y(), trackerY + 53, trackerY + 74)) {
					PowerupTracker.setPredictorEnabled(!PowerupTracker.isPredictorEnabled());
					return true;
				}
				int scoreboardY = trackerY + 27 + (expandedModule == 30 ? 55 : 0);
				if (inside((int) event.y(), scoreboardY, scoreboardY + 21)) {
					WinterHud.toggleScoreboard();
					return true;
				}
				int bossbarY = scoreboardY + 26;
				if (inside((int) event.y(), bossbarY, bossbarY + 21)) {
					WinterHud.toggleBossbar();
					return true;
				}
				if (inside((int) event.y(), bossbarY + 26, bossbarY + 47)) {
					this.minecraft.setScreen(new HudEditorScreen(this));
					return true;
				}
			} else if (selectedCategory == Category.HUD && event.button() == 1) {
				int trackerY = top + 26;
				if (inside((int) event.y(), trackerY, trackerY + 21)) {
					setExpandedModule(expandedModule == 30 ? -1 : 30);
					return true;
				}
			} else if (selectedCategory == Category.ASSISTANCE) {
				int y = (int) event.y() + scrollOffset;
				for (int index = 0; index < 6; index++) {
					int module = assistanceModuleId(index);
					int rowY = assistanceRowY(index, top);
					if (y >= rowY && y < rowY + 21) {
						if (event.button() == 0) toggleAssistanceModule(index);
						else if (event.button() == 1 && module != 19) setExpandedModule(expandedModule == module ? -1 : module);
						return true;
					}
				}
				if (event.button() == 0 && expandedModule == 20) {
					int rowY = assistanceRowY(1, top);
					if (inside(y, rowY + 27, rowY + 49)) { waitingForFreecamBind = true; return true; }
					if (Math.abs(y - (rowY + 72)) <= 6) { activeSlider = 6; updateSlider(event.x()); return true; }
				} else if (event.button() == 0 && expandedModule == 21) {
					int rowY = assistanceRowY(2, top);
					if (inside(y, rowY + 27, rowY + 49)) { waitingForSkillBind = true; return true; }
				} else if (event.button() == 0 && expandedModule == 22) {
					int rowY = assistanceRowY(3, top);
					if (inside(y, rowY + 27, rowY + 49)) { waitingForAimBind = true; return true; }
					if (inside(y, rowY + 92, rowY + 113)) {
						AimAssist.setFovCircleEnabled(!AimAssist.isFovCircleEnabled()); return true;
					}
					if (Math.abs(y - (rowY + 72)) <= 6) { activeSlider = 8; updateSlider(event.x()); return true; }
				} else if (event.button() == 0 && expandedModule == 23) {
					int rowY = assistanceRowY(4, top);
					if (inside(y, rowY + 27, rowY + 49)) {
						Triggerbot.setOnKeyMode(!Triggerbot.isOnKeyMode());
						return true;
					}
					if (Math.abs(y - (rowY + 72)) <= 6) { activeSlider = 9; updateSlider(event.x()); return true; }
					if (inside(y, rowY + 92, rowY + 113)) {
						Triggerbot.setThroughWalls(!Triggerbot.isThroughWalls()); return true;
					}
					if (Triggerbot.isOnKeyMode() && inside(y, rowY + 119, rowY + 141)) {
						waitingForTriggerbotBind = true; return true;
					}
				} else if (event.button() == 0 && expandedModule == 24) {
					int rowY = assistanceRowY(5, top);
					if (Math.abs(y - (rowY + 47)) <= 7) {
						int minX = contentLeft() + (int) Math.round((contentRight() - contentLeft()) * cpsProgress(RightClicker.getMinimumCps()));
						int maxX = contentLeft() + (int) Math.round((contentRight() - contentLeft()) * cpsProgress(RightClicker.getMaximumCps()));
						activeSlider = Math.abs(event.x() - minX) <= Math.abs(event.x() - maxX) ? 12 : 13;
						updateSlider(event.x());
						return true;
					}
				}
			} else if (selectedCategory == Category.VISUALS) {
				int y = (int) event.y() + scrollOffset;
				for (int module = 0; module < 9; module++) {
					int rowY = visualRowY(module, top);
					if (inside(y, rowY, rowY + 20)) {
						if (event.button() == 0) toggleVisualModule(module);
						else if (event.button() == 1) setExpandedModule(expandedModule == module ? -1 : module);
						return true;
					}
				}
				if (event.button() == 0 && expandedModule >= 0 && expandedModule <= 8) {
					int rowY = visualRowY(expandedModule, top);
					if (expandedModule == 7) {
						ChatFilter.Filter[] filters = ChatFilter.Filter.values();
						for (int i = 0; i < filters.length; i++) {
							if (inside(y, rowY + 27 + i * 24, rowY + 48 + i * 24)) {
								ChatFilter.Filter filter = filters[i];
								ChatFilter.setFilterEnabled(filter, !ChatFilter.isFilterEnabled(filter));
								return true;
							}
						}
					}
					if (expandedModule == 5 && inside(y, rowY + 27, rowY + 49)) {
						BrokenWindowEsp.cycleMap(); return true;
					}
					if (expandedModule == 2 && inside(y, rowY + 56, rowY + 77)) {
						MobEsp.setDamageBlinkEnabled(!MobEsp.isDamageBlinkEnabled()); return true;
					}
					if (expandedModule == 2 && inside(y, rowY + 82, rowY + 104)) {
						MobEsp.cycleHealthNametagMode(); return true;
					}
					if (expandedModule == 0 && Math.abs(y - (rowY + 42)) <= 6) activeSlider = 0;
					else if (expandedModule == 0 && Math.abs(y - (rowY + 73)) <= 6) activeSlider = 1;
					else if (expandedModule == 1 && Math.abs(y - (rowY + 42)) <= 6) activeSlider = 2;
					else if (expandedModule == 2 && Math.abs(y - (rowY + 42)) <= 6) activeSlider = 3;
					else if (expandedModule == 3 && Math.abs(y - (rowY + 42)) <= 6) activeSlider = 4;
					else if (expandedModule == 4 && Math.abs(y - (rowY + 42)) <= 6) activeSlider = 5;
					else if (expandedModule == 6 && Math.abs(y - (rowY + 42)) <= 6) activeSlider = 7;
					else if (expandedModule == 2 && Math.abs(y - (rowY + 124)) <= 6) activeSlider = 10;
					else if (expandedModule == 2 && Math.abs(y - (rowY + 158)) <= 6) activeSlider = 11;
					if (activeSlider != -1) { updateSlider(event.x()); return true; }
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (waitingForTriggerbotBind) {
			int key = event.key();
			if (key == GLFW.GLFW_KEY_ESCAPE) waitingForTriggerbotBind = false;
			else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
				Triggerbot.setActivationBind(GLFW.GLFW_KEY_UNKNOWN);
				waitingForTriggerbotBind = false;
			} else if (key != GLFW.GLFW_KEY_UNKNOWN) {
				Triggerbot.setActivationBind(key);
				waitingForTriggerbotBind = false;
			}
			return true;
		}
		if (waitingForAimBind) {
			int key = event.key();
			if (key == GLFW.GLFW_KEY_ESCAPE) waitingForAimBind = false;
			else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
				AimAssist.setBindCode(GLFW.GLFW_KEY_UNKNOWN);
				waitingForAimBind = false;
			} else if (key != GLFW.GLFW_KEY_UNKNOWN) {
				AimAssist.setBindCode(key);
				waitingForAimBind = false;
			}
			return true;
		}
		if (waitingForSkillBind) {
			int key = event.key();
			if (key == GLFW.GLFW_KEY_ESCAPE) waitingForSkillBind = false;
			else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
				SkillBind.setBindCode(GLFW.GLFW_KEY_UNKNOWN);
				waitingForSkillBind = false;
			} else if (key != GLFW.GLFW_KEY_UNKNOWN) {
				SkillBind.setBindCode(key);
				waitingForSkillBind = false;
			}
			return true;
		}
		if (waitingForFreecamBind) {
			int key = event.key();
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				waitingForFreecamBind = false;
			} else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
				Freecam.setBindKey(GLFW.GLFW_KEY_UNKNOWN);
				waitingForFreecamBind = false;
			} else if (key != GLFW.GLFW_KEY_UNKNOWN) {
				Freecam.setBindKey(key);
				waitingForFreecamBind = false;
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (activeSlider != -1) {
			updateSlider(event.x());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (activeSlider != -1) {
			activeSlider = -1;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int contentLeft = contentLeft();
		int contentRight = contentRight();
		int contentTop = contentTop();
		int contentBottom = panelY + panelHeight - 12;
		if ((selectedCategory == Category.ASSISTANCE || selectedCategory == Category.VISUALS)
			&& mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom) {
			int visibleHeight = contentBottom - contentTop;
			int maxScroll = Math.max(0, categoryContentHeight() - visibleHeight);
			scrollOffset = Math.max(0, Math.min(maxScroll,
				scrollOffset - (int) Math.signum(verticalAmount) * 18));
			return true;
		}
		if (selectedCategory == Category.DEBUG && mouseX >= contentLeft && mouseX <= contentRight
			&& mouseY >= contentTop && mouseY <= contentBottom) {
			int eventsTop = debugEventsTop();
			int visibleHeight = contentBottom - eventsTop;
			int maxScroll = Math.max(0, ShotDebugModule.getEvents().size() * EVENT_ROW_HEIGHT - visibleHeight);
			scrollOffset = Math.max(0, Math.min(maxScroll,
				scrollOffset - (int) Math.signum(verticalAmount) * 12));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}

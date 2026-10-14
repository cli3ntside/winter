package dev.luxnet.winter.client;

import dev.luxnet.winter.client.debug.MobDebugModule;
import dev.luxnet.winter.client.debug.AreaSelector;
import dev.luxnet.winter.client.assistance.AutoRevive;
import dev.luxnet.winter.client.assistance.Freecam;
import dev.luxnet.winter.client.debug.ShotDebugModule;
import dev.luxnet.winter.client.visuals.BulletTracers;
import dev.luxnet.winter.client.visuals.LuckyChestEsp;
import dev.luxnet.winter.client.visuals.MobEsp;
import dev.luxnet.winter.client.visuals.TeamEsp;
import dev.luxnet.winter.client.visuals.PowerupEsp;
import dev.luxnet.winter.client.visuals.BrokenWindowEsp;
import dev.luxnet.winter.client.WinterHud;
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
		savedExpandedModule = module == DEBUG_AREA_EXPANSION || module == 20 || module >= 0 && module <= 5
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
		drawModuleRow(graphics, contentX, contentRight, contentTop, "Capture: " + (captureEnabled ? "ON" : "OFF"), captureEnabled);

		boolean mobsEnabled = MobDebugModule.isEnabled();
		int mobsY = contentTop + 26;
		String mobsLabel = "Detect Mobs: " + (mobsEnabled ? "ON" : "OFF");
		if (mobsEnabled) mobsLabel += " (" + MobDebugModule.getSnapshots().size() + ")";
		drawModuleRow(graphics, contentX, contentRight, mobsY, mobsLabel, mobsEnabled);
		int areaY = contentTop + 52;
		boolean areaEnabled = AreaSelector.isEnabled();
		drawModuleRow(graphics, contentX, contentRight, areaY,
			"Area Selector: " + (areaEnabled ? "ON" : "OFF"), areaEnabled);

		int eventsTop = debugEventsTop();
		if (expandedModule == DEBUG_AREA_EXPANSION) {
			BlockPos first = AreaSelector.getFirstCorner();
			BlockPos second = AreaSelector.getSecondCorner();
			String firstText = first == null ? "Point 1: right click a block" : "Point 1: " + coordinates(first);
			String secondText = second == null ? (first == null ? "Point 2: —" : "Point 2: right click a block")
				: "Point 2: " + coordinates(second);
			graphics.drawString(font, trimToWidth(firstText, contentRight - contentX), contentX + 2,
				contentTop + 80, 0xFFD5DCE5, false);
			graphics.drawString(font, trimToWidth(secondText, contentRight - contentX), contentX + 2,
				contentTop + 93, 0xFFD5DCE5, false);
			int saveY = contentTop + 108;
			boolean canSave = AreaSelector.canSave();
			boolean hovered = mouseX >= contentX && mouseX <= contentRight && mouseY >= saveY && mouseY < saveY + 20;
			graphics.fill(contentX, saveY, contentRight, saveY + 20,
				canSave ? (hovered ? 0xCC337D8E : 0xAA285E6A) : 0xAA303640);
			String saveLabel = "Save area  ·  " + AreaSelector.getSavedAreaCount() + " saved";
			graphics.drawString(font, saveLabel, contentX + 8, saveY + 6,
				canSave ? 0xFFEAF7FC : 0xFF9AA3AF, false);
			if (!AreaSelector.getSaveStatus().isEmpty()) {
				graphics.drawString(font, trimToWidth(AreaSelector.getSaveStatus(), contentRight - contentX),
					contentX + 2, contentTop + 132, 0xFF9AA3AF, false);
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
		return contentTop() + (expandedModule == DEBUG_AREA_EXPANSION ? 152 : 80);
	}

	private static String coordinates(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}

	private void renderVisuals(GuiGraphics graphics, int contentX, int contentTop, int contentRight) {
		boolean enabled = BulletTracers.isEnabled();
		drawModuleRow(graphics, contentX, contentRight, contentTop,
			"BulletTracers: " + (enabled ? "ON" : "OFF"), enabled);
		boolean chestEspEnabled = LuckyChestEsp.isEnabled();
		drawModuleRow(graphics, contentX, contentRight, contentTop + 27,
			"Lucky Chest ESP: " + (chestEspEnabled ? "ON" : "OFF"), chestEspEnabled);
		boolean mobEspEnabled = MobEsp.isEnabled();
		drawModuleRow(graphics, contentX, contentRight, contentTop + 54,
			"Mob ESP: " + (mobEspEnabled ? "ON" : "OFF"), mobEspEnabled);
		boolean teamEspEnabled = TeamEsp.isEnabled();
		drawModuleRow(graphics, contentX, contentRight, contentTop + 81,
			"Team ESP: " + (teamEspEnabled ? "ON" : "OFF"), teamEspEnabled);
		boolean powerupEspEnabled = PowerupEsp.isEnabled();
		drawModuleRow(graphics, contentX, contentRight, contentTop + 108,
			"Powerup ESP: " + (powerupEspEnabled ? "ON" : "OFF"), powerupEspEnabled);
		boolean brokenWindowEspEnabled = BrokenWindowEsp.isEnabled();
		drawModuleRow(graphics, contentX, contentRight, contentTop + 135,
			"Broken Window ESP: " + (brokenWindowEspEnabled ? "ON" : "OFF"), brokenWindowEspEnabled);
		if (expandedModule == 0) {
			drawSettingLabel(graphics, contentX, contentRight, contentTop + 163, "Lifetime",
				String.format(Locale.ROOT, "%.2fs", BulletTracers.getLifetimeMs() / 1000.0));
			drawSlider(graphics, contentX, contentRight, contentTop + 178, lifetimeProgress());
			drawSettingLabel(graphics, contentX, contentRight, contentTop + 198, "Thickness",
				String.format(Locale.ROOT, "%.2f", BulletTracers.getThickness()));
			drawSlider(graphics, contentX, contentRight, contentTop + 213, thicknessProgress());
		} else if (expandedModule == 1) {
			drawSettingLabel(graphics, contentX, contentRight, contentTop + 164, "Range",
				LuckyChestEsp.getRenderDistance() + " blocks");
			drawSlider(graphics, contentX, contentRight, contentTop + 181, rangeProgress(LuckyChestEsp.getRenderDistance()));
		} else if (expandedModule == 2) {
			drawSettingLabel(graphics, contentX, contentRight, contentTop + 164, "Range",
				MobEsp.getRenderDistance() + " blocks");
			drawSlider(graphics, contentX, contentRight, contentTop + 181, rangeProgress(MobEsp.getRenderDistance()));
		} else if (expandedModule == 3) {
			drawSettingLabel(graphics, contentX, contentRight, contentTop + 164, "Range",
				TeamEsp.getRenderDistance() + " blocks");
			drawSlider(graphics, contentX, contentRight, contentTop + 181, rangeProgress(TeamEsp.getRenderDistance()));
		} else if (expandedModule == 4) {
			drawSettingLabel(graphics, contentX, contentRight, contentTop + 164, "Range",
				PowerupEsp.getRenderDistance() + " blocks");
			drawSlider(graphics, contentX, contentRight, contentTop + 181, rangeProgress(PowerupEsp.getRenderDistance()));
		} else if (expandedModule == 5) {
			int mapY = contentTop + 163;
			graphics.fill(contentX, mapY, contentRight, mapY + 22, 0xAA303640);
			graphics.drawString(font, "Map", contentX + 8, mapY + 7, 0xFFEAF7FC, false);
			String mapName = BrokenWindowEsp.getSelectedMapName();
			graphics.drawString(font, mapName + "  ‹ ›", contentRight - font.width(mapName + "  ‹ ›") - 8,
				mapY + 7, 0xFF9FFFD7, false);
		}
	}

	private void renderHud(GuiGraphics graphics, int contentX, int contentTop, int contentRight) {
		boolean enabled = WinterHud.isWatermarkEnabled();
		drawModuleRow(graphics, contentX, contentRight, contentTop,
			"Watermark: " + (enabled ? "ON" : "OFF"), enabled);
	}

	private void renderAssistance(GuiGraphics graphics, int contentX, int contentTop, int contentRight) {
		boolean reviveEnabled = AutoRevive.isEnabled();
		drawModuleRow(graphics, contentX, contentRight, contentTop,
			"Auto Revive: " + (reviveEnabled ? "ON" : "OFF"), reviveEnabled);
		boolean freecamEnabled = Freecam.isEnabled();
		drawModuleRow(graphics, contentX, contentRight, contentTop + 27,
			"Freecam: " + (freecamEnabled ? "ON" : "OFF"), freecamEnabled);
		if (expandedModule == 20) {
			String bind = waitingForFreecamBind ? "Press a key..." : Freecam.getBindKey() == GLFW.GLFW_KEY_UNKNOWN
				? "None" : InputConstants.Type.KEYSYM.getOrCreate(Freecam.getBindKey()).getDisplayName().getString();
			int bindY = contentTop + 54;
			graphics.fill(contentX, bindY, contentRight, bindY + 22, waitingForFreecamBind ? 0xAA245A50 : 0xAA303640);
			graphics.drawString(font, "Bind (key)", contentX + 8, bindY + 7, 0xFFEAF7FC, false);
			graphics.drawString(font, bind, contentRight - font.width(bind) - 8, bindY + 7, 0xFF9AA3AF, false);
			drawSettingLabel(graphics, contentX, contentRight, contentTop + 84, "Speed",
				String.format(Locale.ROOT, "%.2f blocks/tick", Freecam.getSpeed()));
			drawSlider(graphics, contentX, contentRight, freecamSliderY(), (Freecam.getSpeed() - 0.1) / 1.9);
		}
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

	private double lifetimeProgress() { return (BulletTracers.getLifetimeMs() - 200) / 2800.0; }
	private double thicknessProgress() { return (BulletTracers.getThickness() - 0.08) / 0.56; }
	private double rangeProgress(int distance) { return (distance - 16) / 112.0; }
	private int contentLeft() { return panelX + SIDEBAR_WIDTH + 4 + CONTENT_PADDING; }
	private int contentRight() { return panelX + panelWidth - 12; }
	private int contentTop() { return panelY + HEADER_HEIGHT + 14; }
	private int lifetimeSliderY() { return contentTop() + 178; }
	private int thicknessSliderY() { return contentTop() + 213; }
	private int rangeSliderY() { return contentTop() + 181; }
	private int freecamSliderY() { return contentTop() + 101; }

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
				if (event.button() == 0 && event.y() >= top && event.y() <= top + 21) {
					ShotDebugModule.toggle();
					return true;
				}
				if (event.button() == 0 && event.y() >= top + 26 && event.y() <= top + 47) {
					MobDebugModule.toggle();
					return true;
				}
				if (event.y() >= top + 52 && event.y() <= top + 73) {
					if (event.button() == 0) AreaSelector.toggle();
					else if (event.button() == 1)
						setExpandedModule(expandedModule == DEBUG_AREA_EXPANSION ? -1 : DEBUG_AREA_EXPANSION);
					return true;
				}
				if (expandedModule == DEBUG_AREA_EXPANSION && event.button() == 0
					&& event.y() >= top + 108 && event.y() < top + 128) {
					AreaSelector.saveCurrentArea();
					return true;
				}
			} else if (selectedCategory == Category.HUD && event.button() == 0) {
				if (event.y() >= top && event.y() <= top + 21) {
					WinterHud.toggleWatermark();
					return true;
				}
			} else if (selectedCategory == Category.ASSISTANCE) {
				if (event.button() == 0 && event.y() >= top && event.y() <= top + 21) {
					AutoRevive.toggle();
					return true;
				}
				if (event.y() >= top + 27 && event.y() <= top + 48) {
					if (event.button() == 0) Freecam.toggle();
					else if (event.button() == 1) setExpandedModule(expandedModule == 20 ? -1 : 20);
					return true;
				}
				if (expandedModule == 20 && event.button() == 0 && event.y() >= top + 54 && event.y() <= top + 76) {
					waitingForFreecamBind = true;
					return true;
				}
				if (expandedModule == 20 && event.button() == 0 && Math.abs(event.y() - freecamSliderY()) <= 6) {
					activeSlider = 6;
					updateSlider(event.x());
					return true;
				}
			} else if (selectedCategory == Category.VISUALS) {
				if (event.y() >= top && event.y() <= top + 21) {
					if (event.button() == 0) BulletTracers.toggle();
					else if (event.button() == 1) setExpandedModule(expandedModule == 0 ? -1 : 0);
					return true;
				}
				if (event.y() >= top + 27 && event.y() <= top + 48) {
					if (event.button() == 0) LuckyChestEsp.toggle();
					else if (event.button() == 1) setExpandedModule(expandedModule == 1 ? -1 : 1);
					return true;
				}
				if (event.y() >= top + 54 && event.y() <= top + 75) {
					if (event.button() == 0) MobEsp.toggle();
					else if (event.button() == 1) setExpandedModule(expandedModule == 2 ? -1 : 2);
					return true;
				}
				if (event.y() >= top + 81 && event.y() <= top + 102) {
					if (event.button() == 0) TeamEsp.toggle();
					else if (event.button() == 1) setExpandedModule(expandedModule == 3 ? -1 : 3);
					return true;
				}
				if (event.y() >= top + 108 && event.y() <= top + 129) {
					if (event.button() == 0) PowerupEsp.toggle();
					else if (event.button() == 1) setExpandedModule(expandedModule == 4 ? -1 : 4);
					return true;
				}
				if (event.y() >= top + 135 && event.y() <= top + 156) {
					if (event.button() == 0) BrokenWindowEsp.toggle();
					else if (event.button() == 1) setExpandedModule(expandedModule == 5 ? -1 : 5);
					return true;
				}
				if (expandedModule == 5 && event.button() == 0
					&& event.y() >= top + 163 && event.y() <= top + 185) {
					BrokenWindowEsp.cycleMap();
					return true;
				}
				if (expandedModule != -1 && event.button() == 0) {
					if (expandedModule == 0 && Math.abs(event.y() - lifetimeSliderY()) <= 6) activeSlider = 0;
					else if (expandedModule == 0 && Math.abs(event.y() - thicknessSliderY()) <= 6) activeSlider = 1;
					else if (expandedModule == 1 && Math.abs(event.y() - rangeSliderY()) <= 6) activeSlider = 2;
					else if (expandedModule == 2 && Math.abs(event.y() - rangeSliderY()) <= 6) activeSlider = 3;
					else if (expandedModule == 3 && Math.abs(event.y() - rangeSliderY()) <= 6) activeSlider = 4;
					else if (expandedModule == 4 && Math.abs(event.y() - rangeSliderY()) <= 6) activeSlider = 5;
					if (activeSlider != -1) {
						updateSlider(event.x());
						return true;
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
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

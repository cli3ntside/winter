package dev.luxnet.winter.client;

import dev.luxnet.winter.client.debug.MobDebugModule;
import dev.luxnet.winter.client.debug.ShotDebugModule;
import dev.luxnet.winter.client.visuals.BulletTracers;
import dev.luxnet.winter.client.visuals.LuckyChestEsp;
import dev.luxnet.winter.client.visuals.MobEsp;
import dev.luxnet.winter.client.visuals.TeamEsp;
import dev.luxnet.winter.client.visuals.PowerupEsp;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int scrollOffset;
	private int activeSlider = -1;
	private int expandedModule = -1;
	private Category selectedCategory = Category.DEBUG;

	private enum Category { DEBUG, VISUALS }

	public WinterScreen() {
		super(Component.literal("Winter"));
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
		drawCategoryTab(graphics, mouseX, mouseY, sidebarX, sidebarY, sidebarRight, "Debug", Category.DEBUG);
		drawCategoryTab(graphics, mouseX, mouseY, sidebarX, sidebarY + 30, sidebarRight, "Visuals", Category.VISUALS);

		int dividerX = panelX + SIDEBAR_WIDTH + 4;
		graphics.fill(dividerX, panelY + HEADER_HEIGHT + 10, dividerX + 1, panelY + panelHeight - 12, 0x665E6672);
		int contentX = dividerX + CONTENT_PADDING;
		int contentRight = panelX + panelWidth - 12;
		int contentTop = panelY + HEADER_HEIGHT + 14;
		int contentBottom = panelY + panelHeight - 12;
		graphics.enableScissor(contentX, contentTop, contentRight, contentBottom);
		if (selectedCategory == Category.DEBUG) {
			renderDebug(graphics, mouseX, mouseY, contentX, contentTop, contentBottom, contentRight);
		} else {
			renderVisuals(graphics, contentX, contentTop, contentRight);
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

		List<String> events = ShotDebugModule.getEvents();
		int eventsTop = contentTop + 54;
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
				selectedCategory = Category.DEBUG;
				scrollOffset = 0;
				return true;
			}
			if (event.y() >= sidebarY + 30 && event.y() < sidebarY + 54) {
				selectedCategory = Category.VISUALS;
				scrollOffset = 0;
				return true;
			}
		}

		int left = contentLeft();
		int right = contentRight();
		int top = contentTop();
		if (event.x() >= left && event.x() <= right) {
			if (selectedCategory == Category.DEBUG && event.button() == 0) {
				if (event.y() >= top && event.y() <= top + 21) {
					ShotDebugModule.toggle();
					return true;
				}
				if (event.y() >= top + 26 && event.y() <= top + 47) {
					MobDebugModule.toggle();
					return true;
				}
			} else if (selectedCategory == Category.VISUALS) {
				if (event.y() >= top && event.y() <= top + 21) {
					if (event.button() == 0) BulletTracers.toggle();
					else if (event.button() == 1) expandedModule = expandedModule == 0 ? -1 : 0;
					return true;
				}
				if (event.y() >= top + 27 && event.y() <= top + 48) {
					if (event.button() == 0) LuckyChestEsp.toggle();
					else if (event.button() == 1) expandedModule = expandedModule == 1 ? -1 : 1;
					return true;
				}
				if (event.y() >= top + 54 && event.y() <= top + 75) {
					if (event.button() == 0) MobEsp.toggle();
					else if (event.button() == 1) expandedModule = expandedModule == 2 ? -1 : 2;
					return true;
				}
				if (event.y() >= top + 81 && event.y() <= top + 102) {
					if (event.button() == 0) TeamEsp.toggle();
					else if (event.button() == 1) expandedModule = expandedModule == 3 ? -1 : 3;
					return true;
				}
				if (event.y() >= top + 108 && event.y() <= top + 129) {
					if (event.button() == 0) PowerupEsp.toggle();
					else if (event.button() == 1) expandedModule = expandedModule == 4 ? -1 : 4;
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
			int visibleHeight = contentBottom - (contentTop + 54);
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

package dev.luxnet.winter.client;

import dev.luxnet.winter.client.debug.MobDebugModule;
import dev.luxnet.winter.client.debug.ShotDebugModule;
import dev.luxnet.winter.client.visuals.BulletTracers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public class WinterScreen extends Screen {
	private static final int PANEL_WIDTH = 320;
	private static final int PANEL_HEIGHT = 270;
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
	private boolean tracerSettingsExpanded;
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
		graphics.drawString(font, "Right click for settings", contentX + 7, contentTop + 24, 0xFF9AA3AF, false);
		if (!tracerSettingsExpanded) return;

		int lifetimeY = contentTop + 45;
		graphics.drawString(font, "Lifetime", contentX + 2, lifetimeY, 0xFFEAF7FC, false);
		String lifetimeValue = String.format(Locale.ROOT, "%.2fs", BulletTracers.getLifetimeMs() / 1000.0);
		graphics.drawString(font, lifetimeValue, contentRight - font.width(lifetimeValue), lifetimeY, 0xFF9AA3AF, false);
		drawSlider(graphics, contentX, contentRight, contentTop + 61, lifetimeProgress());

		int thicknessY = contentTop + 82;
		graphics.drawString(font, "Thickness", contentX + 2, thicknessY, 0xFFEAF7FC, false);
		String thicknessValue = String.format(Locale.ROOT, "%.2f", BulletTracers.getThickness());
		graphics.drawString(font, thicknessValue, contentRight - font.width(thicknessValue), thicknessY, 0xFF9AA3AF, false);
		drawSlider(graphics, contentX, contentRight, contentTop + 98, thicknessProgress());
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
	private int contentLeft() { return panelX + SIDEBAR_WIDTH + 4 + CONTENT_PADDING; }
	private int contentRight() { return panelX + panelWidth - 12; }
	private int contentTop() { return panelY + HEADER_HEIGHT + 14; }
	private int lifetimeSliderY() { return contentTop() + 61; }
	private int thicknessSliderY() { return contentTop() + 98; }

	private void updateSlider(double mouseX) {
		double progress = Math.max(0.0, Math.min(1.0, (mouseX - contentLeft()) / (contentRight() - contentLeft())));
		if (activeSlider == 0) {
			int value = 200 + (int) Math.round(progress * 2800.0);
			BulletTracers.setLifetimeMs((int) Math.round(value / 50.0) * 50);
		} else if (activeSlider == 1) {
			BulletTracers.setThickness((float) (0.08 + progress * 0.56));
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
					else if (event.button() == 1) tracerSettingsExpanded = !tracerSettingsExpanded;
					return true;
				}
				if (tracerSettingsExpanded && event.button() == 0) {
					if (Math.abs(event.y() - lifetimeSliderY()) <= 6) activeSlider = 0;
					else if (Math.abs(event.y() - thicknessSliderY()) <= 6) activeSlider = 1;
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

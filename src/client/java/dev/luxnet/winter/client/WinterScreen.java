package dev.luxnet.winter.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import dev.luxnet.winter.client.debug.ShotDebugModule;
import dev.luxnet.winter.client.visuals.BulletTracers;

import java.util.Locale;

public class WinterScreen extends Screen {
	private static final int PANEL_WIDTH = 320;
	private static final int PANEL_HEIGHT = 230;
	private static final int SIDEBAR_WIDTH = 92;
	private static final int HEADER_HEIGHT = 58;
	private static final int CONTENT_PADDING = 14;
	private static final int EVENT_ROW_HEIGHT = 13;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int scrollOffset;
	private boolean tracerSettingsExpanded;
	private int activeSlider = -1;
	private Category selectedCategory = Category.DEBUG;

	private enum Category {
		DEBUG,
		VISUALS
	}

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

		// Panel and subtle edge
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
		int contentTop = panelY + HEADER_HEIGHT + 14;
		int contentBottom = panelY + panelHeight - 12;
		int contentRight = panelX + panelWidth - 12;
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
		graphics.drawString(font, "SHOT PATTERN", contentX, contentTop + 2, 0xFF82DDF6, false);

		boolean traceEnabled = ShotDebugModule.isEnabled();
		int buttonY = contentTop + 16;
		graphics.fill(contentX, buttonY, contentRight, buttonY + 20, traceEnabled ? 0xAA245A50 : 0xAA303640);
		graphics.drawString(font, "Capture: " + (traceEnabled ? "ON" : "OFF"), contentX + 7, buttonY + 6,
			traceEnabled ? 0xFF9FFFD7 : 0xFFD3D9E0, false);

		var events = ShotDebugModule.getEvents();
		int eventsTop = contentTop + 42;
		if (events.isEmpty()) {
			graphics.drawString(font, "Enable capture, then test", contentX, eventsTop - scrollOffset, 0xFF9AA3AF, false);
		} else {
			for (int i = 0; i < events.size(); i++) {
				int eventY = eventsTop + i * EVENT_ROW_HEIGHT - scrollOffset;
				if (eventY + EVENT_ROW_HEIGHT >= contentTop && eventY <= contentBottom) {
					String fullEvent = events.get(events.size() - 1 - i);
					String event = trimToWidth(fullEvent, contentRight - contentX - 6);
					graphics.drawString(font, event, contentX + 3, eventY, 0xFFD5DCE5, false);
					if (mouseX >= contentX && mouseX <= contentRight && mouseY >= eventY && mouseY < eventY + EVENT_ROW_HEIGHT) {
						graphics.setTooltipForNextFrame(Component.literal(fullEvent), mouseX, mouseY);
					}
				}
			}
		}
	}

	private void renderVisuals(GuiGraphics graphics, int contentX, int contentTop, int contentRight) {
		graphics.drawString(font, "VISUALS", contentX, contentTop + 2, 0xFF82DDF6, false);
		int buttonY = contentTop + 18;
		boolean enabled = BulletTracers.isEnabled();
		graphics.fill(contentX, buttonY, contentRight, buttonY + 22, enabled ? 0xAA245A50 : 0xAA303640);
		graphics.drawString(font, "BulletTracers  " + (enabled ? "ON" : "OFF"), contentX + 7, buttonY + 7,
			enabled ? 0xFF9FFFD7 : 0xFFD3D9E0, false);
		graphics.drawString(font, "RClick: module settings", contentX + 7, buttonY + 23, 0xFF9AA3AF, false);
		if (!tracerSettingsExpanded) return;

		int lifetimeLabelY = buttonY + 41;
		graphics.drawString(font, "Lifetime", contentX, lifetimeLabelY, 0xFFEAF7FC, false);
		String lifetimeValue = String.format(Locale.ROOT, "%.2fs", BulletTracers.getLifetimeMs() / 1000.0);
		graphics.drawString(font, lifetimeValue, contentRight - font.width(lifetimeValue), lifetimeLabelY, 0xFF9AA3AF, false);
		drawSlider(graphics, contentX, contentRight, buttonY + 56, lifetimeProgress());

		int thicknessLabelY = buttonY + 76;
		graphics.drawString(font, "Thickness", contentX, thicknessLabelY, 0xFFEAF7FC, false);
		String thicknessValue = String.format(Locale.ROOT, "%.2f", BulletTracers.getThickness());
		graphics.drawString(font, thicknessValue, contentRight - font.width(thicknessValue), thicknessLabelY, 0xFF9AA3AF, false);
		drawSlider(graphics, contentX, contentRight, buttonY + 91, thicknessProgress());
	}

	private void drawSlider(GuiGraphics graphics, int left, int right, int y, double progress) {
		graphics.fill(left, y, right, y + 4, 0xFF38414D);
		int knobX = left + (int) Math.round((right - left) * progress);
		graphics.fill(left, y, knobX, y + 4, 0xFF70D8F5);
		graphics.fill(knobX - 3, y - 3, knobX + 4, y + 7, 0xFFEAF7FC);
	}

	private double lifetimeProgress() {
		return (BulletTracers.getLifetimeMs() - 200) / 2800.0;
	}

	private double thicknessProgress() {
		return (BulletTracers.getThickness() - 0.08) / 0.56;
	}

	private int visualsModuleY() { return panelY + HEADER_HEIGHT + 14 + 18; }
	private int lifetimeSliderY() { return visualsModuleY() + 56; }
	private int thicknessSliderY() { return visualsModuleY() + 91; }

	private void updateSlider(double mouseX) {
		int left = panelX + SIDEBAR_WIDTH + 4 + CONTENT_PADDING;
		int right = panelX + panelWidth - 12;
		double progress = Math.max(0.0, Math.min(1.0, (mouseX - left) / (right - left)));
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
		while (!result.isEmpty() && font.width(result) > maxWidth) {
			result = result.substring(0, result.length() - 1);
		}
		return result.equals(text) ? result : result + "…";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int dividerX = panelX + SIDEBAR_WIDTH + 4;
		int contentX = dividerX + CONTENT_PADDING;
		int contentRight = panelX + panelWidth - 12;
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

		int contentTop = panelY + HEADER_HEIGHT + 14;
		if (event.x() >= contentX && event.x() <= contentRight) {
			if (selectedCategory == Category.DEBUG && event.button() == 0) {
				int buttonY = contentTop + 16;
				if (event.y() >= buttonY && event.y() <= buttonY + 20) {
					ShotDebugModule.toggle();
					return true;
				}
			} else if (selectedCategory == Category.VISUALS) {
				int buttonY = contentTop + 18;
				if (event.y() >= buttonY && event.y() <= buttonY + 22) {
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
		int contentLeft = panelX + SIDEBAR_WIDTH + CONTENT_PADDING + 5;
		int contentRight = panelX + panelWidth - 12;
		int contentTop = panelY + HEADER_HEIGHT + 10;
		int contentBottom = panelY + panelHeight - 12;
		if (mouseX >= contentLeft && mouseX <= contentRight && mouseY >= contentTop && mouseY <= contentBottom) {
			int visibleHeight = contentBottom - contentTop;
			int contentHeight = 42 + ShotDebugModule.getEvents().size() * EVENT_ROW_HEIGHT;
			int maxScroll = Math.max(0, contentHeight - visibleHeight);
			scrollOffset = Math.max(0, Math.min(maxScroll,
				scrollOffset - (int) Math.signum(verticalAmount) * 12));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

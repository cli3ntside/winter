package dev.luxnet.winter.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** In-game drag editor for the watermark and power-up HUDs. */
public final class HudEditorScreen extends Screen {
	private int dragging = -1;
	private int selected = -1;
	private boolean resizing;
	private final Screen parent;
	private double dragOffsetX;
	private double dragOffsetY;
	private double resizeStartX;
	private double resizeStartY;
	private double resizeStartScale;
	private double resizeStartMouseX;
	private double resizeStartMouseY;
	private int resizeStartWidth;
	private int resizeStartHeight;

	public HudEditorScreen(Screen parent) { super(Component.literal("Winter HUD Editor")); this.parent = parent; }

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, 0x99060A10);
		graphics.fill(12, 12, width - 12, 48, 0xE612151B);
		graphics.drawString(font, "HUD Editor", 22, 20, 0xFFEAF7FC, false);
		String help = "Drag to move  ·  drag corner to resize  ·  Esc to close";
		graphics.drawString(font, help, width - font.width(help) - 22, 20, 0xFF9AA3AF, false);
		for (int element = WinterHud.WATERMARK; element <= WinterHud.BOSS_WARNING; element++) {
			int x = WinterHud.getScreenX(element, width, font);
			int y = WinterHud.getEditorScreenY(element, height, font);
			int boxWidth = WinterHud.getWidth(element, font);
			int boxHeight = WinterHud.getHeight(element, font);
			graphics.fill(x - 3, y - 3, x + boxWidth + 3, y + boxHeight + 3,
				selected == element ? 0x8870D8F5 : 0x665E6672);
			WinterHud.renderEditorPreview(graphics, element, x, y);
			graphics.fill(x + boxWidth - 4, y + boxHeight - 4, x + boxWidth + 4, y + boxHeight + 4,
				selected == element ? 0xFF70D8F5 : 0xFF9AA3AF);
			if (selected == element) {
				String size = Math.round(WinterHud.getElementScale(element) * 100) + "%";
				graphics.drawString(font, size, x, Math.max(54, y - font.lineHeight - 5), 0xFFEAF7FC, true);
			}
		}
		super.render(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) return super.mouseClicked(event, doubleClick);
		for (int element = WinterHud.BOSS_WARNING; element >= WinterHud.WATERMARK; element--) {
			int x = WinterHud.getScreenX(element, width, font);
			int y = WinterHud.getEditorScreenY(element, height, font);
			int boxWidth = WinterHud.getWidth(element, font);
			int boxHeight = WinterHud.getHeight(element, font);
			if (event.x() >= x + boxWidth - 8 && event.x() <= x + boxWidth + 5
				&& event.y() >= y + boxHeight - 8 && event.y() <= y + boxHeight + 5) {
				dragging = element;
				selected = element;
				resizing = true;
				resizeStartX = x;
				resizeStartY = y;
				resizeStartScale = WinterHud.getElementScale(element);
				resizeStartMouseX = event.x();
				resizeStartMouseY = event.y();
				resizeStartWidth = Math.max(1, boxWidth);
				resizeStartHeight = Math.max(1, boxHeight);
				dragOffsetX = event.x() - x;
				dragOffsetY = event.y() - y;
				return true;
			}
			if (event.x() >= x - 4 && event.x() <= x + boxWidth + 4
				&& event.y() >= y - 4 && event.y() <= y + boxHeight + 4) {
				dragging = element;
				selected = element;
				resizing = false;
				dragOffsetX = event.x() - x;
				dragOffsetY = event.y() - y;
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging < 0) return super.mouseDragged(event, dragX, dragY);
		if (resizing) {
			double relativeDelta = Math.max((event.x() - resizeStartMouseX) / resizeStartWidth,
				(event.y() - resizeStartMouseY) / resizeStartHeight);
			WinterHud.setElementScale(dragging, resizeStartScale * (1.0 + relativeDelta));
			int newWidth = WinterHud.getWidth(dragging, font);
			int newHeight = WinterHud.getHeight(dragging, font);
			double x = Math.max(0, Math.min(width - newWidth, resizeStartX));
			double y = Math.max(0, Math.min(height - newHeight, resizeStartY));
			WinterHud.setPosition(dragging, x / Math.max(1, width - newWidth), y / Math.max(1, height - newHeight));
			return true;
		}
		int itemWidth = WinterHud.getWidth(dragging, font);
		int itemHeight = WinterHud.getHeight(dragging, font);
		double x = Math.max(0, Math.min(width - itemWidth, event.x() - dragOffsetX));
		double y = Math.max(0, Math.min(height - itemHeight, event.y() - dragOffsetY));
		WinterHud.setPosition(dragging, x / Math.max(1, width - itemWidth), y / Math.max(1, height - itemHeight));
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging >= 0) { dragging = -1; resizing = false; WinterHud.savePositions(); return true; }
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			WinterHud.savePositions();
			minecraft.setScreen(parent);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() { return false; }
}

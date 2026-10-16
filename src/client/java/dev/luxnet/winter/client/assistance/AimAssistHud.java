package dev.luxnet.winter.client.assistance;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Draws the configured aim cone as a thin screen-space circle. */
public final class AimAssistHud {
	private AimAssistHud() { }

	public static void register() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("winter", "aim_fov_circle"), AimAssistHud::render);
	}

	private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!AimAssist.isEnabled() || !AimAssist.isFovCircleEnabled() || minecraft.player == null
			|| minecraft.options.hideGui || minecraft.screen != null) return;

		double verticalFov = minecraft.options.fov().get();
		double radius = graphics.guiHeight() * 0.5
			* Math.tan(Math.toRadians(AimAssist.getFov()))
			/ Math.tan(Math.toRadians(verticalFov) * 0.5);
		double centerX = graphics.guiWidth() * 0.5;
		double centerY = graphics.guiHeight() * 0.5;
		int segments = Math.max(180, Math.min(4096, (int) Math.ceil(Math.PI * radius)));
		int color = 0xBFEAF7FC;
		double previousX = centerX + radius;
		double previousY = centerY;
		for (int i = 1; i <= segments; i++) {
			double angle = Math.PI * 2.0 * i / segments;
			double nextX = centerX + Math.cos(angle) * radius;
			double nextY = centerY + Math.sin(angle) * radius;
			int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(nextX - previousX), Math.abs(nextY - previousY))));
			for (int step = 0; step < steps; step++) {
				double t = step / (double) steps;
				int x = (int) Math.round(previousX + (nextX - previousX) * t);
				int y = (int) Math.round(previousY + (nextY - previousY) * t);
				graphics.fill(x, y, x + 1, y + 1, color);
			}
			previousX = nextX;
			previousY = nextY;
		}
	}
}

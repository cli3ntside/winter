package dev.luxnet.winter.client.visuals;

import dev.luxnet.winter.client.WinterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** Hides nearby player models while leaving the held item render layer visible. */
public final class PlayerVisibility {
	private static boolean enabled;
	private static int renderDistance = 16;

	private PlayerVisibility() { }

	public static boolean isEnabled() { return enabled; }
	public static void setEnabled(boolean value) { enabled = value; WinterConfig.save(); }
	public static void toggle() { setEnabled(!enabled); }
	public static int getRenderDistance() { return renderDistance; }
	public static void setRenderDistance(int value) {
		renderDistance = Math.max(4, Math.min(64, value));
		WinterConfig.save();
	}
	public static boolean shouldHide(AvatarRenderState state) {
		if (!enabled || state == null || state.distanceToCameraSq > (double) renderDistance * renderDistance) return false;
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player == null || state.id != minecraft.player.getId();
	}
}

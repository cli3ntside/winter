package dev.luxnet.winter.client;

import dev.luxnet.winter.client.debug.ShotDebugModule;
import dev.luxnet.winter.client.visuals.BulletTracers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class WinterClient implements ClientModInitializer {
	private static KeyMapping openMenuKey;

	@Override
	public void onInitializeClient() {
		BulletTracers.registerWorldRender();

		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.winter.open_menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_RIGHT_SHIFT,
			KeyMapping.Category.MISC
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null) {
				ShotDebugModule.recordSneakState(client.player.isShiftKeyDown());
			}
			while (openMenuKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new WinterScreen());
				}
			}
		});
	}
}

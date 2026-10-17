package dev.luxnet.winter.client.assistance;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luxnet.winter.client.WinterConfig;
import dev.luxnet.winter.client.mixin.KeyMappingAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

/** Queues discrete use-key presses while the physical right mouse button is held. */
public final class RightClicker {
	private static boolean enabled;
	private static int minimumCps = 8;
	private static int maximumCps = 12;
	private static long nextClickAt;
	private static boolean rightWasHeld;
	private static KeyMapping pendingRelease;

	private RightClicker() { }

	public static boolean isEnabled() { return enabled; }
	public static int getMinimumCps() { return minimumCps; }
	public static int getMaximumCps() { return maximumCps; }
	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		nextClickAt = 0L;
		rightWasHeld = false;
		if (!value) releasePending(Minecraft.getInstance());
		WinterConfig.save();
	}
	public static void toggle() { setEnabled(!enabled); }
	public static void setMinimumCps(int value) {
		minimumCps = Math.min(clampCps(value), maximumCps);
		WinterConfig.save();
	}
	public static void setMaximumCps(int value) {
		maximumCps = Math.max(clampCps(value), minimumCps);
		WinterConfig.save();
	}
	public static void loadCpsRange(int min, int max) {
		minimumCps = clampCps(Math.min(min, max));
		maximumCps = clampCps(Math.max(min, max));
	}

	public static void register() {
		WorldRenderEvents.END_MAIN.register(context -> update(Minecraft.getInstance()));
		ClientTickEvents.END_CLIENT_TICK.register(RightClicker::releaseIfUnavailable);
	}

	private static void update(Minecraft minecraft) {
		releasePending(minecraft);
		if (!enabled || minecraft.player == null || minecraft.level == null || minecraft.screen != null
			|| minecraft.getWindow() == null
			|| GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) {
			nextClickAt = 0L;
			rightWasHeld = false;
			return;
		}

		long now = System.nanoTime();
		if (!rightWasHeld) {
			rightWasHeld = true;
			nextClickAt = now + nextIntervalNanos();
			return;
		}
		if (now < nextClickAt) return;
		emulateClick(minecraft);
		nextClickAt = now + nextIntervalNanos();
	}

	private static void releaseIfUnavailable(Minecraft minecraft) {
		releasePending(minecraft);
		if (!enabled || minecraft.player == null || minecraft.level == null || minecraft.screen != null
			|| minecraft.getWindow() == null
			|| GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) != GLFW.GLFW_PRESS) {
			nextClickAt = 0L;
			rightWasHeld = false;
		}
	}

	private static long nextIntervalNanos() {
		int cps = ThreadLocalRandom.current().nextInt(minimumCps, maximumCps + 1);
		return 1_000_000_000L / cps;
	}

	private static void emulateClick(Minecraft minecraft) {
		KeyMapping use = minecraft.options.keyUse;
		InputConstants.Key key = ((KeyMappingAccessor) (Object) use).winter$getBoundKey();
		KeyMapping.click(key);
		KeyMapping.set(key, true);
		use.setDown(true);
		pendingRelease = use;
	}

	private static void releasePending(Minecraft minecraft) {
		if (pendingRelease == null) return;
		KeyMapping use = pendingRelease;
		InputConstants.Key key = ((KeyMappingAccessor) (Object) use).winter$getBoundKey();
		boolean physicallyDown = false;
		if (minecraft.getWindow() != null) {
			physicallyDown = key.getType() == InputConstants.Type.MOUSE
				? GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS
				: InputConstants.isKeyDown(minecraft.getWindow(), key.getValue());
		}
		KeyMapping.set(key, physicallyDown);
		use.setDown(physicallyDown);
		pendingRelease = null;
	}

	private static int clampCps(int value) { return Math.max(1, Math.min(20, value)); }
}

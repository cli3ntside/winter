package dev.luxnet.winter.client.assistance;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luxnet.winter.client.WinterConfig;
import dev.luxnet.winter.client.mixin.KeyMappingAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Repeatedly cycles between the configured hotbar slots using their normal key bindings. */
public final class QuickSwap {
	private static final int MOUSE_BIND_OFFSET = 1000;
	private static final int[] SLOTS_2_3 = {1, 2};
	private static final int[] SLOTS_2_3_4 = {1, 2, 3};
	private static boolean enabled;
	private static int delayMs = 250;
	private static int jitterMs;
	private static SlotMode slotMode = SlotMode.SLOTS_2_3;
	private static ActivationMode activationMode = ActivationMode.HOLD;
	private static int bindCode = GLFW.GLFW_KEY_UNKNOWN;
	private static boolean bindWasDown;
	private static boolean toggleActive;
	private static int nextSlotIndex;
	private static boolean slotSequenceStarted;
	private static long nextSwapAt;
	private static Object sequenceLevel;
	private static Object sequenceConnection;
	private static KeyMapping pendingRelease;
	private static boolean pendingWasDown;

	private QuickSwap() { }

	public static boolean isEnabled() { return enabled; }
	public static int getDelayMs() { return delayMs; }
	public static int getJitterMs() { return jitterMs; }
	public static SlotMode getSlotMode() { return slotMode; }
	public static ActivationMode getActivationMode() { return activationMode; }
	public static int getBindCode() { return bindCode; }
	public static boolean isMouseBind(int code) { return code >= MOUSE_BIND_OFFSET; }
	public static int getMouseButton(int code) { return code - MOUSE_BIND_OFFSET; }
	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		if (!value) {
			toggleActive = false;
			bindWasDown = false;
			cancel(Minecraft.getInstance());
		}
		else resetSequence(Minecraft.getInstance());
		WinterConfig.save();
	}
	public static void toggle() { setEnabled(!enabled); }
	public static void setDelayMs(int value) {
		delayMs = Math.max(0, Math.min(1000, value));
		WinterConfig.save();
	}
	public static void setJitterMs(int value) {
		jitterMs = Math.max(0, Math.min(10, value));
		WinterConfig.save();
	}
	public static void setBindCode(int value) {
		bindCode = value;
		bindWasDown = false;
		toggleActive = false;
		cancel(Minecraft.getInstance());
		WinterConfig.save();
	}
	public static void setActivationMode(ActivationMode value) {
		if (value == null || activationMode == value) return;
		activationMode = value;
		bindWasDown = false;
		toggleActive = false;
		cancel(Minecraft.getInstance());
		WinterConfig.save();
	}
	public static void cycleActivationMode() {
		setActivationMode(activationMode == ActivationMode.HOLD ? ActivationMode.TOGGLE : ActivationMode.HOLD);
	}
	public static void cycleSlotMode() {
		setSlotMode(slotMode == SlotMode.SLOTS_2_3 ? SlotMode.SLOTS_2_3_4 : SlotMode.SLOTS_2_3);
	}
	public static void setSlotMode(SlotMode value) {
		if (value == null || slotMode == value) return;
		slotMode = value;
		resetSequence(Minecraft.getInstance());
		WinterConfig.save();
	}
	public static void loadSettings(int delay, int jitter, String slots, String activation, int bind) {
		delayMs = Math.max(0, Math.min(1000, delay));
		jitterMs = Math.max(0, Math.min(10, jitter));
		bindCode = bind;
		try { slotMode = SlotMode.valueOf(slots); }
		catch (RuntimeException ignored) { slotMode = SlotMode.SLOTS_2_3; }
		try { activationMode = ActivationMode.valueOf(activation); }
		catch (RuntimeException ignored) { activationMode = ActivationMode.HOLD; }
	}

	public static void register() {
		WorldRenderEvents.END_MAIN.register(context -> update(Minecraft.getInstance()));
		ClientTickEvents.END_CLIENT_TICK.register(QuickSwap::cleanupIfUnavailable);
	}

	private static void update(Minecraft minecraft) {
		releasePendingKey();
		if (!enabled || minecraft.player == null || minecraft.level == null || minecraft.screen != null
			|| minecraft.getConnection() == null) {
			bindWasDown = isBindDown(minecraft);
			cancel(minecraft);
			return;
		}
		if (sequenceLevel != minecraft.level || sequenceConnection != minecraft.getConnection()) {
			resetSequence(minecraft);
			return;
		}
		boolean bindDown = isBindDown(minecraft);
		if (activationMode == ActivationMode.TOGGLE && bindDown && !bindWasDown) toggleActive = !toggleActive;
		bindWasDown = bindDown;
		boolean active = activationMode == ActivationMode.HOLD ? bindDown : toggleActive;
		if (!active) {
			slotSequenceStarted = false;
			nextSwapAt = 0L;
			return;
		}

		long now = System.nanoTime();
		int[] slots = slotsForMode();
		if (!slotSequenceStarted) {
			nextSlotIndex = findNextSlotIndex(slots, minecraft.player.getInventory().getSelectedSlot());
			slotSequenceStarted = true;
			nextSwapAt = now;
		}
		if (now < nextSwapAt) return;
		queueTap(minecraft.options.keyHotbarSlots[slots[nextSlotIndex]]);
		nextSlotIndex = (nextSlotIndex + 1) % slots.length;
		nextSwapAt = now + nextDelayNanos();
	}

	private static void cleanupIfUnavailable(Minecraft minecraft) {
		releasePendingKey();
		if (!enabled || minecraft.player == null || minecraft.level == null || minecraft.screen != null
			|| minecraft.getConnection() == null) {
			bindWasDown = isBindDown(minecraft);
			slotSequenceStarted = false;
			nextSwapAt = 0L;
		}
	}

	private static int[] slotsForMode() {
		return slotMode == SlotMode.SLOTS_2_3 ? SLOTS_2_3 : SLOTS_2_3_4;
	}

	private static int findNextSlotIndex(int[] slots, int selectedSlot) {
		for (int i = 0; i < slots.length; i++) {
			if (slots[i] == selectedSlot) return (i + 1) % slots.length;
		}
		return 0;
	}

	private static boolean isBindDown(Minecraft minecraft) {
		if (bindCode == GLFW.GLFW_KEY_UNKNOWN || minecraft.getWindow() == null) return false;
		if (isMouseBind(bindCode)) {
			return GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), getMouseButton(bindCode)) == GLFW.GLFW_PRESS;
		}
		return InputConstants.isKeyDown(minecraft.getWindow(), bindCode);
	}

	private static long nextDelayNanos() {
		int jitter = jitterMs == 0 ? 0 : 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(jitterMs);
		return (delayMs + jitter) * 1_000_000L;
	}

	private static void queueTap(KeyMapping mapping) {
		InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).winter$getBoundKey();
		pendingWasDown = mapping.isDown();
		KeyMapping.click(key);
		KeyMapping.set(key, true);
		mapping.setDown(true);
		pendingRelease = mapping;
	}

	private static void releasePendingKey() {
		if (pendingRelease == null) return;
		pendingRelease.setDown(pendingWasDown);
		KeyMapping.set(((KeyMappingAccessor) (Object) pendingRelease).winter$getBoundKey(), pendingWasDown);
		pendingRelease = null;
	}

	private static void cancel(Minecraft minecraft) {
		releasePendingKey();
		slotSequenceStarted = false;
		sequenceLevel = minecraft.level;
		sequenceConnection = minecraft.getConnection();
		nextSwapAt = 0L;
	}

	private static void resetSequence(Minecraft minecraft) {
		slotSequenceStarted = false;
		sequenceLevel = minecraft.level;
		sequenceConnection = minecraft.getConnection();
		nextSwapAt = System.nanoTime();
	}

	public enum SlotMode { SLOTS_2_3, SLOTS_2_3_4 }
	public enum ActivationMode { HOLD, TOGGLE }
}

package dev.luxnet.winter.client.assistance;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luxnet.winter.client.WinterConfig;
import dev.luxnet.winter.client.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Simulates the user's hotbar slot and use key presses for the skill action. */
public final class SkillBind {
	private static final int MOUSE_BIND_OFFSET = 1000;
	private static boolean enabled;
	private static int bindCode = GLFW.GLFW_KEY_UNKNOWN;
	private static boolean bindWasDown;
	private static Phase phase = Phase.IDLE;
	private static int previousSlot;
	private static int phaseTicks;
	private static Object sequenceLevel;
	private static Object sequenceConnection;
	private static KeyMapping pendingRelease;
	private static boolean pendingWasDown;

	private SkillBind() { }

	public static boolean isEnabled() { return enabled; }
	public static int getBindCode() { return bindCode; }
	public static boolean isMouseBind(int code) { return code >= MOUSE_BIND_OFFSET; }
	public static int getMouseButton(int code) { return code - MOUSE_BIND_OFFSET; }

	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		if (!value) {
			cancel(Minecraft.getInstance());
			bindWasDown = false;
		}
		WinterConfig.save();
	}
	public static void toggle() { setEnabled(!enabled); }
	public static void setBindCode(int code) {
		bindCode = code;
		WinterConfig.save();
	}

	public static void tick(Minecraft minecraft) {
		releasePendingKey();
		if (phase != Phase.IDLE && (minecraft.player == null || minecraft.level == null
			|| minecraft.level != sequenceLevel || minecraft.getConnection() != sequenceConnection)) {
			cancel(minecraft);
		}
		if (enabled && bindCode != GLFW.GLFW_KEY_UNKNOWN && minecraft.getWindow() != null) {
			boolean down = isBindDown(minecraft);
			if (down && !bindWasDown && minecraft.screen == null && minecraft.player != null && phase == Phase.IDLE) {
				begin(minecraft);
			}
			bindWasDown = down;
		}
		if (phase == Phase.IDLE) return;

		phaseTicks++;
		switch (phase) {
			case WAIT_FOR_SKILL_SLOT -> {
				if (minecraft.player.getInventory().getSelectedSlot() == 4) {
					queueTap(minecraft.options.keyUse);
					phase = Phase.WAIT_FOR_USE;
					phaseTicks = 0;
				} else if (phaseTicks >= 4) {
					cancel(minecraft);
				}
			}
			case WAIT_FOR_USE -> {
				queueTap(minecraft.options.keyHotbarSlots[previousSlot]);
				phase = Phase.WAIT_FOR_RETURN;
				phaseTicks = 0;
			}
			case WAIT_FOR_RETURN -> {
				if (minecraft.player.getInventory().getSelectedSlot() == previousSlot || phaseTicks >= 4) finish();
			}
			case IDLE -> { }
		}
	}

	private static boolean isBindDown(Minecraft minecraft) {
		if (isMouseBind(bindCode)) return GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), getMouseButton(bindCode)) == GLFW.GLFW_PRESS;
		return InputConstants.isKeyDown(minecraft.getWindow(), bindCode);
	}

	private static void begin(Minecraft minecraft) {
		previousSlot = minecraft.player.getInventory().getSelectedSlot();
		sequenceLevel = minecraft.level;
		sequenceConnection = minecraft.getConnection();
		phase = Phase.WAIT_FOR_SKILL_SLOT;
		phaseTicks = 0;
		queueTap(minecraft.options.keyHotbarSlots[4]);
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
		if (phase != Phase.IDLE && minecraft.player != null && minecraft.level == sequenceLevel
			&& minecraft.getConnection() == sequenceConnection
			&& minecraft.player.getInventory().getSelectedSlot() != previousSlot) {
			queueTap(minecraft.options.keyHotbarSlots[previousSlot]);
		}
		finish();
	}

	private static void finish() {
		phase = Phase.IDLE;
		phaseTicks = 0;
		sequenceLevel = null;
		sequenceConnection = null;
	}

	private enum Phase { IDLE, WAIT_FOR_SKILL_SLOT, WAIT_FOR_USE, WAIT_FOR_RETURN }
}

package dev.luxnet.winter.client.assistance;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luxnet.winter.client.WinterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/** A local-only detached camera; it does not move the player or send movement packets. */
public final class Freecam {
	private static double speed = 0.80;
	private static final double FAST_SPEED_MULTIPLIER = 2.5;
	private static boolean enabled;
	private static boolean initialized;
	private static int bindKey = GLFW.GLFW_KEY_UNKNOWN;
	private static boolean bindWasDown;
	private static double x, y, z;
	private static double previousX, previousY, previousZ;
	private static float yaw, pitch;
	private static float previousYaw, previousPitch;
	private static ClientLevel activeLevel;
	private static ClientPacketListener activeConnection;

	private Freecam() { }

	public static boolean isEnabled() { return enabled; }
	public static double getSpeed() { return speed; }
	public static void setSpeed(double value) {
		speed = Math.max(0.1, Math.min(2.0, value));
		WinterConfig.save();
	}
	public static int getBindKey() { return bindKey; }
	public static void setBindKey(int key) {
		bindKey = key;
		WinterConfig.save();
	}

	public static void toggle() { setEnabled(!enabled); }
	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (value && minecraft.player != null && minecraft.gameRenderer != null) {
			Vec3 position = minecraft.gameRenderer.getMainCamera().position();
			x = previousX = position.x;
			y = previousY = position.y;
			z = previousZ = position.z;
			yaw = previousYaw = minecraft.player.getYRot();
			pitch = previousPitch = minecraft.player.getXRot();
			initialized = true;
			activeLevel = minecraft.level;
			activeConnection = minecraft.getConnection();
		}
		if (!value) {
			initialized = false;
			activeLevel = null;
			activeConnection = null;
		}
		enabled = value;
		WinterConfig.save();
	}

	public static void tick(Minecraft minecraft) {
		if (bindKey != GLFW.GLFW_KEY_UNKNOWN && minecraft.getWindow() != null) {
			boolean down = InputConstants.isKeyDown(minecraft.getWindow(), bindKey);
			if (down && !bindWasDown && minecraft.screen == null) toggle();
			bindWasDown = down;
		}
		if (!enabled) return;
		if (minecraft.player == null || minecraft.level == null
			|| (initialized && (minecraft.level != activeLevel || minecraft.getConnection() != activeConnection))) {
			setEnabled(false);
			return;
		}
		if (!initialized && minecraft.gameRenderer != null) {
			Vec3 position = minecraft.gameRenderer.getMainCamera().position();
			x = previousX = position.x; y = previousY = position.y; z = previousZ = position.z;
			yaw = previousYaw = minecraft.player.getYRot();
			pitch = previousPitch = minecraft.player.getXRot();
			initialized = true;
			activeLevel = minecraft.level;
			activeConnection = minecraft.getConnection();
		}

		previousX = x; previousY = y; previousZ = z;
		previousYaw = yaw; previousPitch = pitch;
		if (minecraft.screen != null) return;

		double yawRadians = Math.toRadians(yaw);
		double forwardX = -Math.sin(yawRadians), forwardZ = Math.cos(yawRadians);
		double rightX = -Math.cos(yawRadians), rightZ = -Math.sin(yawRadians);
		double movementSpeed = speed * (minecraft.options.keySprint.isDown() ? FAST_SPEED_MULTIPLIER : 1.0);
		double dx = 0, dy = 0, dz = 0;
		if (minecraft.options.keyUp.isDown()) { dx += forwardX; dz += forwardZ; }
		if (minecraft.options.keyDown.isDown()) { dx -= forwardX; dz -= forwardZ; }
		if (minecraft.options.keyRight.isDown()) { dx += rightX; dz += rightZ; }
		if (minecraft.options.keyLeft.isDown()) { dx -= rightX; dz -= rightZ; }
		if (minecraft.options.keyJump.isDown()) dy += 1;
		if (minecraft.options.keyShift.isDown()) dy -= 1;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length > 0) {
			x += dx / length * movementSpeed;
			y += dy / length * movementSpeed;
			z += dz / length * movementSpeed;
		}
	}

	public static void changeLookDirection(double deltaYaw, double deltaPitch) {
		previousYaw = yaw;
		previousPitch = pitch;
		yaw += (float) (deltaYaw * 0.15);
		pitch = Math.max(-90, Math.min(90, pitch + (float) (deltaPitch * 0.15)));
	}

	public static double x(float delta) { return previousX + (x - previousX) * delta; }
	public static double y(float delta) { return previousY + (y - previousY) * delta; }
	public static double z(float delta) { return previousZ + (z - previousZ) * delta; }
	public static float yaw(float delta) { return previousYaw + (yaw - previousYaw) * delta; }
	public static float pitch(float delta) { return previousPitch + (pitch - previousPitch) * delta; }
}

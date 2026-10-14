package dev.luxnet.winter.client;

import dev.luxnet.winter.client.debug.MobDebugModule;
import dev.luxnet.winter.client.debug.ShotDebugModule;
import dev.luxnet.winter.client.assistance.AutoRevive;
import dev.luxnet.winter.client.assistance.Freecam;
import dev.luxnet.winter.client.visuals.BulletTracers;
import dev.luxnet.winter.client.visuals.BrokenWindowEsp;
import dev.luxnet.winter.client.visuals.LuckyChestEsp;
import dev.luxnet.winter.client.visuals.MobEsp;
import dev.luxnet.winter.client.visuals.PowerupEsp;
import dev.luxnet.winter.client.visuals.TeamEsp;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persists module toggles and settings in the game directory. */
public final class WinterConfig {
	private static final Properties VALUES = new Properties();
	private static Path configFile;
	private static boolean loaded;
	private static boolean applying;

	private WinterConfig() { }

	public static synchronized void load(Minecraft minecraft) {
		configFile = minecraft.gameDirectory.toPath().resolve("winter").resolve("config.properties");
		applying = true;
		try {
			if (Files.isRegularFile(configFile)) {
				try (InputStream input = Files.newInputStream(configFile)) {
					VALUES.load(input);
				}
			}
			ShotDebugModule.setEnabled(booleanValue("debug.capture", false));
			MobDebugModule.setEnabled(booleanValue("debug.detectMobs", false));
			BulletTracers.setEnabled(booleanValue("visuals.bulletTracers", false));
			BulletTracers.setLifetimeMs(intValue("visuals.bulletTracers.lifetimeMs", 1_000));
			BulletTracers.setThickness(floatValue("visuals.bulletTracers.thickness", 0.08F));
			LuckyChestEsp.setEnabled(booleanValue("visuals.luckyChestEsp", false));
			LuckyChestEsp.setRenderDistance(intValue("visuals.luckyChestEsp.range", 64));
			MobEsp.setEnabled(booleanValue("visuals.mobEsp", false));
			MobEsp.setRenderDistance(intValue("visuals.mobEsp.range", 64));
			TeamEsp.setEnabled(booleanValue("visuals.teamEsp", false));
			TeamEsp.setRenderDistance(intValue("visuals.teamEsp.range", 64));
			PowerupEsp.setEnabled(booleanValue("visuals.powerupEsp", false));
			PowerupEsp.setRenderDistance(intValue("visuals.powerupEsp.range", 64));
			BrokenWindowEsp.setEnabled(booleanValue("visuals.brokenWindowEsp", false));
			BrokenWindowEsp.setSelectedMap(VALUES.getProperty("visuals.brokenWindowEsp.map", "deadend"));
			WinterHud.setWatermarkEnabled(booleanValue("hud.watermark", true));
			AutoRevive.setEnabled(booleanValue("assistance.autoRevive", false));
			Freecam.setBindKey(intValue("assistance.freecam.bind", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN));
			double freecamSpeed = doubleValue("assistance.freecam.speed", 0.80);
			if (Math.abs(freecamSpeed - 0.45) < 0.000001) freecamSpeed = 0.80;
			Freecam.setSpeed(freecamSpeed);
			Freecam.setEnabled(false);
			WinterScreen.loadMenuState(VALUES.getProperty("menu.category", "ASSISTANCE"),
				intValue("menu.expandedModule", -1));
		} catch (IOException exception) {
			System.err.println("[Winter] Could not load config: " + exception.getMessage());
		} finally {
			applying = false;
			loaded = true;
		}
		save();
	}

	public static synchronized void save() {
		if (!loaded || applying || configFile == null) return;
		VALUES.setProperty("debug.capture", Boolean.toString(ShotDebugModule.isEnabled()));
		VALUES.setProperty("debug.detectMobs", Boolean.toString(MobDebugModule.isEnabled()));
		VALUES.setProperty("visuals.bulletTracers", Boolean.toString(BulletTracers.isEnabled()));
		VALUES.setProperty("visuals.bulletTracers.lifetimeMs", Integer.toString(BulletTracers.getLifetimeMs()));
		VALUES.setProperty("visuals.bulletTracers.thickness", Float.toString(BulletTracers.getThickness()));
		VALUES.setProperty("visuals.luckyChestEsp", Boolean.toString(LuckyChestEsp.isEnabled()));
		VALUES.setProperty("visuals.luckyChestEsp.range", Integer.toString(LuckyChestEsp.getRenderDistance()));
		VALUES.setProperty("visuals.mobEsp", Boolean.toString(MobEsp.isEnabled()));
		VALUES.setProperty("visuals.mobEsp.range", Integer.toString(MobEsp.getRenderDistance()));
		VALUES.setProperty("visuals.teamEsp", Boolean.toString(TeamEsp.isEnabled()));
		VALUES.setProperty("visuals.teamEsp.range", Integer.toString(TeamEsp.getRenderDistance()));
		VALUES.setProperty("visuals.powerupEsp", Boolean.toString(PowerupEsp.isEnabled()));
		VALUES.setProperty("visuals.powerupEsp.range", Integer.toString(PowerupEsp.getRenderDistance()));
		VALUES.setProperty("visuals.brokenWindowEsp", Boolean.toString(BrokenWindowEsp.isEnabled()));
		VALUES.setProperty("visuals.brokenWindowEsp.map", BrokenWindowEsp.getSelectedMapId());
		VALUES.setProperty("hud.watermark", Boolean.toString(WinterHud.isWatermarkEnabled()));
		VALUES.setProperty("assistance.autoRevive", Boolean.toString(AutoRevive.isEnabled()));
		VALUES.setProperty("assistance.freecam.bind", Integer.toString(Freecam.getBindKey()));
		VALUES.setProperty("assistance.freecam.speed", Double.toString(Freecam.getSpeed()));
		VALUES.setProperty("menu.category", WinterScreen.getSelectedCategoryName());
		VALUES.setProperty("menu.expandedModule", Integer.toString(WinterScreen.getExpandedModule()));
		try {
			Files.createDirectories(configFile.getParent());
			try (OutputStream output = Files.newOutputStream(configFile)) {
				VALUES.store(output, "Winter client configuration");
			}
		} catch (IOException exception) {
			System.err.println("[Winter] Could not save config: " + exception.getMessage());
		}
	}

	private static boolean booleanValue(String key, boolean fallback) {
		String value = VALUES.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value);
	}

	private static int intValue(String key, int fallback) {
		try { return Integer.parseInt(VALUES.getProperty(key)); }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static float floatValue(String key, float fallback) {
		try { return Float.parseFloat(VALUES.getProperty(key)); }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static double doubleValue(String key, double fallback) {
		try { return Double.parseDouble(VALUES.getProperty(key)); }
		catch (RuntimeException ignored) { return fallback; }
	}
}

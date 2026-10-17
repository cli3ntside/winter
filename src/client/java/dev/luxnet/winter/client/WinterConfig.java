package dev.luxnet.winter.client;

import dev.luxnet.winter.client.debug.MobDebugModule;
import dev.luxnet.winter.client.debug.ShotDebugModule;
import dev.luxnet.winter.client.debug.DrawPredict;
import dev.luxnet.winter.client.assistance.AutoRevive;
import dev.luxnet.winter.client.assistance.Freecam;
import dev.luxnet.winter.client.assistance.SkillBind;
import dev.luxnet.winter.client.assistance.AimAssist;
import dev.luxnet.winter.client.assistance.Triggerbot;
import dev.luxnet.winter.client.assistance.RightClicker;
import dev.luxnet.winter.client.visuals.BulletTracers;
import dev.luxnet.winter.client.visuals.BrokenWindowEsp;
import dev.luxnet.winter.client.visuals.LuckyChestEsp;
import dev.luxnet.winter.client.visuals.MobEsp;
import dev.luxnet.winter.client.visuals.PowerupEsp;
import dev.luxnet.winter.client.visuals.TeamEsp;
import dev.luxnet.winter.client.visuals.PlayerVisibility;
import dev.luxnet.winter.client.visuals.ChatFilter;
import dev.luxnet.winter.client.visuals.WallbangTransparency;
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
			DrawPredict.setEnabled(booleanValue("debug.drawPredict", false));
			BulletTracers.setEnabled(booleanValue("visuals.bulletTracers", false));
			BulletTracers.setLifetimeMs(intValue("visuals.bulletTracers.lifetimeMs", 1_000));
			BulletTracers.setThickness(floatValue("visuals.bulletTracers.thickness", 0.08F));
			LuckyChestEsp.setEnabled(booleanValue("visuals.luckyChestEsp", false));
			LuckyChestEsp.setRenderDistance(intValue("visuals.luckyChestEsp.range", 64));
			MobEsp.setEnabled(booleanValue("visuals.mobEsp", false));
			MobEsp.setRenderDistance(intValue("visuals.mobEsp.range", 64));
			MobEsp.setDamageBlinkEnabled(booleanValue("visuals.mobEsp.damageBlink", true));
			MobEsp.loadHealthNametagMode(VALUES.getProperty("visuals.mobEsp.healthNametagMode", "NONE"));
			MobEsp.setBossNametagDistance(intValue("visuals.mobEsp.bossNametagDistance", 64));
			MobEsp.setNormalNametagDistance(intValue("visuals.mobEsp.normalNametagDistance", 32));
			TeamEsp.setEnabled(booleanValue("visuals.teamEsp", false));
			TeamEsp.setRenderDistance(intValue("visuals.teamEsp.range", 64));
			PowerupEsp.setEnabled(booleanValue("visuals.powerupEsp", false));
			PowerupEsp.setRenderDistance(intValue("visuals.powerupEsp.range", 64));
			BrokenWindowEsp.setEnabled(booleanValue("visuals.brokenWindowEsp", false));
			BrokenWindowEsp.setSelectedMap(VALUES.getProperty("visuals.brokenWindowEsp.map", "deadend"));
			PlayerVisibility.setEnabled(booleanValue("visuals.playerVisibility", false));
			PlayerVisibility.setRenderDistance(intValue("visuals.playerVisibility.range", 16));
			ChatFilter.setEnabled(booleanValue("visuals.chatFilter", false));
			WallbangTransparency.setEnabled(booleanValue("visuals.wallbangTransparency", false));
			for (ChatFilter.Filter filter : ChatFilter.Filter.values()) {
				ChatFilter.setFilterEnabled(filter, booleanValue("visuals.chatFilter." + filter.key(), filter.defaultValue()));
			}
			WinterHud.setWatermarkEnabled(booleanValue("hud.watermark", true));
			WinterHud.setScoreboardEnabled(booleanValue("hud.scoreboard.enabled", true));
			WinterHud.setBossbarEnabled(booleanValue("hud.bossbar.enabled", true));
			PowerupTracker.setEnabled(booleanValue("hud.powerupTracker.enabled", false));
			PowerupTracker.setTimerEnabled(booleanValue("hud.powerupTracker.timer", false));
			PowerupTracker.setPredictorEnabled(booleanValue("hud.powerupTracker.predictor", false));
			WinterHud.loadPosition(WinterHud.WATERMARK, doubleValue("hud.watermark.x", 0.0), doubleValue("hud.watermark.y", 0.0));
			WinterHud.loadPosition(WinterHud.POWERUP_TIMER, doubleValue("hud.powerupTimer.x", 0.0), doubleValue("hud.powerupTimer.y", 0.075));
			WinterHud.loadPosition(WinterHud.POWERUP_PREDICTOR, doubleValue("hud.powerupPredictor.x", 0.0), doubleValue("hud.powerupPredictor.y", 0.155));
			WinterHud.loadPosition(WinterHud.SCOREBOARD, doubleValue("hud.scoreboard.x", 1.0), doubleValue("hud.scoreboard.y", 0.5),
				booleanValue("hud.scoreboard.positioned", false));
			WinterHud.loadPosition(WinterHud.BOSSBAR, doubleValue("hud.bossbar.x", 0.5), doubleValue("hud.bossbar.y", 0.02),
				booleanValue("hud.bossbar.positioned", false));
			WinterHud.loadElementScale(WinterHud.WATERMARK, doubleValue("hud.watermark.scale", 1.0));
			WinterHud.loadElementScale(WinterHud.POWERUP_TIMER, doubleValue("hud.powerupTimer.scale", 1.0));
			WinterHud.loadElementScale(WinterHud.POWERUP_PREDICTOR, doubleValue("hud.powerupPredictor.scale", 1.0));
			WinterHud.loadElementScale(WinterHud.SCOREBOARD, doubleValue("hud.scoreboard.scale", 1.0));
			WinterHud.loadElementScale(WinterHud.BOSSBAR, doubleValue("hud.bossbar.scale", 1.0));
			AutoRevive.setEnabled(booleanValue("assistance.autoRevive", false));
			AimAssist.setBindCode(intValue("assistance.aimAssist.bind", 1001));
			AimAssist.setFov(intValue("assistance.aimAssist.fov", 30));
			AimAssist.setFovCircleEnabled(booleanValue("assistance.aimAssist.fovCircle", false));
			AimAssist.setEnabled(booleanValue("assistance.aimAssist", false));
			Triggerbot.setEnabled(booleanValue("assistance.triggerbot", false));
			Triggerbot.setOnKeyMode(booleanValue("assistance.triggerbot.onKey", false));
			Triggerbot.setThroughWalls(booleanValue("assistance.triggerbot.throughWalls", false));
			Triggerbot.setActivationBind(intValue("assistance.triggerbot.bind", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN));
			Triggerbot.setDelayMs(intValue("assistance.triggerbot.delayMs", 0));
			RightClicker.loadCpsRange(intValue("assistance.rightClicker.minCps", 8), intValue("assistance.rightClicker.maxCps", 12));
			RightClicker.setEnabled(booleanValue("assistance.rightClicker", false));
			Freecam.setBindKey(intValue("assistance.freecam.bind", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN));
			double freecamSpeed = doubleValue("assistance.freecam.speed", 0.80);
			if (Math.abs(freecamSpeed - 0.45) < 0.000001) freecamSpeed = 0.80;
			Freecam.setSpeed(freecamSpeed);
			Freecam.setEnabled(false);
			SkillBind.setBindCode(intValue("assistance.skillBind.bind", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN));
			SkillBind.setEnabled(booleanValue("assistance.skillBind.enabled", false));
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
		VALUES.setProperty("debug.drawPredict", Boolean.toString(DrawPredict.isEnabled()));
		VALUES.setProperty("visuals.bulletTracers", Boolean.toString(BulletTracers.isEnabled()));
		VALUES.setProperty("visuals.bulletTracers.lifetimeMs", Integer.toString(BulletTracers.getLifetimeMs()));
		VALUES.setProperty("visuals.bulletTracers.thickness", Float.toString(BulletTracers.getThickness()));
		VALUES.setProperty("visuals.luckyChestEsp", Boolean.toString(LuckyChestEsp.isEnabled()));
		VALUES.setProperty("visuals.luckyChestEsp.range", Integer.toString(LuckyChestEsp.getRenderDistance()));
		VALUES.setProperty("visuals.mobEsp", Boolean.toString(MobEsp.isEnabled()));
		VALUES.setProperty("visuals.mobEsp.range", Integer.toString(MobEsp.getRenderDistance()));
		VALUES.setProperty("visuals.mobEsp.damageBlink", Boolean.toString(MobEsp.isDamageBlinkEnabled()));
		VALUES.setProperty("visuals.mobEsp.healthNametagMode", MobEsp.getHealthNametagMode().name());
		VALUES.setProperty("visuals.mobEsp.bossNametagDistance", Integer.toString(MobEsp.getBossNametagDistance()));
		VALUES.setProperty("visuals.mobEsp.normalNametagDistance", Integer.toString(MobEsp.getNormalNametagDistance()));
		VALUES.setProperty("visuals.teamEsp", Boolean.toString(TeamEsp.isEnabled()));
		VALUES.setProperty("visuals.teamEsp.range", Integer.toString(TeamEsp.getRenderDistance()));
		VALUES.setProperty("visuals.powerupEsp", Boolean.toString(PowerupEsp.isEnabled()));
		VALUES.setProperty("visuals.powerupEsp.range", Integer.toString(PowerupEsp.getRenderDistance()));
		VALUES.setProperty("visuals.brokenWindowEsp", Boolean.toString(BrokenWindowEsp.isEnabled()));
		VALUES.setProperty("visuals.brokenWindowEsp.map", BrokenWindowEsp.getSelectedMapId());
		VALUES.setProperty("visuals.playerVisibility", Boolean.toString(PlayerVisibility.isEnabled()));
		VALUES.setProperty("visuals.playerVisibility.range", Integer.toString(PlayerVisibility.getRenderDistance()));
		VALUES.setProperty("visuals.chatFilter", Boolean.toString(ChatFilter.isEnabled()));
		VALUES.setProperty("visuals.wallbangTransparency", Boolean.toString(WallbangTransparency.isEnabled()));
		for (ChatFilter.Filter filter : ChatFilter.Filter.values()) {
			VALUES.setProperty("visuals.chatFilter." + filter.key(), Boolean.toString(ChatFilter.isFilterEnabled(filter)));
		}
		VALUES.setProperty("hud.watermark", Boolean.toString(WinterHud.isWatermarkEnabled()));
		VALUES.setProperty("hud.scoreboard.enabled", Boolean.toString(WinterHud.isScoreboardEnabled()));
		VALUES.setProperty("hud.bossbar.enabled", Boolean.toString(WinterHud.isBossbarEnabled()));
		VALUES.setProperty("hud.powerupTracker.enabled", Boolean.toString(PowerupTracker.isEnabled()));
		VALUES.setProperty("hud.powerupTracker.timer", Boolean.toString(PowerupTracker.isTimerEnabled()));
		VALUES.setProperty("hud.powerupTracker.predictor", Boolean.toString(PowerupTracker.isPredictorEnabled()));
		VALUES.setProperty("hud.watermark.x", Double.toString(WinterHud.getX(WinterHud.WATERMARK)));
		VALUES.setProperty("hud.watermark.y", Double.toString(WinterHud.getY(WinterHud.WATERMARK)));
		VALUES.setProperty("hud.powerupTimer.x", Double.toString(WinterHud.getX(WinterHud.POWERUP_TIMER)));
		VALUES.setProperty("hud.powerupTimer.y", Double.toString(WinterHud.getY(WinterHud.POWERUP_TIMER)));
		VALUES.setProperty("hud.powerupPredictor.x", Double.toString(WinterHud.getX(WinterHud.POWERUP_PREDICTOR)));
		VALUES.setProperty("hud.powerupPredictor.y", Double.toString(WinterHud.getY(WinterHud.POWERUP_PREDICTOR)));
		VALUES.setProperty("hud.scoreboard.x", Double.toString(WinterHud.getX(WinterHud.SCOREBOARD)));
		VALUES.setProperty("hud.scoreboard.y", Double.toString(WinterHud.getY(WinterHud.SCOREBOARD)));
		VALUES.setProperty("hud.scoreboard.positioned", Boolean.toString(WinterHud.hasCustomPosition(WinterHud.SCOREBOARD)));
		VALUES.setProperty("hud.bossbar.x", Double.toString(WinterHud.getX(WinterHud.BOSSBAR)));
		VALUES.setProperty("hud.bossbar.y", Double.toString(WinterHud.getY(WinterHud.BOSSBAR)));
		VALUES.setProperty("hud.bossbar.positioned", Boolean.toString(WinterHud.hasCustomPosition(WinterHud.BOSSBAR)));
		VALUES.setProperty("hud.watermark.scale", Double.toString(WinterHud.getElementScale(WinterHud.WATERMARK)));
		VALUES.setProperty("hud.powerupTimer.scale", Double.toString(WinterHud.getElementScale(WinterHud.POWERUP_TIMER)));
		VALUES.setProperty("hud.powerupPredictor.scale", Double.toString(WinterHud.getElementScale(WinterHud.POWERUP_PREDICTOR)));
		VALUES.setProperty("hud.scoreboard.scale", Double.toString(WinterHud.getElementScale(WinterHud.SCOREBOARD)));
		VALUES.setProperty("hud.bossbar.scale", Double.toString(WinterHud.getElementScale(WinterHud.BOSSBAR)));
		VALUES.setProperty("assistance.autoRevive", Boolean.toString(AutoRevive.isEnabled()));
		VALUES.setProperty("assistance.aimAssist", Boolean.toString(AimAssist.isEnabled()));
		VALUES.setProperty("assistance.triggerbot", Boolean.toString(Triggerbot.isEnabled()));
		VALUES.setProperty("assistance.triggerbot.onKey", Boolean.toString(Triggerbot.isOnKeyMode()));
		VALUES.setProperty("assistance.triggerbot.throughWalls", Boolean.toString(Triggerbot.isThroughWalls()));
		VALUES.setProperty("assistance.triggerbot.bind", Integer.toString(Triggerbot.getActivationBind()));
		VALUES.setProperty("assistance.triggerbot.delayMs", Integer.toString(Triggerbot.getDelayMs()));
		VALUES.setProperty("assistance.rightClicker", Boolean.toString(RightClicker.isEnabled()));
		VALUES.setProperty("assistance.rightClicker.minCps", Integer.toString(RightClicker.getMinimumCps()));
		VALUES.setProperty("assistance.rightClicker.maxCps", Integer.toString(RightClicker.getMaximumCps()));
		VALUES.setProperty("assistance.aimAssist.bind", Integer.toString(AimAssist.getBindCode()));
		VALUES.setProperty("assistance.aimAssist.fov", Integer.toString(AimAssist.getFov()));
		VALUES.setProperty("assistance.aimAssist.fovCircle", Boolean.toString(AimAssist.isFovCircleEnabled()));
		VALUES.setProperty("assistance.freecam.bind", Integer.toString(Freecam.getBindKey()));
		VALUES.setProperty("assistance.freecam.speed", Double.toString(Freecam.getSpeed()));
		VALUES.setProperty("assistance.skillBind.bind", Integer.toString(SkillBind.getBindCode()));
		VALUES.setProperty("assistance.skillBind.enabled", Boolean.toString(SkillBind.isEnabled()));
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

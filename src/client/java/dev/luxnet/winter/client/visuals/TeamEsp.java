package dev.luxnet.winter.client.visuals;

import dev.luxnet.winter.client.WinterConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Outlines active player entities other than the local player. */
public final class TeamEsp {
	private static final int REFRESH_TICKS = 10;
	private static final double MAX_RANGE = 128.0;
	private static final int TEAM_COLOR = 0xB8FF4545;
	private static final List<Player> TARGETS = new ArrayList<>();
	private static boolean enabled;
	private static int renderDistance = 64;
	private static int ticksSinceScan;
	private static Object scannedLevel;

	private TeamEsp() { }

	public static void registerWorldRender() {
		WorldRenderEvents.END_MAIN.register(TeamEsp::render);
	}

	public static boolean isEnabled() { return enabled; }
	public static int getRenderDistance() { return renderDistance; }
	public static void setRenderDistance(int value) {
		int updated = Math.max(16, Math.min(128, value));
		if (renderDistance != updated) { renderDistance = updated; WinterConfig.save(); }
	}
	public static void toggle() { setEnabled(!enabled); }
	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		if (!enabled) TARGETS.clear();
		WinterConfig.save();
	}

	public static void tick(Minecraft minecraft) {
		if (!enabled || minecraft.level == null || minecraft.player == null) {
			TARGETS.clear();
			scannedLevel = null;
			return;
		}
		if (scannedLevel != minecraft.level || ++ticksSinceScan >= REFRESH_TICKS) {
			scannedLevel = minecraft.level;
			ticksSinceScan = 0;
			scan(minecraft);
		}
	}

	private static void scan(Minecraft minecraft) {
		TARGETS.clear();
		if (!isZombiesGame(minecraft)) return;
		Vec3 center = minecraft.player.position();
		double distance = Math.min(MAX_RANGE, renderDistance);
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof Player player) || player == minecraft.player || !isActivePlayer(player)) continue;
			if (player.position().distanceToSqr(center) <= distance * distance) TARGETS.add(player);
		}
	}

	private static boolean isZombiesGame(Minecraft minecraft) {
		var scoreboard = minecraft.level.getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) return false;
		String title = plainText(sidebar.getDisplayName().getString()).toLowerCase(Locale.ROOT);
		if (title.contains("zombies") || title.contains("僵尸末日") || title.contains("殭屍末日")) return true;
		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
			String row = plainText(entry.owner()).toLowerCase(Locale.ROOT);
			if (row.contains("zombies left") || row.contains("僵尸") || row.contains("殭屍")) return true;
		}
		return false;
	}

	private static String plainText(String text) {
		return text.replaceAll("§.", "").replaceAll("[^\\p{L}\\p{N}_\\[\\] ]", "").trim();
	}

	private static boolean isActivePlayer(Player player) {
		if (player.isRemoved() || player.isSpectator() || player.getPose() == Pose.SLEEPING
			|| !player.isAlive() || player.getHealth() <= 0.0F) return false;
		var profile = player.getGameProfile();
		if (profile == null) return false;
		String name = profile.name();
		return name != null && name.matches("[A-Za-z0-9_]{1,16}");
	}

	private static void render(WorldRenderContext context) {
		if (!enabled || TARGETS.isEmpty()) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) return;
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		double distance = Math.min(MAX_RANGE, renderDistance);
		for (Player player : TARGETS) {
			if (player.level() != minecraft.level || !isActivePlayer(player)
				|| player.position().distanceToSqr(camera) > distance * distance) continue;
			EspBoxRenderer.draw(context, player.getBoundingBox(), TEAM_COLOR, 2.0F);
		}
	}
}

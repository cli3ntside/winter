package dev.luxnet.winter.client.visuals;

import dev.luxnet.winter.client.WinterConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Outlines active player entities other than the local player. */
public final class TeamEsp {
	private static final int REFRESH_TICKS = 10;
	private static final double MAX_RANGE = 128.0;
	private static final int TEAM_COLOR = 0xB8FF4545;
	private static final int DOWNED_COLOR = 0xF8FFD740;
	private static final List<Player> TARGETS = new ArrayList<>();
	private static final Set<String> DOWNED_PLAYERS = new HashSet<>();
	private static final Set<String> ROSTER_NAMES = new HashSet<>();
	private static final Set<Integer> DOWNED_ENTITY_IDS = new HashSet<>();
	private static final Map<String, Vec3> LAST_POSITIONS = new HashMap<>();
	private static final Map<String, Integer> DOWNED_BODY_IDS = new HashMap<>();
	private static boolean enabled;
	private static int renderDistance = 64;
	private static int ticksSinceScan;
	private static Object scannedLevel;
	private static Object downedLevel;

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
		if (!enabled) {
			TARGETS.clear();
			DOWNED_PLAYERS.clear();
			ROSTER_NAMES.clear();
			DOWNED_ENTITY_IDS.clear();
			LAST_POSITIONS.clear();
			DOWNED_BODY_IDS.clear();
		}
		WinterConfig.save();
	}

	public static void tick(Minecraft minecraft) {
		if (!enabled || minecraft.level == null || minecraft.player == null) {
			TARGETS.clear();
			scannedLevel = null;
			downedLevel = null;
			return;
		}
		if (downedLevel != minecraft.level) {
			DOWNED_PLAYERS.clear();
			ROSTER_NAMES.clear();
			DOWNED_ENTITY_IDS.clear();
			LAST_POSITIONS.clear();
			DOWNED_BODY_IDS.clear();
			downedLevel = minecraft.level;
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
		readDownedScoreboard(minecraft);
		Vec3 center = minecraft.player.position();
		double distance = Math.min(MAX_RANGE, renderDistance);
		List<Player> players = new ArrayList<>();
		List<Vec3> reviveMarkers = new ArrayList<>();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (hasReviveMarker(entity)) reviveMarkers.add(entity.position());
			if (entity instanceof Player player && hasPlayerName(player)) players.add(player);
		}
		DOWNED_ENTITY_IDS.clear();
		for (Player player : players) {
			for (Vec3 marker : reviveMarkers) {
				double dx = player.getX() - marker.x;
				double dz = player.getZ() - marker.z;
				double dy = marker.y - player.getY();
				if (dx * dx + dz * dz <= 2.25 && dy >= 0.0 && dy <= 4.5) {
					DOWNED_ENTITY_IDS.add(player.getId());
					break;
				}
			}
		}
		for (Player player : players) {
			if (player == minecraft.player && !isDowned(player)) continue;
			String name = player.getGameProfile().name();
			if (isDowned(player)) {
				if (isRenderableDownedPlayer(player)) addTarget(player, center, distance);
			} else if (isActivePlayer(player)) {
				ROSTER_NAMES.add(name);
				LAST_POSITIONS.put(name, player.position());
				DOWNED_BODY_IDS.remove(name);
				addTarget(player, center, distance);
			}
		}
		// Hypixel may replace a downed teammate with a sleeping player entity using
		// a generated name. Associate that body with the downed roster entry near
		// the teammate's last known position, as the reference ESP does.
		DOWNED_BODY_IDS.entrySet().removeIf(entry -> players.stream().noneMatch(p -> p.getId() == entry.getValue()));
		for (String name : DOWNED_PLAYERS) {
			if (players.stream().anyMatch(p -> p.getGameProfile().name().equals(name))) {
				DOWNED_BODY_IDS.remove(name);
				continue;
			}
			Vec3 lastPosition = LAST_POSITIONS.get(name);
			if (lastPosition == null) continue;
			Player body = null;
			double nearestDistance = 3.0 * 3.0;
			for (Player candidate : players) {
				if (ROSTER_NAMES.contains(candidate.getGameProfile().name()) || candidate.getPose() != Pose.SLEEPING) continue;
				double candidateDistance = candidate.position().distanceToSqr(lastPosition);
				if (candidateDistance <= nearestDistance) {
					body = candidate;
					nearestDistance = candidateDistance;
				}
			}
			if (body != null) {
				DOWNED_BODY_IDS.put(name, body.getId());
				addTarget(body, center, distance);
			}
		}
	}

	private static void addTarget(Player player, Vec3 center, double distance) {
		if (player.position().distanceToSqr(center) <= distance * distance && !TARGETS.contains(player)) TARGETS.add(player);
	}

	/** Called for server chat messages so knockdown/revive announcements update the ESP state. */
	public static void onServerMessage(String rawMessage) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!enabled || minecraft.level == null || !isZombiesGame(minecraft)) return;
		String message = rawMessage.replaceAll("§.", "").toLowerCase(Locale.ROOT);
		boolean downed = message.contains("was knocked down") || message.contains("has been knocked")
			|| message.contains("has been downed") || message.contains("was downed")
			|| rawMessage.contains("击倒了") || rawMessage.contains("倒地了");
		boolean cleared = message.contains("was killed by") || message.contains("has been killed")
			|| message.contains("was slain by") || message.contains("has been slain")
			|| message.contains("revived") || rawMessage.contains("被救起了") || rawMessage.contains("救援了")
			|| rawMessage.contains("复活了") || rawMessage.contains("復活了")
			|| rawMessage.contains("阵亡") || rawMessage.contains("死亡");
		if (!downed && !cleared) return;

		Set<String> names = new HashSet<>(ROSTER_NAMES);
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof Player player && hasPlayerName(player)) names.add(player.getGameProfile().name());
		}
		if (downed) {
			String target = firstPlayerName(message, names);
			if (target != null) DOWNED_PLAYERS.add(target);
		} else {
			for (String name : names) {
				if (containsPlayerName(message, name)) {
					DOWNED_PLAYERS.remove(name);
					DOWNED_BODY_IDS.remove(name);
				}
			}
			if (message.contains("full revive") || message.matches(".*you revived \\d+ players?.*")) {
				DOWNED_PLAYERS.clear();
				DOWNED_BODY_IDS.clear();
			}
		}
	}

	private static boolean containsPlayerName(String message, String name) {
		return java.util.regex.Pattern.compile("(?<![a-z0-9_])" + java.util.regex.Pattern.quote(name.toLowerCase(Locale.ROOT))
			+ "(?![a-z0-9_])").matcher(message).find();
	}

	private static String firstPlayerName(String message, Set<String> names) {
		String first = null;
		int firstIndex = Integer.MAX_VALUE;
		for (String name : names) {
			java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
				"(?<![a-z0-9_])" + java.util.regex.Pattern.quote(name.toLowerCase(Locale.ROOT)) + "(?![a-z0-9_])")
				.matcher(message);
			if (matcher.find() && matcher.start() < firstIndex) {
				first = name;
				firstIndex = matcher.start();
			}
		}
		return first;
	}

	private static void readDownedScoreboard(Minecraft minecraft) {
		Objective sidebar = minecraft.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) return;
		for (PlayerScoreEntry entry : minecraft.level.getScoreboard().listPlayerScores(sidebar)) {
			var display = entry.display();
			String displayText = display == null ? "" : display.getString();
			String row = (entry.owner() + " " + displayText).replaceAll("§.", "").toLowerCase(Locale.ROOT);
			if (!isDownedStatus(row)) continue;
			for (Entity entity : minecraft.level.entitiesForRendering()) {
				if (entity instanceof Player player && hasPlayerName(player)
					&& containsPlayerName(row, player.getGameProfile().name())) {
					DOWNED_PLAYERS.add(player.getGameProfile().name());
				}
			}
		}
	}

	private static boolean isDownedStatus(String text) {
		return text.contains("downed") || text.contains("waiting for revive") || text.contains("awaiting revive")
			|| text.contains("waiting for rescue") || text.contains("倒地") || text.contains("等待救援")
			|| text.contains("待救援");
	}

	private static boolean hasPlayerName(Player player) {
		if (player.isRemoved() || player.isSpectator()) return false;
		var profile = player.getGameProfile();
		return profile != null && profile.name() != null && profile.name().matches("[A-Za-z0-9_]{1,16}");
	}

	private static boolean isDowned(Player player) {
		return DOWNED_PLAYERS.contains(player.getGameProfile().name())
			|| DOWNED_BODY_IDS.containsValue(player.getId()) || DOWNED_ENTITY_IDS.contains(player.getId());
	}

	private static boolean hasReviveMarker(Entity entity) {
		String label = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
		if (entity instanceof Display.TextDisplay textDisplay) label += " " + textDisplay.getText().getString();
		label = label.replaceAll("§.", "").toLowerCase(Locale.ROOT);
		return label.contains("hold sneak to revive") || label.contains("hold shift to revive")
			|| label.contains("sneak to revive");
	}

	private static boolean isRenderableDownedPlayer(Player player) {
		return !player.isRemoved() && !player.isSpectator();

	}

	public static boolean isZombiesGame(Minecraft minecraft) {
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
		float tickDelta = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		for (Player player : TARGETS) {
			if (player.level() != minecraft.level || !hasPlayerName(player)
				|| (isDowned(player) ? !isRenderableDownedPlayer(player) : !isActivePlayer(player))) continue;
			double x = player.xo + (player.getX() - player.xo) * tickDelta;
			double y = player.yo + (player.getY() - player.yo) * tickDelta;
			double z = player.zo + (player.getZ() - player.zo) * tickDelta;
			double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z;
			if (dx * dx + dy * dy + dz * dz > distance * distance) continue;
			var box = player.getBoundingBox().move(x - player.getX(), y - player.getY(), z - player.getZ());
			if (isDowned(player)) {
				EspBoxRenderer.draw(context, box, DOWNED_COLOR, 2.5F);
				EspBoxRenderer.drawLabel(context,
					new Vec3(x, box.maxY + 0.25, z), "REVIVE", DOWNED_COLOR);
			} else {
				EspBoxRenderer.draw(context, box, TEAM_COLOR, 2.0F);
			}
		}
	}
}

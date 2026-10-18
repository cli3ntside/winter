package dev.luxnet.winter.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.luxnet.winter.client.visuals.TeamEsp;
import net.minecraft.client.Minecraft;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** HUD warning for map and difficulty specific boss-round data. */
public final class BossWarning {
	private static final String[] MAP_KEYS = { "alien_arcadium", "dead_end", "bad_blood", "prison" };
	private static final String[] MAP_NAMES = { "AA", "DeadEnd", "BadBlood", "Prison" };
	private static final String[] AA_ALERT_ORDER = { "giant_only", "to1_only", "to1_and_giant" };
	private static final Map<String, Map<String, Set<Integer>>> BOSS_ROUNDS = new HashMap<>();
	private static final Map<String, Map<Integer, List<Integer>>> AA_ALERTS = new HashMap<>();
	private static boolean dataLoaded;
	private static boolean enabled;
	private static int selectedMap = 1;

	private BossWarning() { }

	public static boolean isEnabled() { return enabled; }
	public static void setEnabled(boolean value) { if (enabled != value) { enabled = value; WinterConfig.save(); } }
	public static void toggle() { setEnabled(!enabled); }
	public static int getSelectedMap() { return selectedMap; }
	public static String getMapName() { return MAP_NAMES[selectedMap]; }
	public static void cycleMap(int direction) {
		selectedMap = Math.floorMod(selectedMap + direction, MAP_NAMES.length);
		WinterConfig.save();
	}
	public static void setSelectedMap(int index) { selectedMap = Math.max(0, Math.min(MAP_NAMES.length - 1, index)); }

	/** Returns the active warning, including AA's per-wave special-boss groups. */
	public static String getLiveLabel() {
		Minecraft minecraft = Minecraft.getInstance();
		if (!enabled || minecraft.player == null || minecraft.level == null || !TeamEsp.isZombiesGame(minecraft)) return null;
		int round = PowerupTracker.getCurrentRound();
		if (round <= 0) return null;
		loadData();

		String map = MAP_KEYS[selectedMap];
		String difficulty = PowerupTracker.getCurrentDifficulty().toLowerCase(Locale.ROOT);
		if (!Set.of("normal", "hard", "rip").contains(difficulty)) difficulty = "normal";
		Set<Integer> rounds = BOSS_ROUNDS.getOrDefault(map, Map.of()).get(difficulty);
		if (rounds == null) rounds = BOSS_ROUNDS.getOrDefault(map, Map.of()).get("normal");
		boolean bossRound = rounds != null && rounds.contains(round);

		List<String> aaAlerts = selectedMap == 0 ? aaAlertLabels(round) : List.of();
		if (!bossRound && aaAlerts.isEmpty()) return null;
		StringBuilder label = new StringBuilder();
		if (bossRound) label.append("BOSS ROUND · ").append(round);
		else label.append("AA ALERT · R").append(round);
		for (String alert : aaAlerts) label.append(" · ").append(alert);
		return label.toString();
	}

	public static String getPreviewLabel() { return "BOSS ROUND · 10"; }

	private static List<String> aaAlertLabels(int round) {
		List<String> labels = new ArrayList<>(3);
		for (String kind : AA_ALERT_ORDER) {
			List<Integer> waves = AA_ALERTS.getOrDefault(kind, Map.of()).get(round);
			if (waves == null || waves.isEmpty()) continue;
			String name = switch (kind) {
				case "giant_only" -> "Giant only";
				case "to1_only" -> "TO1 only";
				default -> "TO1+Giant";
			};
			labels.add(name + " W" + String.join(",", waves.stream().map(String::valueOf).toList()));
		}
		return labels;
	}

	private static void loadData() {
		if (dataLoaded) return;
		dataLoaded = true;
		try (var stream = BossWarning.class.getResourceAsStream("/assets/winter/data/boss_rounds.json")) {
			if (stream == null) return;
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonObject bossRounds = root.getAsJsonObject("boss_rounds");
			for (String map : bossRounds.keySet()) {
				Map<String, Set<Integer>> difficultyTable = new HashMap<>();
				JsonObject byDifficulty = bossRounds.getAsJsonObject(map);
				for (String difficulty : byDifficulty.keySet()) {
					Set<Integer> values = new HashSet<>();
					for (JsonElement value : byDifficulty.getAsJsonArray(difficulty)) values.add(value.getAsInt());
					difficultyTable.put(difficulty.toLowerCase(Locale.ROOT), values);
				}
				BOSS_ROUNDS.put(map, difficultyTable);
			}
			JsonObject alerts = root.getAsJsonObject("aa_color_alert");
			for (String kind : alerts.keySet()) {
				Map<Integer, List<Integer>> rounds = new HashMap<>();
				JsonObject byRound = alerts.getAsJsonObject(kind);
				for (String round : byRound.keySet()) {
					List<Integer> waves = new ArrayList<>();
					for (JsonElement wave : byRound.getAsJsonArray(round)) waves.add(wave.getAsInt());
					rounds.put(Integer.parseInt(round), waves);
				}
				AA_ALERTS.put(kind, rounds);
			}
		} catch (Exception exception) {
			System.err.println("[Winter] Could not load boss round data: " + exception.getMessage());
		}
	}
}

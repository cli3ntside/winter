package dev.luxnet.winter.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import dev.luxnet.winter.client.visuals.TeamEsp;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Power-up detection, active/drop timers and learned round-pattern predictions. */
public final class PowerupTracker {
	private static final Pattern ROUND_EN = Pattern.compile("(?i)\\bround\\s*#?\\s*(\\d+)");
	private static final Pattern ROUND_ZH = Pattern.compile("第\\s*(\\d+)\\s*回合");
	private static final Pattern DIFFICULTY_PATTERN = Pattern.compile("(?i)\\bdifficulty\\s*:?\\s*(rip|hard|normal|安息|普通)");
	private static final long DROP_LIFETIME_MS = 60_000L;
	private static final PowerupType[] ORDER = { PowerupType.INSTA_KILL, PowerupType.MAX_AMMO,
		PowerupType.SHOPPING_SPREE, PowerupType.DOUBLE_GOLD, PowerupType.BONUS_GOLD, PowerupType.CARPENTER };
	private static final Map<String, Map<PowerupType, List<PatternEntry>>> PATTERNS = new HashMap<>();
	private static final Map<PowerupType, PatternEntry> COMMITTED = new EnumMap<>(PowerupType.class);
	private static final Map<PowerupType, ActiveTimer> ACTIVE = new EnumMap<>(PowerupType.class);
	private static final Map<PowerupType, Long> DROPPED = new EnumMap<>(PowerupType.class);
	private static final Map<Integer, Long> STAND_EXPIRIES = new HashMap<>();
	private static final Map<PowerupType, Integer> OBSERVED_ROUND = new EnumMap<>(PowerupType.class);
	private static final Map<Integer, PowerupType> STAND_TYPES = new HashMap<>();
	private static final Set<Integer> SEEN_STANDS = new HashSet<>();
	private static boolean enabled;
	private static boolean timerEnabled;
	private static boolean predictorEnabled;
	private static boolean loadedPatterns;
	private static boolean wasInGame;
	private static Object currentLevel;
	private static String currentMap = "";
	private static String currentDifficulty = "normal";
	private static int currentRound;
	private static int scanTicks;

	private PowerupTracker() { }

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PowerupTracker::tick);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message.getString()));
	}

	public static boolean isEnabled() { return enabled; }
	public static boolean isTimerEnabled() { return timerEnabled; }
	public static boolean isPredictorEnabled() { return predictorEnabled; }
	public static int getCurrentRound() { return currentRound; }
	public static String getCurrentDifficulty() { return currentDifficulty; }
	/** Seconds until this dropped power-up's one-minute lifetime expires. */
	public static int droppedSeconds(int entityId) {
		long expiresAt = STAND_EXPIRIES.getOrDefault(entityId, System.currentTimeMillis() + DROP_LIFETIME_MS);
		return (int) Math.max(0, (expiresAt - System.currentTimeMillis() + 999) / 1000);
	}
	public static void setEnabled(boolean value) { if (enabled != value) { enabled = value; WinterConfig.save(); } }
	public static void setTimerEnabled(boolean value) { if (timerEnabled != value) { timerEnabled = value; WinterConfig.save(); } }
	public static void setPredictorEnabled(boolean value) { if (predictorEnabled != value) { predictorEnabled = value; WinterConfig.save(); } }

	public static List<TimerRow> activeTimerRows() {
		long now = System.currentTimeMillis();
		List<TimerRow> rows = new ArrayList<>();
		for (PowerupType type : ORDER) {
			ActiveTimer active = ACTIVE.get(type);
			if (active != null && active.expiresAt > now) {
				long remaining = active.expiresAt - now;
				int seconds = (int) Math.max(1, (remaining + 999) / 1000);
				rows.add(new TimerRow(type.label, seconds, Math.min(1.0, remaining / (double) active.durationMs)));
			}
			if (rows.size() == 3) break;
		}
		return rows;
	}

	public static List<String> predictorLines() {
		List<String> lines = new ArrayList<>();
		lines.add("Powerup Predictor");
		if (currentRound <= 0 || currentMap.isEmpty()) {
			lines.add("Waiting for round data");
			return lines;
		}
		for (PowerupType type : ORDER) {
			PatternEntry entry = COMMITTED.get(type);
			if (entry == null) continue;
			int minRound = OBSERVED_ROUND.getOrDefault(type, -1) == currentRound ? currentRound + 1 : currentRound;
			int round = nextRound(entry, minRound);
			if (round >= 0 && round <= currentRound + 10) lines.add(type.label + "  R" + String.format(Locale.ROOT, "%02d", round));
		}
		if (lines.size() == 1) lines.add("Learned from drops");
		return lines;
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null || !TeamEsp.isZombiesGame(minecraft)) {
			if (wasInGame || currentLevel != null && minecraft.level != currentLevel) clearRuntime();
			wasInGame = false;
			currentLevel = minecraft.level;
			return;
		}
		wasInGame = true;
		if (currentLevel != minecraft.level) {
			clearRuntime();
			currentLevel = minecraft.level;
		}
		readRoundAndMap(minecraft);
		long now = System.currentTimeMillis();
		ACTIVE.values().removeIf(timer -> timer.expiresAt <= now);
		DROPPED.values().removeIf(expiry -> expiry <= now);
		if (++scanTicks % 4 != 0) return;
		Set<Integer> liveIds = new HashSet<>();
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof ArmorStand stand) || stand.getCustomName() == null) continue;
			PowerupType type = PowerupType.fromName(plain(stand.getCustomName().getString()));
			if (type == null) continue;
			liveIds.add(stand.getId());
			STAND_TYPES.put(stand.getId(), type);
			if (SEEN_STANDS.add(stand.getId())) {
				STAND_EXPIRIES.put(stand.getId(), now + DROP_LIFETIME_MS);
				DROPPED.put(type, now + DROP_LIFETIME_MS);
				OBSERVED_ROUND.put(type, currentRound);
				commitPattern(type, currentRound);
			}
		}
		for (int id : new ArrayList<>(STAND_TYPES.keySet())) {
			if (liveIds.contains(id)) continue;
			PowerupType removedType = STAND_TYPES.remove(id);
			STAND_EXPIRIES.remove(id);
			if (STAND_TYPES.containsValue(removedType) || ACTIVE.containsKey(removedType)) continue;
			DROPPED.remove(removedType);
		}
		SEEN_STANDS.retainAll(liveIds);
	}

	private static void onMessage(String raw) {
		Minecraft minecraft = Minecraft.getInstance();
		String message = plain(raw);
		readRoundFromText(message);
		if (minecraft.level == null || !TeamEsp.isZombiesGame(minecraft) || message.contains(":")) return;
		String lower = message.toLowerCase(Locale.ROOT);
		if (!(lower.contains("enabled") || lower.contains("activated") || lower.contains("picked up")
			|| lower.contains("picked up a") || message.contains("已激活") || message.contains("已啟動")
			|| message.contains("启用") || message.contains("啟用"))) return;
		PowerupType type = PowerupType.fromName(message);
		if (type == null) return;
		Matcher duration = Pattern.compile("(?i)(?:for\\s+)?(\\d+)\\s*(?:s|sec|seconds|秒)").matcher(message);
		int seconds = duration.find() ? Integer.parseInt(duration.group(1)) : 0;
		long durationMs = Math.max(3, seconds) * 1000L;
		ACTIVE.put(type, new ActiveTimer(System.currentTimeMillis() + durationMs, durationMs));
		DROPPED.remove(type);
		OBSERVED_ROUND.put(type, currentRound);
		commitPattern(type, currentRound);
	}

	public static void onTitle(String title) { readRoundFromText(plain(title)); }

	private static void readRoundAndMap(Minecraft minecraft) {
		Objective sidebar = minecraft.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) return;
		readRoundFromText(plain(sidebar.getDisplayName().getString()));
		StringBuilder text = new StringBuilder();
		for (PlayerScoreEntry entry : minecraft.level.getScoreboard().listPlayerScores(sidebar)) {
			String row = entry.owner() + " " + (entry.display() == null ? "" : entry.display().getString());
			row = plain(row);
			text.append(' ').append(row);
			readRoundFromText(row);
		}
		currentDifficulty = identifyDifficulty(text.toString());
		currentMap = identifyMap(plain(sidebar.getDisplayName().getString()) + " " + text);
		if (currentMap.isEmpty() && minecraft.level.hasChunkAt(new BlockPos(0, 72, 12))) {
			var state = minecraft.level.getBlockState(new BlockPos(0, 72, 12));
			if (state.is(BlockTags.WOOL_CARPETS)) currentMap = "alien_arcadium";
			else if (state.is(Blocks.STONE_BRICKS)) currentMap = "bad_blood";
			else if (state.is(BlockTags.WOOL)) currentMap = "dead_end";
			else if (state.is(BlockTags.TERRACOTTA)) currentMap = "prison";
		}
	}

	private static String identifyDifficulty(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		Matcher matcher = DIFFICULTY_PATTERN.matcher(lower);
		if (!matcher.find()) return "normal";
		String value = matcher.group(1);
		if (value.equals("rip") || value.equals("安息")) return "rip";
		if (value.equals("hard")) return "hard";
		return "normal";
	}

	private static String identifyMap(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		if (lower.contains("alien arcadium") || lower.contains("alien arcade")) return "alien_arcadium";
		if (lower.contains("bad blood")) return "bad_blood";
		if (lower.contains("dead end")) return "dead_end";
		if (lower.contains("prison")) return "prison";
		return "";
	}

	private static void readRoundFromText(String text) {
		Matcher matcher = ROUND_EN.matcher(text);
		int next;
		if (matcher.find()) next = Integer.parseInt(matcher.group(1));
		else {
			matcher = ROUND_ZH.matcher(text);
			if (!matcher.find()) return;
			next = Integer.parseInt(matcher.group(1));
		}
		if (next > 0 && next != currentRound) currentRound = next;
	}

	private static void commitPattern(PowerupType type, int round) {
		if (round <= 0 || currentMap.isEmpty() || COMMITTED.containsKey(type)) return;
		loadPatterns();
		List<PatternEntry> entries = PATTERNS.getOrDefault(currentMap, Map.of()).getOrDefault(type, List.of());
		for (PatternEntry entry : entries) {
			if (entry.contains(round)) { COMMITTED.put(type, entry); return; }
		}
		if (entries.isEmpty() && (type == PowerupType.INSTA_KILL || type == PowerupType.MAX_AMMO
			|| type == PowerupType.SHOPPING_SPREE)) COMMITTED.put(type, new PatternEntry(new int[]{round}, new int[]{round % 10}));
	}

	private static int nextRound(PatternEntry entry, int round) {
		int best = Integer.MAX_VALUE;
		for (int explicit : entry.rounds) if (explicit >= round) best = Math.min(best, explicit);
		if (best != Integer.MAX_VALUE) return best;
		for (int decade = round - round % 10; decade <= round + 100; decade += 10)
			for (int digit : entry.digits) if (decade + digit >= round) return decade + digit;
		return -1;
	}

	private static void loadPatterns() {
		if (loadedPatterns) return;
		loadedPatterns = true;
		try (var stream = PowerupTracker.class.getResourceAsStream("/assets/winter/data/powerup_patterns.json")) {
			if (stream == null) return;
			JsonObject maps = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
				.getAsJsonObject().getAsJsonObject("maps");
			for (Map.Entry<String, JsonElement> map : maps.entrySet()) {
				EnumMap<PowerupType, List<PatternEntry>> parsed = new EnumMap<>(PowerupType.class);
				for (Map.Entry<String, JsonElement> typeEntry : map.getValue().getAsJsonObject().entrySet()) {
					PowerupType type = PowerupType.fromId(typeEntry.getKey());
					if (type == null) continue;
					List<PatternEntry> patterns = new ArrayList<>();
					for (JsonElement item : typeEntry.getValue().getAsJsonArray()) {
						JsonObject object = item.getAsJsonObject();
						patterns.add(new PatternEntry(toIntArray(object.getAsJsonArray("rounds")),
							toIntArray(object.has("digits") ? object.getAsJsonArray("digits") : new JsonArray())));
					}
					parsed.put(type, patterns);
				}
				PATTERNS.put(map.getKey(), parsed);
			}
		} catch (Exception exception) {
			System.err.println("[Winter] Could not load powerup patterns: " + exception.getMessage());
		}
	}

	private static int[] toIntArray(JsonArray array) {
		int[] result = new int[array.size()];
		for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsInt();
		return result;
	}

	private static String plain(String text) { return text.replaceAll("§.", "").replaceAll("[^\\p{L}\\p{N}_:# ]", "").trim(); }

	private static void clearRuntime() {
		ACTIVE.clear(); DROPPED.clear(); OBSERVED_ROUND.clear(); STAND_TYPES.clear(); STAND_EXPIRIES.clear(); SEEN_STANDS.clear(); COMMITTED.clear();
		currentRound = 0; currentMap = ""; currentDifficulty = "normal"; scanTicks = 0;
	}

	public record TimerRow(String label, int seconds, double progress) { }
	private record ActiveTimer(long expiresAt, long durationMs) { }
	private record PatternEntry(int[] rounds, int[] digits) {
		boolean contains(int round) { for (int value : rounds) if (value == round) return true; return false; }
	}

	private enum PowerupType {
		INSTA_KILL("insta_kill", "Insta Kill", "insta kill", "秒杀", "一擊必殺", "瞬间击杀", "瞬間擊殺"),
		MAX_AMMO("max_ammo", "Max Ammo", "max ammo", "满弹药", "滿彈藥", "弹药满载", "彈藥滿載"),
		DOUBLE_GOLD("double_gold", "Double Gold", "double gold", "双倍金币", "雙倍金幣", "双倍金钱", "雙倍金錢"),
		CARPENTER("carpenter", "Carpenter", "carpenter", "木匠"),
		BONUS_GOLD("bonus_gold", "Bonus Gold", "bonus gold", "额外金币", "額外金幣"),
		SHOPPING_SPREE("shopping_spree", "Shopping Spree", "shopping spree", "shop spree", "购物狂潮", "購物狂潮");
		final String id;
		final String label;
		final String[] names;
		PowerupType(String id, String label, String... names) { this.id = id; this.label = label; this.names = names; }
		static PowerupType fromName(String name) {
			String lower = name.toLowerCase(Locale.ROOT);
			for (PowerupType type : values()) for (String alias : type.names)
				if (lower.contains(alias.toLowerCase(Locale.ROOT))) return type;
			return null;
		}
		static PowerupType fromId(String id) { for (PowerupType type : values()) if (type.id.equals(id)) return type; return null; }
	}
}

package dev.luxnet.winter.client.visuals;

import dev.luxnet.winter.client.WinterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/** Selective chat noise filter adapted from NoMoreZombies. */
public final class ChatFilter {
	private static final Pattern GOLD = Pattern.compile(
		"\\+\\s*\\d+\\s*(金钱|金币|金幣|金錢|Gold|coin|硬币|硬幣)"
			+ "|(你获得了|You received|You got|You earned)\\s*\\d+\\s*(金钱|金币|金幣|金錢|Gold|coin|硬币|硬幣)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern WINDOW = Pattern.compile(
		"正在修理窗户|已停止修理|停止修理|完全修好|修好.*窗户"
			+ "|repairing windows|stopped repairing|fully repaired|can't repair windows|cannot repair windows",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern HIT_TARGET = Pattern.compile("击中了目标|擊中目標|hit the target", Pattern.CASE_INSENSITIVE);
	private static final Pattern LUCKY_CHEST = Pattern.compile("幸运箱|幸運箱|lucky chest", Pattern.CASE_INSENSITIVE);
	private static final Pattern OPEN_AREA = Pattern.compile("开启了|打开了|開啟了|打開了|opened the|opened up", Pattern.CASE_INSENSITIVE);
	private static final Pattern PLAYER_CONNECTION = Pattern.compile(
		"加入了游戏|加入遊戲|joined the game|has joined|left the game|离开了游戏|退出了游戏|重新加入|重新進入|rejoined",
		Pattern.CASE_INSENSITIVE);
	private static boolean enabled;
	private static final boolean[] FILTERS = { true, false, false, false, false, false };

	private ChatFilter() { }

	public static boolean isEnabled() { return enabled; }
	public static void setEnabled(boolean value) { if (enabled != value) { enabled = value; WinterConfig.save(); } }
	public static void toggle() { setEnabled(!enabled); }
	public static boolean isFilterEnabled(Filter filter) { return FILTERS[filter.ordinal()]; }
	public static void setFilterEnabled(Filter filter, boolean value) {
		if (FILTERS[filter.ordinal()] != value) { FILTERS[filter.ordinal()] = value; WinterConfig.save(); }
	}

	public static boolean shouldHide(Component component) {
		if (!enabled || component == null || Minecraft.getInstance().level == null) return false;
		Minecraft minecraft = Minecraft.getInstance();
		if (!TeamEsp.isZombiesGame(minecraft)) return false;
		String message = component.getString().replaceAll("§.", "").trim();
		if (message.isEmpty() || isPowerupActivation(message)) return false;
		if (FILTERS[Filter.GOLD.ordinal()] && GOLD.matcher(message).find()) return true;
		if (FILTERS[Filter.WINDOW_REPAIR.ordinal()] && WINDOW.matcher(message).find()) return true;
		if (FILTERS[Filter.HIT_TARGET.ordinal()] && HIT_TARGET.matcher(message).find()) return true;
		if (FILTERS[Filter.LUCKY_CHEST.ordinal()] && LUCKY_CHEST.matcher(message).find()) return true;
		if (FILTERS[Filter.OPEN_AREA.ordinal()] && OPEN_AREA.matcher(message).find()) return true;
		return FILTERS[Filter.PLAYER_CONNECTION.ordinal()] && PLAYER_CONNECTION.matcher(message).find();
	}

	private static boolean isPowerupActivation(String message) {
		String lower = message.toLowerCase(Locale.ROOT);
		return lower.contains("activated") || lower.contains("enabled")
			|| message.contains("已激活") || message.contains("已啟動") || message.contains("启用") || message.contains("啟用");
	}

	public enum Filter {
		GOLD("gold", "Gold gain"),
		WINDOW_REPAIR("windowRepair", "Window repair"),
		HIT_TARGET("hitTarget", "Hit confirmations"),
		LUCKY_CHEST("luckyChest", "Lucky Chest"),
		OPEN_AREA("openArea", "Opened areas"),
		PLAYER_CONNECTION("playerConnection", "Player joins/leaves");

		private final String key;
		private final String label;
		Filter(String key, String label) { this.key = key; this.label = label; }
		public String key() { return key; }
		public String label() { return label; }
		public boolean defaultValue() { return this == GOLD; }
	}
}

package dev.luxnet.winter.client.debug;

import dev.luxnet.winter.Winter;
import dev.luxnet.winter.client.WinterConfig;
import dev.luxnet.winter.client.WeaponCatalog;
import dev.luxnet.winter.client.visuals.BulletTracers;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ShotDebugModule {
	private static final int MAX_EVENTS = 100;
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SS");
	private static final ArrayDeque<String> EVENTS = new ArrayDeque<>();
	private static boolean enabled;
	private static Boolean wasSneaking;

	private ShotDebugModule() {
	}

	public static synchronized boolean isEnabled() {
		return enabled;
	}

	public static synchronized void toggle() {
		setEnabled(!enabled);
	}

	public static synchronized void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		record(enabled ? "TRACE ENABLED" : "TRACE DISABLED");
		WinterConfig.save();
	}

	public static synchronized List<String> getEvents() {
		return new ArrayList<>(EVENTS);
	}

	public static synchronized void recordOutgoing(Packet<?> packet) {
		if (!enabled) return;

		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;
		if (player == null) return;

		if (packet instanceof ServerboundUseItemPacket useItem) {
			ItemStack item = player.getItemInHand(useItem.getHand());
			String shotPattern = WeaponCatalog.detectionSignal(item).orElseGet(() ->
				WeaponCatalog.fireRateSeconds(item).isPresent()
					? "fire-rate-candidate delay=" + WeaponCatalog.fireRateSeconds(item).getAsDouble() + "s"
					: "unknown");
			record("RIGHT CLICK REQUEST " + useItem.getHand() + " | " + WeaponCatalog.describe(item)
				+ " | pattern=" + shotPattern);
		} else if (packet instanceof ServerboundSwingPacket swing) {
			ItemStack item = player.getItemInHand(swing.getHand());
			String action = WeaponCatalog.isKnownWeapon(item) && !WeaponCatalog.isMeleeWeapon(item)
				? "left-click/reload-candidate" : "left-click/swing";
			record("OUT SWING " + swing.getHand() + " " + action + " | " + WeaponCatalog.describe(item));
		}
	}

	public static synchronized void recordSneakState(boolean sneaking) {
		if (wasSneaking == null || wasSneaking == sneaking) {
			wasSneaking = sneaking;
			return;
		}
		wasSneaking = sneaking;
		if (enabled) record("SNEAK " + (sneaking ? "DOWN" : "UP"));
	}

	public static synchronized void recordReloadCandidate(ItemStack item, double seconds, int damage, int maxDamage) {
		if (!enabled) return;
		record("RELOAD CANDIDATE " + WeaponCatalog.describe(item) + " duration=" + seconds + "s durability="
			+ damage + "/" + maxDamage);
	}

	public static synchronized void recordReloadConfirmed(ItemStack item, int previousDamage, int currentDamage, int maxDamage) {
		if (!enabled) return;
		record("RELOAD CONFIRMED " + WeaponCatalog.describe(item) + " durability=" + previousDamage + "->"
			+ currentDamage + "/" + maxDamage);
	}

	public static synchronized void recordServerMessage(ClientboundSystemChatPacket packet) {
		if (!enabled) return;
		String message = packet.content().getString();
		String normalized = message.toLowerCase(Locale.ROOT);
		String ownName = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getName().getString();
		if (!normalized.startsWith("you ") && !normalized.startsWith("you' ")
			&& (ownName.isEmpty() || !message.contains(ownName))) return;
		if (normalized.contains("opened ") || normalized.contains("purchased ")
			|| normalized.contains("activated ") || normalized.contains("ammo")
			|| normalized.contains("repair") || normalized.contains("reviv")) {
			record("SERVER ACTION " + message);
		}
	}

	public static synchronized void recordSound(ClientboundSoundPacket packet) {
		String sound = packet.getSound().unwrapKey()
			.map(key -> key.identifier().toString())
			.orElse("unknown");
		String weapon = BulletTracers.observeSignal("sound", sound,
			new net.minecraft.world.phys.Vec3(packet.getX(), packet.getY(), packet.getZ()));
		if (enabled && weapon != null) {
			record("SHOT CONFIRMED " + weapon + " by sound " + sound + " " + packet.getSource() + " at "
				+ coordinates(packet.getX(), packet.getY(), packet.getZ()));
		}
	}

	public static synchronized void recordSoundEntity(ClientboundSoundEntityPacket packet) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) return;
		var entity = minecraft.level.getEntity(packet.getId());
		if (entity instanceof Player otherPlayer && otherPlayer != minecraft.player) return;
		String sound = packet.getSound().unwrapKey()
			.map(key -> key.identifier().toString())
			.orElse("unknown");
		var position = entity == null ? minecraft.player.position() : entity.position();
		String weapon = BulletTracers.observeSignal("sound", sound, position);
		if (enabled && weapon != null) {
			record("SHOT CONFIRMED " + weapon + " by entity sound " + sound + " " + packet.getSource());
		}
	}

	public static synchronized void recordParticle(ClientboundLevelParticlesPacket packet) {
		String particle = BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType()).toString();
		String weapon = BulletTracers.observeSignal("particle", particle,
			new net.minecraft.world.phys.Vec3(packet.getX(), packet.getY(), packet.getZ()));
		if (enabled && weapon != null) {
			record("SHOT CONFIRMED " + weapon + " by particle " + particle + " at "
				+ coordinates(packet.getX(), packet.getY(), packet.getZ()));
		}
	}

	private static String coordinates(double x, double y, double z) {
		return String.format(Locale.ROOT, "%.1f,%.1f,%.1f", x, y, z);
	}

	private static void record(String message) {
		while (EVENTS.size() >= MAX_EVENTS) EVENTS.removeFirst();
		String entry = TIME_FORMAT.format(LocalTime.now()) + " " + message;
		EVENTS.addLast(entry);
		Winter.LOGGER.info("[shot-trace] {}", entry);
	}
}

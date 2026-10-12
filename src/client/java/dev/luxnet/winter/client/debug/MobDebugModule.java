package dev.luxnet.winter.client.debug;

import dev.luxnet.winter.Winter;
import dev.luxnet.winter.client.WinterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Collects nearby client-visible mob state for future gameplay modules. */
public final class MobDebugModule {
	private static final double RADIUS = 64.0;
	private static final double RADIUS_SQUARED = RADIUS * RADIUS;
	private static final Map<UUID, MobSnapshot> SNAPSHOTS = new LinkedHashMap<>();
	private static boolean enabled;
	private static int ticksUntilScan;

	private MobDebugModule() { }

	public static synchronized boolean isEnabled() {
		return enabled;
	}

	public static synchronized void toggle() {
		setEnabled(!enabled);
	}

	public static synchronized void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		ticksUntilScan = 0;
		if (!enabled) SNAPSHOTS.clear();
		Winter.LOGGER.info("[mob-detect] {}", enabled ? "enabled" : "disabled");
		WinterConfig.save();
	}

	public static synchronized List<MobSnapshot> getSnapshots() {
		return List.copyOf(SNAPSHOTS.values());
	}

	public static synchronized MobSnapshot getSnapshot(UUID uuid) {
		return SNAPSHOTS.get(uuid);
	}

	public static synchronized void tick(Minecraft minecraft) {
		if (!enabled || minecraft.player == null || minecraft.level == null) return;
		if (ticksUntilScan-- > 0) return;
		ticksUntilScan = 20;

		var player = minecraft.player;
		Map<UUID, MobSnapshot> current = new LinkedHashMap<>();
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof Mob mob) || mob.isRemoved()) continue;
			double distanceSquared = mob.distanceToSqr(player);
			if (distanceSquared > RADIUS_SQUARED) continue;

			String type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
			LivingEntity target = mob.getTarget();
			Map<EquipmentSlot, ItemSnapshot> equipment = snapshotEquipment(mob);
			MobSnapshot snapshot = new MobSnapshot(
				mob.getUUID(), mob.getId(), type, mob.getName().getString(),
				mob.getX(), mob.getY(), mob.getZ(), mob.getHealth(), mob.getMaxHealth(),
				Math.sqrt(distanceSquared), target == null ? "" : target.getName().getString(),
				mob.isAlive(), equipment, Instant.now().toEpochMilli()
			);
			MobSnapshot previous = SNAPSHOTS.get(snapshot.uuid());
			current.put(snapshot.uuid(), snapshot);
			if (previous == null) {
				logSnapshot("seen", snapshot);
			} else if (!previous.equipment().equals(snapshot.equipment())) {
				logSnapshot("equipment-change", snapshot);
			} else if (Math.abs(previous.health() - snapshot.health()) >= 0.01F) {
				logSnapshot("health-change", snapshot);
			}
		}
		SNAPSHOTS.clear();
		SNAPSHOTS.putAll(current);
	}

	private static Map<EquipmentSlot, ItemSnapshot> snapshotEquipment(Mob mob) {
		EnumMap<EquipmentSlot, ItemSnapshot> equipment = new EnumMap<>(EquipmentSlot.class);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			equipment.put(slot, snapshotItem(slot, mob.getItemBySlot(slot)));
		}
		return Map.copyOf(equipment);
	}

	private static ItemSnapshot snapshotItem(EquipmentSlot slot, ItemStack stack) {
		if (stack.isEmpty()) {
			return new ItemSnapshot(slot, "minecraft:air", "Air", 0, 0, 0, "empty", "", "");
		}
		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		String name = stack.getHoverName().getString();
		String components = stack.getComponents().toString();
		String componentPatch = stack.getComponentsPatch().toString();
		String fingerprint = fingerprint(itemId, stack.getCount(), components, componentPatch);
		return new ItemSnapshot(slot, itemId, name, stack.getCount(), stack.getDamageValue(), stack.getMaxDamage(),
			fingerprint, components, componentPatch);
	}

	private static void logSnapshot(String event, MobSnapshot mob) {
		Winter.LOGGER.info("[mob-detect] {} type={} entityId={} uuid={} name=\"{}\" pos=({},{},{}) "
				+ "health={}/{} distance={} target=\"{}\" alive={} equipment={}",
			event, mob.type(), mob.entityId(), mob.uuid(), mob.name(),
			String.format(java.util.Locale.ROOT, "%.2f", mob.x()),
			String.format(java.util.Locale.ROOT, "%.2f", mob.y()),
			String.format(java.util.Locale.ROOT, "%.2f", mob.z()),
			String.format(java.util.Locale.ROOT, "%.1f", mob.health()),
			String.format(java.util.Locale.ROOT, "%.1f", mob.maxHealth()),
			String.format(java.util.Locale.ROOT, "%.1f", mob.distance()), mob.target(), mob.alive(),
			formatEquipment(mob.equipment()));
	}

	private static String formatEquipment(Map<EquipmentSlot, ItemSnapshot> equipment) {
		StringBuilder result = new StringBuilder("{");
		boolean first = true;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (!first) result.append(", ");
			first = false;
			ItemSnapshot item = equipment.get(slot);
			result.append(slot.getSerializedName()).append('=').append(item.itemId())
				.append("[name=\"").append(sanitize(item.name())).append("\",count=").append(item.count())
				.append(",damage=").append(item.damage()).append('/').append(item.maxDamage())
				.append(",fingerprint=").append(item.fingerprint())
				.append(",components=").append(sanitize(item.components()))
				.append(",componentPatch=").append(sanitize(item.componentPatch())).append(']');
		}
		return result.append('}').toString();
	}

	private static String sanitize(String value) {
		return value.replace("\n", " ").replace("\r", " ");
	}

	private static String fingerprint(String itemId, int count, String components, String componentPatch) {
		String data = itemId + '\u0000' + count + '\u0000' + components + '\u0000' + componentPatch;
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 8);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	public record MobSnapshot(UUID uuid, int entityId, String type, String name,
							   double x, double y, double z, float health, float maxHealth,
							   double distance, String target, boolean alive,
							   Map<EquipmentSlot, ItemSnapshot> equipment, long lastSeenEpochMillis) { }

	public record ItemSnapshot(EquipmentSlot slot, String itemId, String name, int count,
								int damage, int maxDamage, String fingerprint,
								String components, String componentPatch) { }
}

package dev.luxnet.winter.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Set;
import java.util.Optional;

public final class WeaponCatalog {
	private static final Set<String> WEAPON_NAMES = Set.of(
		"knife", "pistol", "ultimate pistol", "rifle", "ultimate rifle", "shotgun", "ultimate shotgun",
		"rocket launcher", "ultimate rocket launcher", "sniper", "ultimate sniper", "elder gun",
		"ultimate elder gun", "flame thrower", "flamethrower", "ultimate flamethrower", "zombie soaker",
		"ultimate zombie soaker", "gold digger", "ultimate gold digger i", "ultimate gold digger ii",
		"ultimate gold digger iii", "ultimate gold digger iv", "ultimate gold digger v", "the puncher",
		"upgraded puncher", "zombie zapper", "ultimate zombie zapper", "blow dart", "ultimate blow dart",
		"rainbow rifle", "rainbow rifle ultimate i", "rainbow rifle ultimate ii", "rainbow rifle ultimate iii",
		"double barrel shotgun", "double barrel shotgun ultimate i", "double barrel shotgun ultimate ii",
		"double barrel shotgun ultimate iii", "пистолет", "дробовик", "винтовка", "нож",
		"тренировочное оружие"
	);

	private WeaponCatalog() {
	}

	public static boolean isKnownWeapon(ItemStack stack) {
		String normalizedName = stack.getHoverName().getString().toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9а-яё ]", "").trim();
		return WEAPON_NAMES.contains(normalizedName);
	}

	public static boolean isMeleeWeapon(ItemStack stack) {
		String normalizedName = stack.getHoverName().getString().toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9а-яё ]", "").trim();
		return normalizedName.equals("knife") || normalizedName.equals("нож");
	}

	public static Optional<String> detectionSignal(ItemStack stack) {
		String name = normalizedName(stack);
		if (name.equals("pistol") || name.equals("ultimate pistol")) {
			return Optional.of("sound:minecraft:entity.iron_golem.hurt");
		}
		if (name.equals("rifle") || name.equals("ultimate rifle") || name.startsWith("rainbow rifle")) {
			return Optional.of("particle:minecraft:firework");
		}
		if (name.equals("shotgun") || name.startsWith("double barrel shotgun")) {
			return Optional.of("particle:minecraft:large_smoke|minecraft:smoke");
		}
		return Optional.empty();
	}

	public static String normalizedName(ItemStack stack) {
		return stack.getHoverName().getString().toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9а-яё ]", "").trim();
	}

	public static String describe(ItemStack stack) {
		String displayName = stack.getHoverName().getString();
		String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		String recognized = isKnownWeapon(stack) ? "weapon=" + displayName : "unmatched";
		return displayName + " (" + registryId + ", " + recognized + ")";
	}
}

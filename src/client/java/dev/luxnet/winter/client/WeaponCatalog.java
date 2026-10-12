package dev.luxnet.winter.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.Locale;
import java.util.Set;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WeaponCatalog {
	private static final Pattern FIRE_RATE_PATTERN = Pattern.compile(
		"(?i)\\bFire\\s*Rate\\s*:\\s*([0-9]+(?:[.,][0-9]+)?)\\s*s\\b"
	);
	private static final Pattern RELOAD_PATTERN = Pattern.compile(
		"(?i)\\bReload\\s*:\\s*([0-9]+(?:[.,][0-9]+)?)\\s*s\\b"
	);
	private static final Set<String> WEAPON_NAMES = Set.of(
		"knife", "pistol", "ultimate pistol", "rifle", "ultimate rifle", "shotgun", "ultimate shotgun",
		"rocket launcher", "ultimate rocket launcher", "sniper", "ultimate sniper", "elder gun",
		"ultimate elder gun", "flame thrower", "flamethrower", "ultimate flamethrower", "zombie soaker",
		"ultimate zombie soaker", "gold digger", "ultimate gold digger i", "ultimate gold digger ii",
		"ultimate gold digger iii", "ultimate gold digger iv", "ultimate gold digger v", "the puncher", "practice gun",
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
		if (name.equals("pistol") || name.equals("ultimate pistol") || name.equals("practice gun")) {
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

	public static OptionalDouble fireRateSeconds(ItemStack stack) {
		return loreSeconds(stack, FIRE_RATE_PATTERN);
	}

	public static OptionalDouble reloadSeconds(ItemStack stack) {
		return loreSeconds(stack, RELOAD_PATTERN);
	}

	private static OptionalDouble loreSeconds(ItemStack stack, Pattern pattern) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return OptionalDouble.empty();
		for (var line : lore.lines()) {
			Matcher matcher = pattern.matcher(line.getString());
			if (!matcher.find()) continue;
			try {
				double seconds = Double.parseDouble(matcher.group(1).replace(',', '.'));
				if (Double.isFinite(seconds) && seconds >= 0.05 && seconds <= 10.0) {
					return OptionalDouble.of(seconds);
				}
			} catch (NumberFormatException ignored) {
				return OptionalDouble.empty();
			}
		}
		return OptionalDouble.empty();
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

package dev.luxnet.winter.client.debug;

import dev.luxnet.winter.Winter;
import dev.luxnet.winter.client.visuals.EspBoxRenderer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Manually captures cuboid block areas for later feature development. */
public final class AreaSelector {
	private static final List<SavedArea> SAVED_AREAS = new ArrayList<>();
	private static boolean enabled;
	private static BlockPos firstCorner;
	private static BlockPos secondCorner;
	private static String saveStatus = "";
	private static Path areasFile;

	private AreaSelector() { }

	public static void register() {
		areasFile = Minecraft.getInstance().gameDirectory.toPath().resolve("winter").resolve("areas.txt");
		load();
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!world.isClientSide() || !enabled || Minecraft.getInstance().screen != null) return InteractionResult.PASS;
			if (firstCorner == null) firstCorner = hit.getBlockPos().immutable();
			else if (secondCorner == null) secondCorner = hit.getBlockPos().immutable();
			return InteractionResult.SUCCESS;
		});
		WorldRenderEvents.END_MAIN.register(AreaSelector::render);
	}

	public static boolean isEnabled() { return enabled; }
	public static void toggle() {
		enabled = !enabled;
		if (!enabled) {
			firstCorner = null;
			secondCorner = null;
		}
	}
	public static BlockPos getFirstCorner() { return firstCorner; }
	public static BlockPos getSecondCorner() { return secondCorner; }
	public static int getSavedAreaCount() { return SAVED_AREAS.size(); }
	public static List<SavedArea> getSavedAreas() { return List.copyOf(SAVED_AREAS); }
	public static String getSaveStatus() { return saveStatus; }
	public static boolean canSave() { return firstCorner != null && secondCorner != null; }

	public static boolean saveCurrentArea() {
		if (!canSave()) {
			saveStatus = "Select two blocks first";
			return false;
		}
		SAVED_AREAS.add(new SavedArea(firstCorner, secondCorner));
		try {
			Files.createDirectories(areasFile.getParent());
			List<String> lines = SAVED_AREAS.stream().map(SavedArea::serialize).toList();
			Files.write(areasFile, lines);
			firstCorner = null;
			secondCorner = null;
			saveStatus = "Saved area " + SAVED_AREAS.size();
			return true;
		} catch (IOException exception) {
			SAVED_AREAS.remove(SAVED_AREAS.size() - 1);
			saveStatus = "Save failed";
			Winter.LOGGER.error("Could not save selected areas to {}", areasFile, exception);
			return false;
		}
	}

	private static void load() {
		if (!Files.isRegularFile(areasFile)) return;
		try {
			for (String line : Files.readAllLines(areasFile)) {
				try {
					SAVED_AREAS.add(SavedArea.deserialize(line));
				} catch (RuntimeException ignored) {
					Winter.LOGGER.warn("Skipping invalid area selector entry: {}", line);
				}
			}
		} catch (IOException exception) {
			Winter.LOGGER.error("Could not load selected areas from {}", areasFile, exception);
		}
	}

	private static void render(WorldRenderContext context) {
		if (!enabled) return;
		for (SavedArea area : SAVED_AREAS) drawArea(context, area, 0xA855BDEB, false);
		if (firstCorner != null && secondCorner == null) {
			EspBoxRenderer.draw(context, blockBox(firstCorner), 0xF8FFFFFF, 3.0F);
		} else if (canSave()) {
			SavedArea current = new SavedArea(firstCorner, secondCorner);
			drawArea(context, current, 0xF875DFFF, true);
		}
	}

	private static void drawArea(WorldRenderContext context, SavedArea area, int color, boolean showCorners) {
		BlockPos min = area.min();
		BlockPos max = area.max();
		AABB bounds = new AABB(min.getX(), min.getY(), min.getZ(),
			max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
		EspBoxRenderer.draw(context, bounds, color, showCorners ? 3.0F : 1.5F);
		if (showCorners) {
			EspBoxRenderer.draw(context, blockBox(area.first()), 0xFFFFFFFF, 3.0F);
			EspBoxRenderer.draw(context, blockBox(area.second()), 0xFFFFFFFF, 3.0F);
		}
	}

	private static AABB blockBox(BlockPos pos) {
		return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
	}

	public record SavedArea(BlockPos first, BlockPos second) {
		public BlockPos min() {
			return new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
				Math.min(first.getZ(), second.getZ()));
		}
		public BlockPos max() {
			return new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()),
				Math.max(first.getZ(), second.getZ()));
		}
		private String serialize() {
			return first.getX() + " " + first.getY() + " " + first.getZ() + " "
				+ second.getX() + " " + second.getY() + " " + second.getZ();
		}
		private static SavedArea deserialize(String line) {
			String[] values = line.trim().split("\\s+");
			if (values.length != 6) throw new IllegalArgumentException("Expected six coordinates");
			return new SavedArea(new BlockPos(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2])),
				new BlockPos(Integer.parseInt(values[3]), Integer.parseInt(values[4]), Integer.parseInt(values[5])));
		}
	}
}

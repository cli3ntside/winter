package dev.luxnet.winter.client.visuals;

import dev.luxnet.winter.client.WinterConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Checks manually recorded Dead End windows and marks any incomplete oak-slab area. */
public final class BrokenWindowEsp {
	private static final int REFRESH_TICKS = 5;
	private static final int MAX_BLOCKS_PER_AREA = 128;
	private static final int BROKEN_COLOR = 0xF0FF3838;
	private static final List<WindowArea> DEAD_END_AREAS = List.of(
		window(10, 71, 40, 10, 70, 38),
		window(10, 71, 28, 10, 70, 26),
		window(35, 72, 14, 37, 71, 14),
		window(37, 72, 24, 37, 71, 26),
		window(39, 78, 34, 41, 77, 34),
		window(46, 78, 34, 48, 77, 34),
		window(53, 78, 58, 51, 77, 58),
		window(25, 78, 72, 25, 77, 70),
		window(21, 78, 63, 19, 77, 63),
		window(27, 78, 49, 27, 77, 51),
		window(-3, 70, 37, -5, 69, 37),
		window(-8, 70, 18, -6, 69, 18),
		window(25, 71, 9, 25, 70, 11),
		window(22, 71, -2, 22, 70, 0),
		window(30, 70, -14, 30, 69, -12),
		window(4, 70, -12, 4, 69, -14),
		window(26, 70, -29, 28, 69, -29),
		window(-1, 78, -16, 1, 77, -16),
		window(20, 78, -2, 20, 77, 0),
		window(14, 78, 5, 14, 77, 7),
		window(-12, 78, 22, -12, 77, 20),
		window(-10, 78, 46, -10, 77, 44),
		window(3, 78, 59, 1, 77, 59),
		window(1, 85, 36, 3, 84, 36),
		window(-1, 85, 59, -3, 84, 59),
		window(-9, 85, 59, -11, 84, 59),
		window(-19, 85, 58, -21, 84, 58),
		window(-19, 85, 34, -18, 84, 34),
		window(-46, 85, 33, -46, 84, 32),
		window(-33, 85, 29, -32, 84, 29)
	);
	private static final List<WindowArea> ARCADE_AREAS = List.of(
		window(1, 74, 29, -1, 73, 29),
		window(-15, 74, 18, -15, 73, 16),
		window(-15, 74, 8, -15, 73, 6),
		window(-7, 74, -2, -7, 73, -4),
		window(15, 74, 9, 15, 73, 11),
		window(24, 75, -10, 26, 74, -10),
		window(29, 75, 0, 29, 74, 2),
		window(23, 74, 26, 23, 73, 28),
		window(15, 74, 40, 13, 73, 40),
		window(-25, 74, 25, -25, 73, 23),
		window(-15, 74, 35, -17, 73, 35)
	);
	private static final List<AABB> BROKEN_WINDOWS = new ArrayList<>();
	private static boolean enabled;
	private static Map selectedMap = Map.DEAD_END;
	private static int ticksSinceScan;
	private static Object scannedLevel;

	private BrokenWindowEsp() { }

	public static void registerWorldRender() {
		WorldRenderEvents.END_MAIN.register(BrokenWindowEsp::render);
	}

	public static boolean isEnabled() { return enabled; }
	public static String getSelectedMapName() { return selectedMap.displayName; }
	public static void toggle() { setEnabled(!enabled); }
	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		if (!enabled) BROKEN_WINDOWS.clear();
		WinterConfig.save();
	}
	public static void cycleMap() {
		Map[] maps = Map.values();
		selectedMap = maps[(selectedMap.ordinal() + 1) % maps.length];
		WinterConfig.save();
		BROKEN_WINDOWS.clear();
		scannedLevel = null;
	}
	public static void setSelectedMap(String id) {
		for (Map map : Map.values()) {
			if (map.id.equalsIgnoreCase(id)) {
				selectedMap = map;
				return;
			}
		}
	}
	public static String getSelectedMapId() { return selectedMap.id; }

	public static void tick(Minecraft minecraft) {
		if (!enabled || minecraft.level == null || minecraft.player == null || !TeamEsp.isZombiesGame(minecraft)) {
			BROKEN_WINDOWS.clear();
			scannedLevel = null;
			return;
		}
		if (scannedLevel != minecraft.level || ++ticksSinceScan >= REFRESH_TICKS) {
			scannedLevel = minecraft.level;
			ticksSinceScan = 0;
			scan(minecraft.level);
		}
	}

	private static void scan(Level level) {
		BROKEN_WINDOWS.clear();
		for (WindowArea area : areasForSelectedMap()) {
			BlockPos min = area.min();
			BlockPos max = area.max();
			long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1)
				* (max.getZ() - min.getZ() + 1);
			if (volume <= 0 || volume > MAX_BLOCKS_PER_AREA) continue;
			boolean loaded = true;
			boolean complete = true;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int y = min.getY(); y <= max.getY() && loaded && complete; y++) {
				for (int x = min.getX(); x <= max.getX() && loaded && complete; x++) {
					for (int z = min.getZ(); z <= max.getZ(); z++) {
						pos.set(x, y, z);
						if (!level.hasChunkAt(pos)) {
							loaded = false;
							break;
						}
						if (!level.getBlockState(pos).is(Blocks.OAK_SLAB)) {
							complete = false;
							break;
						}
					}
				}
			}
			if (loaded && !complete) {
				BROKEN_WINDOWS.add(new AABB(min.getX(), min.getY(), min.getZ(),
					max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0));
			}
		}
	}

	private static List<WindowArea> areasForSelectedMap() {
		return switch (selectedMap) {
			case DEAD_END -> DEAD_END_AREAS;
			case ARCADE -> ARCADE_AREAS;
		};
	}

	private static WindowArea window(int x1, int y1, int z1, int x2, int y2, int z2) {
		return new WindowArea(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2));
	}

	private static void render(WorldRenderContext context) {
		if (!enabled || BROKEN_WINDOWS.isEmpty()) return;
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		for (AABB window : BROKEN_WINDOWS) {
			if (window.getCenter().distanceToSqr(camera) > 256.0 * 256.0) continue;
			EspBoxRenderer.draw(context, window, BROKEN_COLOR, 2.5F);
		}
	}

	private enum Map {
		DEAD_END("deadend", "Dead End"),
		ARCADE("arcade", "Arcade");

		private final String id;
		private final String displayName;

		Map(String id, String displayName) {
			this.id = id;
			this.displayName = displayName;
		}
	}

	private record WindowArea(BlockPos first, BlockPos second) {
		private BlockPos min() {
			return new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
				Math.min(first.getZ(), second.getZ()));
		}
		private BlockPos max() {
			return new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()),
				Math.max(first.getZ(), second.getZ()));
		}
	}
}

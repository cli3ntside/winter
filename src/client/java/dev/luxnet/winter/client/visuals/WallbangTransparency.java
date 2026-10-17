package dev.luxnet.winter.client.visuals;

import dev.luxnet.winter.client.WinterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Hides selected partial blocks in the local renderer so entities behind them remain visible. */
public final class WallbangTransparency {
	private static boolean enabled;

	private WallbangTransparency() { }

	public static boolean isEnabled() { return enabled; }

	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.levelRenderer != null) minecraft.levelRenderer.allChanged();
		WinterConfig.save();
	}

	public static void toggle() { setEnabled(!enabled); }

	public static boolean shouldHide(BlockState state) {
		if (!enabled) return false;
		var block = state.getBlock();
		return block instanceof SlabBlock || block instanceof StairBlock || block instanceof IronBarsBlock;
	}
}

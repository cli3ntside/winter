package dev.luxnet.winter.client.mixin;

import dev.luxnet.winter.client.HudEditorScreen;
import dev.luxnet.winter.client.WinterHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class HudOverlayMixin {
	@Unique private boolean winter$scoreboardTransform;
	@Unique private boolean winter$bossbarTransform;

	@Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
		at = @At("HEAD"), cancellable = true)
	private void winter$positionScoreboard(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!WinterHud.isScoreboardEnabled() || net.minecraft.client.Minecraft.getInstance().screen instanceof HudEditorScreen) {
			ci.cancel();
			return;
		}
		WinterHud.beginNativeElement(graphics, WinterHud.SCOREBOARD);
		winter$scoreboardTransform = true;
	}

	@Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
		at = @At("RETURN"))
	private void winter$restoreScoreboard(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (winter$scoreboardTransform) {
			WinterHud.endNativeElement(graphics, WinterHud.SCOREBOARD);
			winter$scoreboardTransform = false;
		}
	}

	@Inject(method = "renderBossOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
		at = @At("HEAD"), cancellable = true)
	private void winter$positionBossbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!WinterHud.isBossbarEnabled() || net.minecraft.client.Minecraft.getInstance().screen instanceof HudEditorScreen) {
			ci.cancel();
			return;
		}
		WinterHud.beginNativeElement(graphics, WinterHud.BOSSBAR);
		winter$bossbarTransform = true;
	}

	@Inject(method = "renderBossOverlay(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
		at = @At("RETURN"))
	private void winter$restoreBossbar(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (winter$bossbarTransform) {
			WinterHud.endNativeElement(graphics, WinterHud.BOSSBAR);
			winter$bossbarTransform = false;
		}
	}
}

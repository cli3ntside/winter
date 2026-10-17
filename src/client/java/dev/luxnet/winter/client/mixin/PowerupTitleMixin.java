package dev.luxnet.winter.client.mixin;

import dev.luxnet.winter.client.PowerupTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class PowerupTitleMixin {
	@Inject(method = "setTitle", at = @At("HEAD"))
	private void winter$readPowerupRound(Component title, CallbackInfo callback) {
		PowerupTracker.onTitle(title.getString());
	}
}

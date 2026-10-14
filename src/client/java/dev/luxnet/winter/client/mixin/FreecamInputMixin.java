package dev.luxnet.winter.client.mixin;

import dev.luxnet.winter.client.assistance.Freecam;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class FreecamInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("TAIL"))
	private void winter$freezePlayerInput(CallbackInfo ci) {
		if (!Freecam.isEnabled()) return;
		keyPresses = Input.EMPTY;
		moveVector = Vec2.ZERO;
	}
}

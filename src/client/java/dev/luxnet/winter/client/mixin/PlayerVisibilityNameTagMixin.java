package dev.luxnet.winter.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.luxnet.winter.client.visuals.PlayerVisibility;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerVisibilityNameTagMixin {
	@Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
	private void winter$hideNearbyPlayerName(AvatarRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		if (PlayerVisibility.shouldHide(state)) ci.cancel();
	}
}

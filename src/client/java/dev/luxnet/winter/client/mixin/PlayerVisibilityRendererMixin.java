package dev.luxnet.winter.client.mixin;

import dev.luxnet.winter.client.visuals.PlayerVisibility;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerVisibilityRendererMixin<S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
	@Shadow @Final protected List<RenderLayer<S, M>> layers;
	private List<RenderLayer<S, M>> winter$originalLayers;

	@Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
	private void winter$hideNearbyPlayerModel(S state, boolean bodyVisible, boolean translucent, boolean glowing,
			CallbackInfoReturnable<RenderType> cir) {
		if ((Object) this instanceof AvatarRenderer<?> && state instanceof AvatarRenderState avatar
			&& PlayerVisibility.shouldHide(avatar)) {
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "submit", at = @At("HEAD"))
	private void winter$keepOnlyHeldItemLayer(S state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera, CallbackInfo ci) {
		if (!((Object) this instanceof AvatarRenderer<?>) || !(state instanceof AvatarRenderState avatar)
			|| !PlayerVisibility.shouldHide(avatar)) return;
		winter$originalLayers = new ArrayList<>(layers);
		List<RenderLayer<S, M>> heldItemLayers = new ArrayList<>();
		for (RenderLayer<S, M> layer : layers) {
			if (layer instanceof PlayerItemInHandLayer<?, ?>) heldItemLayers.add(layer);
		}
		layers.clear();
		layers.addAll(heldItemLayers);
	}

	@Inject(method = "submit", at = @At("TAIL"))
	private void winter$restorePlayerLayers(S state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera, CallbackInfo ci) {
		if (winter$originalLayers != null) {
			layers.clear();
			layers.addAll(winter$originalLayers);
			winter$originalLayers = null;
		}
	}
}

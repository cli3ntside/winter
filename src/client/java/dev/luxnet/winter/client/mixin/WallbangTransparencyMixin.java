package dev.luxnet.winter.client.mixin;

import dev.luxnet.winter.client.visuals.WallbangTransparency;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase")
public abstract class WallbangTransparencyMixin {
	@Inject(method = "getRenderShape", at = @At("RETURN"), cancellable = true)
	private void winter$hidePartialBlocks(CallbackInfoReturnable<RenderShape> cir) {
		if (WallbangTransparency.shouldHide((BlockState) (Object) this)) {
			cir.setReturnValue(RenderShape.INVISIBLE);
		}
	}
}

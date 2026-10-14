package dev.luxnet.winter.client.mixin;

import dev.luxnet.winter.client.assistance.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class FreecamCameraMixin {
	@Invoker("setRotation") protected abstract void winter$setRotation(float yaw, float pitch);
	@Invoker("setPosition") protected abstract void winter$setPosition(double x, double y, double z);
	@Accessor("detached") protected abstract void winter$setDetached(boolean detached);

	@Inject(method = "setup", at = @At("TAIL"))
	private void winter$applyFreecam(Level level, Entity entity, boolean detached, boolean mirrored, float tickDelta, CallbackInfo ci) {
		if (!Freecam.isEnabled()) return;
		winter$setPosition(Freecam.x(tickDelta), Freecam.y(tickDelta), Freecam.z(tickDelta));
		winter$setRotation(Freecam.yaw(tickDelta), Freecam.pitch(tickDelta));
		winter$setDetached(true);
	}
}

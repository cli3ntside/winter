package dev.luxnet.winter.client.mixin;

import dev.luxnet.winter.client.assistance.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.CameraType;
import net.minecraft.util.SmoothDouble;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class FreecamMouseMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow private double accumulatedDX;
	@Shadow private double accumulatedDY;
	@Shadow @Final private SmoothDouble smoothTurnX;
	@Shadow @Final private SmoothDouble smoothTurnY;

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void winter$rotateFreecam(double frameTime, CallbackInfo ci) {
		if (!Freecam.isEnabled() || minecraft.options == null) return;

		double sensitivity = minecraft.options.sensitivity().get().doubleValue() * 0.6 + 0.2;
		double sensitivityCubed = sensitivity * sensitivity * sensitivity;
		double deltaYaw;
		double deltaPitch;
		if (minecraft.options.smoothCamera) {
			double smoothScale = sensitivityCubed * 8.0;
			deltaYaw = smoothTurnX.getNewDeltaValue(accumulatedDX * smoothScale, frameTime * smoothScale);
			deltaPitch = smoothTurnY.getNewDeltaValue(accumulatedDY * smoothScale, frameTime * smoothScale);
		} else {
			double scale = minecraft.options.getCameraType().isFirstPerson()
				&& minecraft.player != null && minecraft.player.isScoping()
				? sensitivityCubed : sensitivityCubed * 8.0;
			deltaYaw = accumulatedDX * scale;
			deltaPitch = accumulatedDY * scale;
		}
		if (minecraft.options.invertMouseX().get()) deltaYaw = -deltaYaw;
		if (minecraft.options.invertMouseY().get()) deltaPitch = -deltaPitch;
		Freecam.changeLookDirection(deltaYaw, deltaPitch);
		ci.cancel();
	}
}

package dev.luxnet.winter.client.mixin;

import dev.luxnet.winter.client.debug.ShotDebugModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ShotTracePacketMixin {
	@Inject(method = "handleSoundEvent", at = @At("TAIL"))
	private void winter$captureSound(ClientboundSoundPacket packet, CallbackInfo info) {
		ShotDebugModule.recordSound(packet);
	}

	@Inject(method = "handleSoundEntityEvent", at = @At("TAIL"))
	private void winter$captureEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo info) {
		ShotDebugModule.recordSoundEntity(packet);
	}

	@Inject(method = "handleParticleEvent", at = @At("TAIL"))
	private void winter$captureParticle(ClientboundLevelParticlesPacket packet, CallbackInfo info) {
		ShotDebugModule.recordParticle(packet);
	}

	@Inject(method = "handleSystemChat", at = @At("TAIL"))
	private void winter$captureServerAction(ClientboundSystemChatPacket packet, CallbackInfo info) {
		ShotDebugModule.recordServerMessage(packet);
	}
}

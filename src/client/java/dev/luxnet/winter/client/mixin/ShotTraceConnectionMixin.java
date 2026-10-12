package dev.luxnet.winter.client.mixin;

import dev.luxnet.winter.client.debug.ShotDebugModule;
import dev.luxnet.winter.client.visuals.BulletTracers;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ShotTraceConnectionMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void winter$captureOutgoingPacket(Packet<?> packet, CallbackInfo info) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getConnection() != null && minecraft.getConnection() == ((Connection) (Object) this).getPacketListener()) {
			ShotDebugModule.recordOutgoing(packet);
			BulletTracers.onOutgoingPacket(packet);
		}
	}
}

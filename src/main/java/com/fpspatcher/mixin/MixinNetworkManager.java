package com.fpspatcher.mixin;

import com.fpspatcher.isolation.PacketIsolation;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkManager.class, priority = 50)
@SuppressWarnings("rawtypes")
public abstract class MixinNetworkManager {
    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void fpsPatcher$isolateSend(Packet packet, CallbackInfo ci) {
        if (PacketIsolation.shouldCancel(packet)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "sendPacket(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;[Lio/netty/util/concurrent/GenericFutureListener;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fpsPatcher$isolateSendListeners(Packet packet, GenericFutureListener listener,
                                                 GenericFutureListener[] listeners, CallbackInfo ci) {
        if (PacketIsolation.shouldCancel(packet)) {
            ci.cancel();
        }
    }
}

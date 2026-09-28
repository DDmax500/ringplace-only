package com.fpspatcher.mixin;

import com.fpspatcher.ring.RingPlace;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetHandlerPlayClient.class, priority = 50)
public abstract class MixinNetHandlerPlayClient {
    @Inject(method = "handleBlockChange", at = @At("RETURN"))
    private void fpsPatcher$ringBlock(S23PacketBlockChange packet, CallbackInfo ci) {
        if (packet == null) {
            return;
        }
        RingPlace.onServerBlock(packet.getBlockPosition(), packet.getBlockState());
    }

    @Inject(method = "handleMultiBlockChange", at = @At("RETURN"))
    private void fpsPatcher$ringBlocks(S22PacketMultiBlockChange packet, CallbackInfo ci) {
        if (packet == null || packet.getChangedBlocks() == null) {
            return;
        }
        S22PacketMultiBlockChange.BlockUpdateData[] blocks = packet.getChangedBlocks();
        for (int i = 0; i < blocks.length; i++) {
            S22PacketMultiBlockChange.BlockUpdateData update = blocks[i];
            if (update != null) {
                RingPlace.onServerBlock(update.getPos(), update.getBlockState());
            }
        }
    }
}

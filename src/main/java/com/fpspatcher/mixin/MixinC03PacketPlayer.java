package com.fpspatcher.mixin;

import net.minecraft.network.play.client.C03PacketPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(C03PacketPlayer.class)
public interface MixinC03PacketPlayer {
    @Accessor("yaw")
    void fpsPatcher$setYaw(float yaw);

    @Accessor("pitch")
    void fpsPatcher$setPitch(float pitch);
}

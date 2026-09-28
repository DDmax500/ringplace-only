package com.fpspatcher.isolation;

import com.fpspatcher.mixin.MixinC03PacketPlayer;
import com.fpspatcher.ring.RingPlace;
import com.fpspatcher.rotation.Rotations;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * Keep C03 yaw/pitch on the same look as C08. 1.8 sends the place first;
 * the following flying packet is what RotationPlace resimulates against.
 */
public final class PacketIsolation {
    private PacketIsolation() {
    }

    public static boolean shouldCancel(Packet packet) {
        if (packet instanceof C03PacketPlayer) {
            rewriteLook((C03PacketPlayer) packet);
        }
        return false;
    }

    private static void rewriteLook(C03PacketPlayer packet) {
        if (!packet.getRotating()) {
            return;
        }
        Rotations rotations = Rotations.get();
        if (!rotations.hasPacketLook()) {
            return;
        }
        if (!rotations.isControllingLook() && !RingPlace.wantsMoveFix() && !RingPlace.holdingPlaceLook()) {
            return;
        }
        MixinC03PacketPlayer access = (MixinC03PacketPlayer) packet;
        access.fpsPatcher$setYaw(rotations.getPacketYaw());
        access.fpsPatcher$setPitch(rotations.getPacketPitch());
    }
}

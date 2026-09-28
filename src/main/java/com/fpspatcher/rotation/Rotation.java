package com.fpspatcher.rotation;

import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/** Yaw/pitch pair plus look-at-point helper used by silent ring place. */
public final class Rotation {
    public final float yaw;
    public final float pitch;

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);
    }

    public static Rotation towards(Vec3 from, Vec3 to) {
        if (from == null || to == null) {
            return null;
        }
        double dx = to.xCoord - from.xCoord;
        double dy = to.yCoord - from.yCoord;
        double dz = to.zCoord - from.zCoord;
        double horizontal = MathHelper.sqrt_double(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(dy, horizontal) * 180.0D / Math.PI);
        return new Rotation(yaw, pitch);
    }
}

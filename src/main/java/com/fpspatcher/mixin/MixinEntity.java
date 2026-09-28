package com.fpspatcher.mixin;

import com.fpspatcher.rotation.Rotations;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Inject(method = "setAngles", at = @At("HEAD"), cancellable = true)
    private void fpsPatcher$silentCameraMouse(float yaw, float pitch, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (Rotations.get().consumeSilentMouse(yaw, pitch, self)) {
            ci.cancel();
        }
    }
}

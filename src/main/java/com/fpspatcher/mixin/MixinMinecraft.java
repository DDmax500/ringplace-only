package com.fpspatcher.mixin;

import com.fpspatcher.ring.RingPlace;
import com.fpspatcher.rotation.Rotations;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 50)
public abstract class MixinMinecraft {
    @Shadow
    private int rightClickDelayTimer;

    @Shadow
    private void rightClickMouse() {
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void fpsPatcher$beginTick(CallbackInfo ci) {
        RingPlace.afterFlying();
    }

    /**
     * Vanilla RMB uses the camera ray and a 4-tick delay. While ring place is
     * on, only our packet place runs, every tick, with the body look.
     */
    @Inject(method = "rightClickMouse", at = @At("HEAD"), cancellable = true)
    private void fpsPatcher$blockVanillaPlace(CallbackInfo ci) {
        if (RingPlace.isEnabled() && !RingPlace.skipClientWorld()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;sendClickBlockToController(Z)V"
            )
    )
    private void fpsPatcher$preWorldHit(CallbackInfo ci) {
        boolean alreadyPlaced = this.rightClickDelayTimer == 4;
        if (RingPlace.isEnabled() && !alreadyPlaced) {
            this.rightClickDelayTimer = 0;
        }
        Rotations.get().applyWalkingRotations();
        if (!alreadyPlaced && RingPlace.pumpPlace()) {
            RingPlace.beginPacketPlace();
            try {
                this.rightClickMouse();
            } finally {
                RingPlace.endPacketPlace();
            }
            RingPlace.placed();
        }
    }
}

package com.fpspatcher.mixin;

import com.fpspatcher.command.DotCommands;
import com.fpspatcher.ring.RingPlace;
import com.fpspatcher.rotation.Rotations;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityPlayerSP.class, priority = 50)
public abstract class MixinEntityPlayerSP {
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void fpsPatcher$dotCommands(String message, CallbackInfo ci) {
        if (message != null && message.startsWith(".") && DotCommands.handle(message)) {
            ci.cancel();
        }
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void fpsPatcher$preLiving(CallbackInfo ci) {
        Rotations.get().applyWalkingRotations();
        RingPlace.preLiving();
    }

    @Inject(
            method = "onLivingUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/MovementInput;updatePlayerMoveState()V",
                    shift = At.Shift.AFTER
            )
    )
    private void fpsPatcher$moveFix(CallbackInfo ci) {
        Rotations.get().applySilentMoveFix();
    }

    @Inject(method = "onUpdateWalkingPlayer", at = @At("HEAD"))
    private void fpsPatcher$preWalking(CallbackInfo ci) {
        Rotations.get().applyPacketLook();
    }

    @Inject(method = "onUpdateWalkingPlayer", at = @At("RETURN"))
    private void fpsPatcher$postWalking(CallbackInfo ci) {
        RingPlace.afterFlying();
    }

    @Inject(
            method = "onLivingUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;onLivingUpdate()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void fpsPatcher$moveFixRestore(CallbackInfo ci) {
        Rotations.get().restoreMoveInput();
    }

    @Inject(
            method = "onLivingUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/AbstractClientPlayer;onLivingUpdate()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void fpsPatcher$moveFixRestoreClient(CallbackInfo ci) {
        Rotations.get().restoreMoveInput();
    }
}

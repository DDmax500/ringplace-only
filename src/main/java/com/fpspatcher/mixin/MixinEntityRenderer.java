package com.fpspatcher.mixin;

import com.fpspatcher.rotation.Rotations;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {
    @Inject(
            method = "updateCameraAndRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/profiler/Profiler;endSection()V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void fpsPatcher$afterVanillaMouse(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Rotations.get().applyVanillaStep(partialTicks);
    }

    private boolean fpsPatcher$rayCamera;

    @Inject(method = "getMouseOver", at = @At("HEAD"))
    private void fpsPatcher$cameraRay(float partialTicks, CallbackInfo ci) {
        fpsPatcher$rayCamera = false;
        Rotations rotations = Rotations.get();
        if (rotations.isBodyRay() || rotations.isViewOverride()) {
            return;
        }
        rotations.applyCameraToPlayer();
        fpsPatcher$rayCamera = rotations.isViewOverride();
    }

    @Inject(method = "getMouseOver", at = @At("RETURN"))
    private void fpsPatcher$cameraRayRestore(float partialTicks, CallbackInfo ci) {
        if (!fpsPatcher$rayCamera) {
            return;
        }
        fpsPatcher$rayCamera = false;
        Rotations.get().restoreEntityLook();
    }

    @Inject(method = "setupCameraTransform", at = @At("HEAD"))
    private void fpsPatcher$cameraView(float partialTicks, int pass, CallbackInfo ci) {
        Rotations.get().applyCameraToPlayer();
    }

    @Inject(method = "setupCameraTransform", at = @At("RETURN"))
    private void fpsPatcher$entityView(float partialTicks, int pass, CallbackInfo ci) {
        Rotations.get().restoreEntityLook();
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void fpsPatcher$handCamera(float partialTicks, int xOffset, CallbackInfo ci) {
        Rotations.get().applyCameraToPlayer();
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void fpsPatcher$handEntity(float partialTicks, int xOffset, CallbackInfo ci) {
        Rotations.get().restoreEntityLook();
    }
}

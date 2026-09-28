package com.fpspatcher.rotation;

import com.fpspatcher.ring.RingPlace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Silent look for ring place: camera stays free, body/packets face the block.
 * Mouse only moves the camera while silent hold is active.
 */
public final class Rotations {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static Rotations instance;

    private float trackedYaw = Float.NaN;
    private float trackedPitch = Float.NaN;
    private float visualYaw = Float.NaN;
    private float visualPitch = Float.NaN;
    private float prevVisualYaw = Float.NaN;
    private float prevVisualPitch = Float.NaN;
    private boolean silentHoldActive;
    private boolean viewOverride;
    private int bodyRay;
    private float savedYaw;
    private float savedPitch;
    private float savedPrevYaw;
    private float savedPrevPitch;
    private boolean moveFixApplied;
    private float savedMoveForward;
    private float savedMoveStrafe;
    private boolean walkingRotationApplied;
    private boolean applyingTrackedStep;

    private Rotations() {
    }

    public static void init() {
        get();
    }

    public static Rotations get() {
        if (instance == null) {
            instance = new Rotations();
            MinecraftForge.EVENT_BUS.register(instance);
        }
        return instance;
    }

    public boolean isSilentHold() {
        return silentHoldActive;
    }

    public boolean isViewOverride() {
        return viewOverride;
    }

    public void beginBodyRay() {
        bodyRay++;
    }

    public void endBodyRay() {
        if (bodyRay > 0) {
            bodyRay--;
        }
    }

    public boolean isBodyRay() {
        return bodyRay > 0;
    }

    public boolean isControllingLook() {
        return RingPlace.wantsMoveFix() || silentHoldActive;
    }

    public float getCameraYaw() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            return 0.0F;
        }
        if (isSilentHold() && !Float.isNaN(visualYaw)) {
            return visualYaw;
        }
        return player.rotationYaw;
    }

    public boolean hasPacketLook() {
        return !Float.isNaN(trackedYaw) && !Float.isNaN(trackedPitch);
    }

    public float getPacketYaw() {
        EntityPlayerSP player = mc.thePlayer;
        if (hasPacketLook()) {
            return trackedYaw;
        }
        return player == null ? 0.0F : player.rotationYaw;
    }

    public float getPacketPitch() {
        EntityPlayerSP player = mc.thePlayer;
        if (hasPacketLook()) {
            return trackedPitch;
        }
        return player == null ? 0.0F : player.rotationPitch;
    }

    /**
     * Force the entity look used by {@code onUpdateWalkingPlayer} so C03
     * matches the C08 place look. 1.8 RotationPlace resimulates that flying.
     */
    public void applyPacketLook() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || !isControllingLook() || !hasPacketLook()) {
            return;
        }
        if (viewOverride) {
            restoreEntityLook();
        }
        player.rotationYaw = trackedYaw;
        player.rotationPitch = trackedPitch;
        player.rotationYawHead = trackedYaw;
    }

    public void beginSilentHold() {
        engageSilentHold(mc.thePlayer);
    }

    private void engageSilentHold(EntityPlayerSP player) {
        if (player == null) {
            return;
        }
        if (silentHoldActive) {
            return;
        }
        silentHoldActive = true;
        visualYaw = player.rotationYaw;
        visualPitch = player.rotationPitch;
        prevVisualYaw = player.prevRotationYaw;
        prevVisualPitch = player.prevRotationPitch;
        if (Float.isNaN(trackedYaw)) {
            trackedYaw = player.rotationYaw;
            trackedPitch = player.rotationPitch;
        }
    }

    public boolean consumeSilentMouse(float yaw, float pitch, Entity entity) {
        if (applyingTrackedStep) {
            return false;
        }
        if (!isSilentHold()) {
            return false;
        }
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || entity != player) {
            return false;
        }
        if (Float.isNaN(visualYaw)) {
            visualYaw = player.rotationYaw;
            visualPitch = player.rotationPitch;
            prevVisualYaw = player.prevRotationYaw;
            prevVisualPitch = player.prevRotationPitch;
        }
        float oldYaw = visualYaw;
        float oldPitch = visualPitch;
        visualYaw = (float) ((double) visualYaw + (double) yaw * 0.15D);
        visualPitch = MathHelper.clamp_float(
                (float) ((double) visualPitch - (double) pitch * 0.15D),
                -90.0F,
                90.0F
        );
        prevVisualYaw += visualYaw - oldYaw;
        prevVisualPitch += visualPitch - oldPitch;
        return true;
    }

    public void applyCameraToPlayer() {
        if (!isSilentHold() || viewOverride) {
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || Float.isNaN(visualYaw)) {
            return;
        }
        viewOverride = true;
        savedYaw = player.rotationYaw;
        savedPitch = player.rotationPitch;
        savedPrevYaw = player.prevRotationYaw;
        savedPrevPitch = player.prevRotationPitch;
        player.rotationYaw = visualYaw;
        player.rotationPitch = visualPitch;
        player.prevRotationYaw = Float.isNaN(prevVisualYaw) ? visualYaw : prevVisualYaw;
        player.prevRotationPitch = Float.isNaN(prevVisualPitch) ? visualPitch : prevVisualPitch;
    }

    public void restoreEntityLook() {
        if (!viewOverride) {
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        viewOverride = false;
        if (player == null) {
            return;
        }
        player.rotationYaw = savedYaw;
        player.rotationPitch = savedPitch;
        player.prevRotationYaw = savedPrevYaw;
        player.prevRotationPitch = savedPrevPitch;
    }

    public void disengageSilent() {
        EntityPlayerSP player = mc.thePlayer;
        if (viewOverride) {
            restoreEntityLook();
        }
        if (moveFixApplied) {
            restoreMoveInput();
        }
        if (silentHoldActive && player != null && !Float.isNaN(visualYaw)) {
            player.rotationYaw = visualYaw;
            player.rotationPitch = visualPitch;
            player.rotationYawHead = visualYaw;
        }
        silentHoldActive = false;
        trackedYaw = Float.NaN;
        trackedPitch = Float.NaN;
        visualYaw = Float.NaN;
        visualPitch = Float.NaN;
        prevVisualYaw = Float.NaN;
        prevVisualPitch = Float.NaN;
    }

    private void alignMoveYawToPacket(EntityPlayerSP player) {
        if (player == null || !hasPacketLook()) {
            return;
        }
        if (viewOverride) {
            restoreEntityLook();
        }
        player.rotationYaw = trackedYaw;
        player.rotationPitch = trackedPitch;
        player.rotationYawHead = trackedYaw;
    }

    private void applyVanillaMoveFix(EntityPlayerSP player, float desiredYaw) {
        int forward = 0;
        int left = 0;
        if (mc.gameSettings != null) {
            if (mc.gameSettings.keyBindForward.isKeyDown()) {
                forward++;
            }
            if (mc.gameSettings.keyBindBack.isKeyDown()) {
                forward--;
            }
            if (mc.gameSettings.keyBindLeft.isKeyDown()) {
                left++;
            }
            if (mc.gameSettings.keyBindRight.isKeyDown()) {
                left--;
            }
        }
        if (forward == 0 && left == 0) {
            return;
        }
        savedMoveForward = player.movementInput.moveForward;
        savedMoveStrafe = player.movementInput.moveStrafe;
        moveFixApplied = true;

        float desired = desiredYaw + (float) Math.toDegrees(Math.atan2((double) -left, (double) forward));
        float rel = MathHelper.wrapAngleTo180_float(desired - player.rotationYaw);
        int snapped = Math.round(rel / 45.0F) * 45;
        int f = 0;
        int s = 0;
        if (snapped == 0) {
            f = 1;
        } else if (snapped == 45) {
            f = 1;
            s = -1;
        } else if (snapped == 90) {
            s = -1;
        } else if (snapped == 135) {
            f = -1;
            s = -1;
        } else if (snapped == 180 || snapped == -180) {
            f = -1;
        } else if (snapped == -45) {
            f = 1;
            s = 1;
        } else if (snapped == -90) {
            s = 1;
        } else if (snapped == -135) {
            f = -1;
            s = 1;
        }
        player.movementInput.moveForward = (float) f;
        player.movementInput.moveStrafe = (float) s;
        if (player.movementInput.sneak) {
            player.movementInput.moveForward = (float) ((double) player.movementInput.moveForward * 0.3D);
            player.movementInput.moveStrafe = (float) ((double) player.movementInput.moveStrafe * 0.3D);
        }
    }

    public void applySilentMoveFix() {
        if (moveFixApplied) {
            return;
        }
        boolean place = RingPlace.wantsMoveFix();
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.movementInput == null) {
            return;
        }
        if (place) {
            engageSilentHold(player);
        }
        if (!place && !isSilentHold()) {
            return;
        }
        if (hasPacketLook()) {
            alignMoveYawToPacket(player);
        }
        applyVanillaMoveFix(player, getCameraYaw());
    }

    public void restoreMoveInput() {
        if (!moveFixApplied) {
            return;
        }
        moveFixApplied = false;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.movementInput == null) {
            return;
        }
        player.movementInput.moveForward = savedMoveForward;
        player.movementInput.moveStrafe = savedMoveStrafe;
    }

    public void applyWalkingRotations() {
        if (walkingRotationApplied) {
            return;
        }
        walkingRotationApplied = true;
    }

    /**
     * Body and placement ray turn toward this angle using vanilla mouse GCD
     * pixels. The camera stays on the silent hold.
     */
    public void silentLook(float yaw, float pitch) {
        walkingRotationApplied = true;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            return;
        }
        if (viewOverride) {
            restoreEntityLook();
        }
        engageSilentHold(player);
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);
        float gcd = gcdStep();
        int maxPixels = Math.max(1, MathHelper.ceiling_float_int(360.0F / Math.max(0.05F, gcd)));
        applySweepGcdStep(yaw, pitch, maxPixels);
    }

    private void applySweepGcdStep(float destYaw, float destPitch, int maxPixels) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || maxPixels <= 0) {
            return;
        }
        destPitch = MathHelper.clamp_float(destPitch, -90.0F, 90.0F);
        float f = mouseF();
        float f1 = f * f * f * 8.0F;
        float anglePerPixel = f1 * 0.15F;
        if (anglePerPixel < 1.0E-4F) {
            return;
        }
        applyingTrackedStep = true;
        try {
            for (int i = 0; i < maxPixels; i++) {
                float yawDiff = MathHelper.wrapAngleTo180_float(destYaw - player.rotationYaw);
                float pitchDiff = destPitch - player.rotationPitch;
                int dx = 0;
                int dy = 0;
                if (Math.abs(yawDiff) > anglePerPixel * 0.5F) {
                    dx = yawDiff > 0.0F ? 1 : -1;
                }
                if (Math.abs(pitchDiff) > anglePerPixel * 0.5F) {
                    dy = pitchDiff < 0.0F ? 1 : -1;
                }
                if (dx == 0 && dy == 0) {
                    break;
                }
                player.setAngles((float) dx * f1, (float) dy * f1);
            }
        } finally {
            applyingTrackedStep = false;
        }
        trackedYaw = player.rotationYaw;
        trackedPitch = player.rotationPitch;
        player.rotationYawHead = trackedYaw;
    }

    public void applyVanillaStep(float partialTicks) {
        if (walkingRotationApplied || RingPlace.wantsMoveFix()) {
            return;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            walkingRotationApplied = false;
            if (isSilentHold() && !Float.isNaN(visualYaw)) {
                prevVisualYaw = visualYaw;
                prevVisualPitch = visualPitch;
            }
        }
    }

    private static float mouseF() {
        float sensitivity = mc.gameSettings == null ? 0.5F : mc.gameSettings.mouseSensitivity;
        return sensitivity * 0.6F + 0.2F;
    }

    private static float gcdStep() {
        float f = mouseF();
        float f1 = f * f * f * 8.0F;
        return f1 * 0.15F;
    }
}

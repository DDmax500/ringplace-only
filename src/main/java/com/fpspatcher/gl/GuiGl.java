package com.fpspatcher.gl;

import com.fpspatcher.FPSPatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;

/**
 * After RenderWorldLast, Minecraft still draws the first-person hand and the
 * next world frame. Forcing 2D GUI state here (depth off, cull off) washes
 * lighting and inflates enchant glint — until a GUI opens and skips our
 * overlays. Restore the world pass instead, and keep GlStateManager in sync
 * (no raw GL11 toggles that desync the cache).
 */
public final class GuiGl {
    private GuiGl() {
    }

    public static void init() {
        disableFastRender();
    }

    public static void restore() {
        GlStateManager.matrixMode(5890);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(5888);
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableRescaleNormal();
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(515);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glLineWidth(1.0F);
    }

    /**
     * OptiFine Fast Render shares one framebuffer for world and GUI. Custom
     * nametag/ESP GL then blanks Essential's 2D menu (player model still shows).
     */
    public static void disableFastRender() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return;
        }
        try {
            Field field = mc.gameSettings.getClass().getField("ofFastRender");
            if (field.getBoolean(mc.gameSettings)) {
                field.setBoolean(mc.gameSettings, false);
                FPSPatcher.LOGGER.info("Disabled OptiFine Fast Render (it blanks Essential menus)");
            }
        } catch (Throwable ignored) {
        }
    }
}

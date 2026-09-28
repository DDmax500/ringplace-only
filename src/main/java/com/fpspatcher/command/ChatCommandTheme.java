package com.fpspatcher.command;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;

/**
 * White/blue glimmering outline on the chat box when the line starts with {@code .}.
 */
public final class ChatCommandTheme {
    private ChatCommandTheme() {
    }

    public static boolean isCommandLine(String text) {
        return text != null && !text.isEmpty() && text.charAt(0) == '.';
    }

    public static void renderAfterText(GuiTextField field, int width, int height) {
        if (!active(field)) {
            return;
        }
        drawBorder(
                field.xPosition - 2,
                field.yPosition - 2,
                field.xPosition + width + 2,
                field.yPosition + height + 2
        );
    }

    public static void renderChatOutline(int left, int top, int right, int bottom, GuiTextField field) {
        if (field == null || !isCommandLine(field.getText())) {
            return;
        }
        drawBorder(left, top, right, bottom);
    }

    private static boolean active(GuiTextField field) {
        if (field == null || !field.getVisible() || !isCommandLine(field.getText())) {
            return false;
        }
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof GuiChat) {
            return true;
        }
        if (screen == null) {
            return false;
        }
        String name = screen.getClass().getName().toLowerCase();
        return name.indexOf("chat") >= 0;
    }

    private static void drawBorder(int left, int top, int right, int bottom) {
        float wave = 0.5F + 0.5F * MathHelper.sin(
                (System.currentTimeMillis() % 1600L) / 1600.0F * (float) Math.PI * 2.0F
        );
        int white = argb(255, 255, 255, 255);
        int blue = argb(255, 70, 150, 255);
        int topColor = lerpColor(white, blue, wave);
        int bottomColor = lerpColor(blue, white, wave);
        Gui.drawRect(left, top, right, top + 1, topColor);
        Gui.drawRect(left, bottom - 1, right, bottom, bottomColor);
        Gui.drawRect(left, top, left + 1, bottom, lerpColor(white, blue, 1.0F - wave));
        Gui.drawRect(right - 1, top, right, bottom, lerpColor(blue, white, 1.0F - wave));
        int span = Math.max(16, (right - left) / 5);
        int travel = right - left - span;
        if (travel > 0) {
            int x = left + (int) (travel * wave);
            Gui.drawRect(x, top, x + span, top + 1, white);
            int x2 = left + (int) (travel * (1.0F - wave));
            Gui.drawRect(x2, bottom - 1, x2 + span, bottom, white);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
    }

    private static int lerpColor(int a, int b, float t) {
        t = MathHelper.clamp_float(t, 0.0F, 1.0F);
        int ar = a >> 16 & 255;
        int ag = a >> 8 & 255;
        int ab = a & 255;
        int br = b >> 16 & 255;
        int bg = b >> 8 & 255;
        int bb = b & 255;
        return argb(255,
                ar + (int) ((br - ar) * t),
                ag + (int) ((bg - ag) * t),
                ab + (int) ((bb - ab) * t));
    }

    private static int argb(int a, int r, int g, int b) {
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }
}

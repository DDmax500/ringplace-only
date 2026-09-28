package com.fpspatcher.mixin;

import com.fpspatcher.command.ChatCommandTheme;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiChat.class, priority = 1100)
public abstract class MixinGuiChat extends GuiScreen {
    @Shadow
    protected GuiTextField inputField;

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void fpsPatcher$commandOutline(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.inputField == null) {
            return;
        }
        ChatCommandTheme.renderChatOutline(2, this.height - 14, this.width - 2, this.height - 2, this.inputField);
    }
}

package com.fpspatcher.mixin;

import com.fpspatcher.command.ChatCommandTheme;
import net.minecraft.client.gui.GuiTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiTextField.class)
public abstract class MixinGuiTextField {
    @Shadow
    private int width;
    @Shadow
    private int height;

    @Inject(method = "drawTextBox", at = @At("RETURN"))
    private void fpsPatcher$dotOutline(CallbackInfo ci) {
        ChatCommandTheme.renderAfterText((GuiTextField) (Object) this, this.width, this.height);
    }
}

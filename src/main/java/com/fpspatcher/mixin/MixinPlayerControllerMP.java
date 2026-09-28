package com.fpspatcher.mixin;

import com.fpspatcher.ring.RingPlace;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PlayerControllerMP.class, priority = 50)
public abstract class MixinPlayerControllerMP {
    /**
     * Ring place already sent C08. Skip the client-world set so a server
     * reject cannot flash a ghost block for a frame.
     */
    @Redirect(
            method = "onPlayerRightClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;onItemUse(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;FFF)Z"
            ),
            expect = 2
    )
    private boolean fpsPatcher$skipGhostPlace(ItemStack stack, EntityPlayer player, World world,
                                              BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (RingPlace.skipClientWorld()) {
            return true;
        }
        return stack.onItemUse(player, world, pos, side, hitX, hitY, hitZ);
    }
}

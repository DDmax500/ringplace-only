package com.fpspatcher.ring;

import com.fpspatcher.gl.GuiGl;
import com.fpspatcher.log.CoordLog;
import com.fpspatcher.rotation.Rotation;
import com.fpspatcher.rotation.Rotations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Aims with the real camera and right-clicks the face that fills the next
 * open cell. The player only has to stand in reach.
 */
public final class RingPlace {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final EnumFacing[] FACES = new EnumFacing[]{
            EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.DOWN
    };

    private static RingPlace instance;
    private static KeyBinding toggleKey;
    private static boolean enabled;
    private static boolean silent;
    private static Click armed;
    private static long missingUntil;
    /**
     * After a place, keep this tick's C03 on the same look as C08. 1.8 sends
     * the place before flying; RotationPlace uses that flying look.
     */
    private static boolean freezeLook;
    /**
     * True only while vanilla {@code rightClickMouse} is sending this tick's C08.
     * Client-side {@code onItemUse} is skipped so a rejected place cannot flash
     * a ghost block before the server reply.
     */
    private static boolean packetPlace;
    private static int savedSlot = -1;

    private final Map<BlockPos, Integer> fails = new HashMap<BlockPos, Integer>();
    private final Map<BlockPos, Integer> pending = new HashMap<BlockPos, Integer>();

    private RingPlace() {
    }

    public static final class Click {
        public final BlockPos cell;
        public final BlockPos neighbor;
        public final EnumFacing face;
        public final Vec3 hit;

        Click(BlockPos cell, BlockPos neighbor, EnumFacing face, Vec3 hit) {
            this.cell = cell;
            this.neighbor = neighbor;
            this.face = face;
            this.hit = hit;
        }
    }

    public static void init() {
        if (instance != null) {
            return;
        }
        instance = new RingPlace();
        toggleKey = new KeyBinding("Ring place", Keyboard.KEY_NONE, "Ring Place");
        ClientRegistry.registerKeyBinding(toggleKey);
        MinecraftForge.EVENT_BUS.register(instance);
        RingPlan.init();
    }

    public static KeyBinding getKeyBinding() {
        return toggleKey;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        armed = null;
        freezeLook = false;
        packetPlace = false;
        if (!value) {
            idle();
        }
        if (instance != null) {
            instance.fails.clear();
            instance.pending.clear();
        }
        if (value) {
            RingPlan.reloadSaved();
            int saved = RingPlan.savedCount();
            int open = RingPlan.savedOpenCells().size();
            int have = RingPlan.hotbarCount();
            tell("ring place ON  " + EnumChatFormatting.YELLOW + saved
                    + EnumChatFormatting.RESET + " saved blocks  " + open + " to place  "
                    + have + " " + RingPlan.itemName());
            if (saved <= 0) {
                tell(EnumChatFormatting.GRAY + CoordLog.file().getAbsolutePath());
            }
            if (have <= 0) {
                tell("Put " + RingPlan.itemName() + " in your hotbar");
            }
        } else {
            tell("ring place OFF");
        }
    }

    public static boolean holdingPlaceLook() {
        return freezeLook;
    }

    public static void preLiving() {
        tickPending();
        if (freezeLook) {
            Rotations.get().beginSilentHold();
            silent = true;
            return;
        }
        if (!enabled || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            idle();
            return;
        }
        Click click = fromVanillaRay();
        if (click == null) {
            click = pick();
        }
        if (click == null) {
            idle();
            return;
        }
        rememberHeldSlot();
        if (!selectItem(true)) {
            idle();
            return;
        }
        Rotations.get().beginSilentHold();
        silent = true;
        arm(click);
    }

    public static boolean wantsMoveFix() {
        return enabled && silent;
    }

    public static void arm(Click click) {
        if (freezeLook) {
            return;
        }
        armed = click;
        if (click == null || mc.thePlayer == null) {
            return;
        }
        Rotation rotation = Rotation.towards(mc.thePlayer.getPositionEyes(1.0F), click.hit);
        if (rotation == null) {
            return;
        }
        Rotations.get().silentLook(rotation.yaw, rotation.pitch);
        silent = true;
    }

    public static boolean skipClientWorld() {
        return packetPlace;
    }

    public static void beginPacketPlace() {
        packetPlace = true;
    }

    public static void endPacketPlace() {
        packetPlace = false;
    }

    public static void onServerBlock(BlockPos pos, IBlockState state) {
        if (instance == null || pos == null) {
            return;
        }
        instance.pending.remove(pos);
    }

    public static boolean pumpPlace() {
        tickPending();
        if (!enabled) {
            return false;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.entityRenderer == null) {
            return false;
        }
        if (mc.currentScreen != null || mc.thePlayer.isUsingItem()) {
            return false;
        }
        Click click = pick();
        if (click == null) {
            return false;
        }
        rememberHeldSlot();
        if (!selectItem(false) || !RingPlan.holdsWanted(mc.thePlayer.getHeldItem())) {
            return false;
        }
        freezeLook = false;
        arm(click);
        Click ray = fromVanillaRay();
        if (ray == null) {
            return false;
        }
        armed = ray;
        freezeLook = true;
        return true;
    }

    /**
     * Vanilla crosshair on a ring cell. Same flags as Entity.rayTrace:
     * stopOnLiquid false, ignoreBlockWithoutBoundingBox false, returnLastUncollidable true.
     */
    private static Click fromVanillaRay() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.entityRenderer == null) {
            return null;
        }
        Rotations.get().beginBodyRay();
        try {
            mc.entityRenderer.getMouseOver(1.0F);
        } finally {
            Rotations.get().endBodyRay();
        }
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || mop.getBlockPos() == null || mop.sideHit == null || mop.hitVec == null) {
            return null;
        }
        BlockPos neighbor = mop.getBlockPos();
        EnumFacing face = mop.sideHit;
        if (!placeableSupport(neighbor)) {
            return null;
        }
        BlockPos cell = neighbor.offset(face);
        if (!RingPlan.replaceable(cell) || isPending(cell) || !planned(cell)
                || selfBlocksPlace(mc.thePlayer, cell) || blockedByOthers(cell)) {
            return null;
        }
        if (mc.thePlayer.getPositionEyes(1.0F).distanceTo(mop.hitVec) > (double) reach()) {
            return null;
        }
        return new Click(cell, neighbor, face, mop.hitVec);
    }

    private static boolean planned(BlockPos cell) {
        List<BlockPos> cells = placeCells();
        for (int i = 0; i < cells.size(); i++) {
            if (cell.equals(cells.get(i))) {
                return true;
            }
        }
        return false;
    }

    public static Click findClick(BlockPos cell) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null || cell == null || !RingPlan.replaceable(cell)
                || isPending(cell)) {
            return null;
        }
        if (selfBlocksPlace(player, cell) || blockedByOthers(cell)) {
            return null;
        }
        if (instance != null) {
            Integer fails = instance.fails.get(cell);
            if (fails != null && fails.intValue() > 45) {
                return null;
            }
        }
        Vec3 eyes = player.getPositionEyes(1.0F);
        float reach = reach();
        Click best = null;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < FACES.length; i++) {
            EnumFacing face = FACES[i];
            BlockPos neighbor = cell.offset(face.getOpposite());
            if (!placeableSupport(neighbor)) {
                continue;
            }
            Vec3 hit = faceHit(neighbor, face);
            if (hit == null) {
                continue;
            }
            if (eyes.distanceTo(hit) > (double) reach - 0.05D) {
                continue;
            }
            Vec3 into = new Vec3(
                    hit.xCoord - face.getFrontOffsetX() * 0.02D,
                    hit.yCoord - face.getFrontOffsetY() * 0.02D,
                    hit.zCoord - face.getFrontOffsetZ() * 0.02D
            );
            MovingObjectPosition traced = player.worldObj.rayTraceBlocks(eyes, into, false, false, true);
            if (traced == null || traced.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                    || traced.getBlockPos() == null || traced.sideHit != face
                    || !neighbor.equals(traced.getBlockPos())) {
                continue;
            }
            hit = traced.hitVec;
            double score = eyes.distanceTo(hit) + (face == EnumFacing.UP ? -0.35D : 0.0D);
            if (score < bestScore) {
                bestScore = score;
                best = new Click(cell, neighbor, face, hit);
            }
        }
        return best;
    }

    public static boolean selectItem(boolean complain) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            return false;
        }
        if (RingPlan.holdsWanted(player.getHeldItem())) {
            return true;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (!RingPlan.holdsWanted(stack)) {
                continue;
            }
            rememberHeldSlot();
            player.inventory.currentItem = slot;
            return true;
        }
        if (complain && System.currentTimeMillis() > missingUntil) {
            missingUntil = System.currentTimeMillis() + 2500L;
            tell("Put " + RingPlan.itemName() + " in your hotbar");
        }
        return false;
    }

    public static void placed() {
        freezeLook = true;
        if (armed != null && instance != null) {
            instance.fails.remove(armed.cell);
            int tick = mc.thePlayer == null ? 0 : mc.thePlayer.ticksExisted;
            instance.pending.put(armed.cell, Integer.valueOf(tick));
        }
        armed = null;
    }

    /** After this tick's C03, the place look no longer needs to be frozen. */
    public static void afterFlying() {
        freezeLook = false;
    }

    private static List<BlockPos> placeCells() {
        List<BlockPos> cells = RingPlan.usingCustom()
                ? RingPlan.openCells()
                : RingPlan.savedOpenCells();
        if (instance == null || instance.pending.isEmpty()) {
            return cells;
        }
        List<BlockPos> open = new ArrayList<BlockPos>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            BlockPos cell = cells.get(i);
            if (!isPending(cell)) {
                open.add(cell);
            }
        }
        return open;
    }

    private static Click pick() {
        List<BlockPos> cells = placeCells();
        Click best = null;
        double bestDist = Double.MAX_VALUE;
        EntityPlayerSP player = mc.thePlayer;
        Vec3 eyes = player.getPositionEyes(1.0F);
        for (int i = 0; i < cells.size(); i++) {
            BlockPos cell = cells.get(i);
            Click click = findClick(cell);
            if (click == null) {
                continue;
            }
            double dist = eyes.distanceTo(click.hit);
            if (occupies(player, cell) && !player.onGround) {
                dist -= 0.75D;
            }
            if (dist < bestDist) {
                best = click;
                bestDist = dist;
            }
        }
        return best;
    }

    private static boolean placeableSupport(BlockPos neighbor) {
        if (mc.theWorld == null || neighbor == null || isPending(neighbor)) {
            return false;
        }
        try {
            IBlockState state = mc.theWorld.getBlockState(neighbor);
            Block block = state.getBlock();
            Material material = block.getMaterial();
            if (material == null || material == Material.air || material.isLiquid() || material.isReplaceable()) {
                return false;
            }
            return supportBox(neighbor) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static Vec3 faceHit(BlockPos neighbor, EnumFacing face) {
        AxisAlignedBB box = supportBox(neighbor);
        if (box == null || face == null) {
            return null;
        }
        double x = (box.minX + box.maxX) * 0.5D;
        double y = (box.minY + box.maxY) * 0.5D;
        double z = (box.minZ + box.maxZ) * 0.5D;
        double inset = 0.01D;
        switch (face) {
            case UP:
                y = box.maxY - inset;
                break;
            case DOWN:
                y = box.minY + inset;
                break;
            case SOUTH:
                z = box.maxZ - inset;
                break;
            case NORTH:
                z = box.minZ + inset;
                break;
            case EAST:
                x = box.maxX - inset;
                break;
            case WEST:
                x = box.minX + inset;
                break;
            default:
                break;
        }
        return new Vec3(x, y, z);
    }

    private static AxisAlignedBB supportBox(BlockPos pos) {
        if (mc.theWorld == null || pos == null) {
            return null;
        }
        try {
            IBlockState state = mc.theWorld.getBlockState(pos);
            Block block = state.getBlock();
            block.setBlockBoundsBasedOnState(mc.theWorld, pos);
            AxisAlignedBB box = block.getCollisionBoundingBox(mc.theWorld, pos, state);
            if (box == null) {
                box = block.getSelectedBoundingBox(mc.theWorld, pos);
            }
            return box;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean selfBlocksPlace(EntityPlayerSP player, BlockPos cell) {
        if (player == null || cell == null) {
            return true;
        }
        if (!occupies(player, cell)) {
            return false;
        }
        return player.onGround;
    }

    private static boolean isPending(BlockPos pos) {
        return instance != null && pos != null && instance.pending.containsKey(pos);
    }

    private static void tickPending() {
        if (instance == null || instance.pending.isEmpty()) {
            return;
        }
        int now = mc.thePlayer == null ? 0 : mc.thePlayer.ticksExisted;
        Iterator<Map.Entry<BlockPos, Integer>> it = instance.pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = it.next();
            if (now - entry.getValue().intValue() > 40) {
                it.remove();
            }
        }
    }

    private static boolean occupies(EntityPlayerSP player, BlockPos cell) {
        AxisAlignedBB box = new AxisAlignedBB(cell.getX(), cell.getY(), cell.getZ(),
                cell.getX() + 1, cell.getY() + 1, cell.getZ() + 1);
        return player.getEntityBoundingBox().intersectsWith(box);
    }

    @SuppressWarnings("unchecked")
    private static boolean blockedByOthers(BlockPos cell) {
        if (mc.theWorld == null || mc.thePlayer == null || cell == null) {
            return false;
        }
        AxisAlignedBB box = new AxisAlignedBB(cell.getX(), cell.getY(), cell.getZ(),
                cell.getX() + 1.0D, cell.getY() + 1.0D, cell.getZ() + 1.0D);
        List<Entity> list = mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.thePlayer, box);
        for (int i = 0; i < list.size(); i++) {
            Entity entity = list.get(i);
            if (entity != null && !entity.isDead && entity.preventEntitySpawning) {
                return true;
            }
        }
        return false;
    }

    private static float reach() {
        if (mc.playerController == null) {
            return 4.5F;
        }
        return mc.playerController.getBlockReachDistance();
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.EXPERIENCE) {
            return;
        }
        if (!enabled || mc.thePlayer == null) {
            return;
        }
        String text = !silent
                ? "RING PLACE  idle"
                : "RING PLACE  " + placeCells().size() + "  " + RingPlan.itemName();
        mc.fontRendererObj.drawStringWithShadow(text, 4, 4, 0xFFFFFF);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!enabled) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        List<BlockPos> cells = placeCells();
        if (cells.isEmpty()) {
            return;
        }
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            view = mc.thePlayer;
        }
        float pt = event.partialTicks;
        double rx = view.lastTickPosX + (view.posX - view.lastTickPosX) * (double) pt;
        double ry = view.lastTickPosY + (view.posY - view.lastTickPosY) * (double) pt;
        double rz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * (double) pt;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GL11.glLineWidth(4.0F);
            Tessellator tess = Tessellator.getInstance();
            WorldRenderer wr = tess.getWorldRenderer();
            wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            for (int i = 0; i < cells.size(); i++) {
                BlockPos cell = cells.get(i);
                outline(wr, cell.getX() - rx, cell.getY() - ry, cell.getZ() - rz);
            }
            tess.draw();
        } finally {
            GlStateManager.popMatrix();
            GuiGl.restore();
        }
    }

    private static void outline(WorldRenderer wr, double x, double y, double z) {
        double pad = 0.002D;
        double x0 = x - pad;
        double y0 = y - pad;
        double z0 = z - pad;
        double x1 = x + 1.0D + pad;
        double y1 = y + 1.0D + pad;
        double z1 = z + 1.0D + pad;
        line(wr, x0, y0, z0, x1, y0, z0);
        line(wr, x1, y0, z0, x1, y0, z1);
        line(wr, x1, y0, z1, x0, y0, z1);
        line(wr, x0, y0, z1, x0, y0, z0);
        line(wr, x0, y1, z0, x1, y1, z0);
        line(wr, x1, y1, z0, x1, y1, z1);
        line(wr, x1, y1, z1, x0, y1, z1);
        line(wr, x0, y1, z1, x0, y1, z0);
        line(wr, x0, y0, z0, x0, y1, z0);
        line(wr, x1, y0, z0, x1, y1, z0);
        line(wr, x1, y0, z1, x1, y1, z1);
        line(wr, x0, y0, z1, x0, y1, z1);
    }

    private static void line(WorldRenderer wr, double x0, double y0, double z0, double x1, double y1, double z1) {
        wr.pos(x0, y0, z0).color(0, 0, 0, 255).endVertex();
        wr.pos(x1, y1, z1).color(0, 0, 0, 255).endVertex();
    }

    private static void idle() {
        armed = null;
        restoreHeldSlot();
        releaseSilent();
    }

    private static void rememberHeldSlot() {
        if (savedSlot < 0 && mc.thePlayer != null) {
            savedSlot = mc.thePlayer.inventory.currentItem;
        }
    }

    private static void restoreHeldSlot() {
        if (savedSlot < 0 || mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.inventory.currentItem = savedSlot;
        savedSlot = -1;
    }

    private static void releaseSilent() {
        if (!silent) {
            return;
        }
        silent = false;
        Rotations.get().disengageSilent();
    }

    private static void tell(String message) {
        if (mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.addChatMessage(new ChatComponentText(
                EnumChatFormatting.AQUA + "[RingPlace] " + EnumChatFormatting.RESET + message
        ));
    }
}

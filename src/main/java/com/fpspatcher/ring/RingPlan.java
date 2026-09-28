package com.fpspatcher.ring;

import com.fpspatcher.FPSPatcher;
import com.fpspatcher.log.CoordLog;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.config.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Footprint of the wall. The coordinate file is dirt the player looked at;
 * each of those columns becomes four obsidian blocks stacked on top of it.
 * Custom cells replace that plan until cleared.
 */
public final class RingPlan {
    public static final int PILLAR = 4;

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String CATEGORY = "ring";

    private static final List<BlockPos> customCells = new ArrayList<BlockPos>();
    private static final List<BlockPos> customBases = new ArrayList<BlockPos>();
    private static String itemName = "obsidian";
    private static boolean loading;
    private static int cacheTick = Integer.MIN_VALUE;
    private static List<Column> cache = Collections.emptyList();
    private static int savedCacheTick = Integer.MIN_VALUE;
    private static List<Column> savedCache = Collections.emptyList();

    private RingPlan() {
    }

    public static void init() {
        load();
    }

    public static final class Column {
        public final int x;
        public final int z;
        public final int[] ys;

        Column(int x, int z, int[] ys) {
            this.x = x;
            this.z = z;
            this.ys = ys;
        }

        public int minY() {
            return ys[0];
        }

        public boolean isLow(int y) {
            return y <= minY() + 1;
        }

        public BlockPos pos(int y) {
            return new BlockPos(x, y, z);
        }
    }

    public static boolean usingCustom() {
        return !customCells.isEmpty() || !customBases.isEmpty();
    }

    public static String itemName() {
        if ("stone".equals(itemName)) {
            return "stone/obsidian";
        }
        if ("obsidian".equals(itemName)) {
            return "obsidian/stone";
        }
        return itemName + "/stone";
    }

    public static void setItemName(String name) {
        if (name == null) {
            return;
        }
        String cleaned = name.trim().toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("minecraft:")) {
            cleaned = cleaned.substring("minecraft:".length());
        }
        if (cleaned.isEmpty()) {
            return;
        }
        Block block = resolveBlock(cleaned);
        if (block == null || block == Blocks.air) {
            tell("Unknown block " + cleaned);
            return;
        }
        itemName = cleaned;
        save();
        tell("place item  " + EnumChatFormatting.YELLOW + itemName);
    }

    public static void addCell(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!contains(customCells, pos)) {
            customCells.add(pos);
        }
        invalidate();
        tell("cell  " + EnumChatFormatting.YELLOW + x + " " + y + " " + z
                + EnumChatFormatting.RESET + "  —  " + customCells.size() + " custom cells");
    }

    public static void addBase(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!contains(customBases, pos)) {
            customBases.add(pos);
        }
        invalidate();
        tell("base  " + EnumChatFormatting.YELLOW + x + " " + y + " " + z
                + EnumChatFormatting.GRAY + "  →  4 above it");
    }

    public static boolean addLookCell() {
        BlockPos pos = lookedPlace();
        if (pos == null) {
            tell("Look at a block face to add the cell in front of it");
            return false;
        }
        addCell(pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    public static boolean addLookBase() {
        BlockPos pos = lookedBlock();
        if (pos == null) {
            tell("Look at the floor block the pillar should stand on");
            return false;
        }
        addBase(pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    public static void clearCustom() {
        int n = customCells.size() + customBases.size();
        customCells.clear();
        customBases.clear();
        invalidate();
        CoordLog.reload();
        tell("using the coordinate file  (" + columns().size() + " columns)");
        if (n == 0) {
            return;
        }
    }

    public static void useFile() {
        customCells.clear();
        customBases.clear();
        CoordLog.reload();
        invalidate();
        tell("file  " + columns().size() + " columns  " + EnumChatFormatting.GRAY + CoordLog.file().getName());
    }

    public static List<Column> columns() {
        EntityPlayerSP player = mc.thePlayer;
        int tick = player == null ? 0 : player.ticksExisted;
        if (cacheTick == tick && cache != null) {
            return cache;
        }
        cacheTick = tick;
        cache = build();
        return cache;
    }

    public static void reloadSaved() {
        CoordLog.reload();
        savedCacheTick = Integer.MIN_VALUE;
        if (!usingCustom()) {
            invalidate();
        }
    }

    public static int savedCount() {
        return CoordLog.count();
    }

    /**
     * Open pillar cells built only from {@code points.jsonl}, ignoring any
     * test coordinates added with {@code .ring add}.
     */
    public static List<BlockPos> savedOpenCells() {
        return openFrom(savedColumns());
    }

    public static List<BlockPos> openCells() {
        return openFrom(columns());
    }

    private static List<BlockPos> openFrom(List<Column> cols) {
        List<BlockPos> out = new ArrayList<BlockPos>();
        if (mc.theWorld == null) {
            return out;
        }
        for (int i = 0; i < cols.size(); i++) {
            Column column = cols.get(i);
            for (int j = 0; j < column.ys.length; j++) {
                BlockPos pos = column.pos(column.ys[j]);
                if (replaceable(pos)) {
                    out.add(pos);
                }
            }
        }
        return out;
    }

    private static List<Column> savedColumns() {
        EntityPlayerSP player = mc.thePlayer;
        int tick = player == null ? 0 : player.ticksExisted;
        if (savedCacheTick == tick && savedCache != null) {
            return savedCache;
        }
        savedCacheTick = tick;
        savedCache = columnsFromMarks(CoordLog.marks());
        return savedCache;
    }

    public static int openCount() {
        return openCells().size();
    }

    public static boolean replaceable(BlockPos pos) {
        if (mc.theWorld == null || pos == null) {
            return false;
        }
        try {
            return mc.theWorld.getBlockState(pos).getBlock().getMaterial().isReplaceable();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean solid(BlockPos pos) {
        if (mc.theWorld == null || pos == null) {
            return false;
        }
        try {
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            return block.getMaterial().isSolid() && !block.getMaterial().isReplaceable() && !block.getMaterial().isLiquid();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean passable(BlockPos pos) {
        if (mc.theWorld == null || pos == null) {
            return false;
        }
        try {
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            return !block.getMaterial().isLiquid() && (block.getMaterial().isReplaceable() || !block.getMaterial().blocksMovement());
        } catch (Exception e) {
            return false;
        }
    }

    public static Block wantedBlock() {
        Block block = resolveBlock(itemName);
        if (block == null || block == Blocks.air) {
            return Blocks.obsidian;
        }
        return block;
    }

    public static boolean holdsWanted(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        if (block == Blocks.stone || block == Blocks.obsidian) {
            return true;
        }
        return block == wantedBlock();
    }

    public static int hotbarCount() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (holdsWanted(stack)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    public static String statusLine() {
        String source = usingCustom() ? "custom" : "file";
        return source + "  " + columns().size() + " col  " + openCount() + " open  " + itemName;
    }

    public static void printList() {
        if (usingCustom()) {
            tell("custom cells " + customCells.size() + "   bases " + customBases.size());
            int shown = 0;
            for (int i = 0; i < customCells.size() && shown < 24; i++) {
                BlockPos pos = customCells.get(i);
                tell(EnumChatFormatting.YELLOW + "cell  " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                shown++;
            }
            for (int i = 0; i < customBases.size() && shown < 24; i++) {
                BlockPos pos = customBases.get(i);
                tell(EnumChatFormatting.YELLOW + "base  " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                shown++;
            }
            return;
        }
        List<Column> cols = columns();
        tell("file  " + cols.size() + " columns  " + openCount() + " still open");
        int shown = 0;
        for (int i = 0; i < cols.size() && shown < 12; i++) {
            Column column = cols.get(i);
            tell(EnumChatFormatting.YELLOW.toString() + column.x + " " + column.ys[0] + ".." + column.ys[column.ys.length - 1]
                    + " " + column.z);
            shown++;
        }
    }

    public static int indexOf(List<Column> cols, int x, int z) {
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i).x == x && cols.get(i).z == z) {
                return i;
            }
        }
        return -1;
    }

    private static List<Column> build() {
        Map<Long, List<Integer>> map = new LinkedHashMap<Long, List<Integer>>();
        if (usingCustom()) {
            for (int i = 0; i < customBases.size(); i++) {
                BlockPos base = customBases.get(i);
                for (int h = 1; h <= PILLAR; h++) {
                    addY(map, base.getX(), base.getZ(), base.getY() + h);
                }
            }
            for (int i = 0; i < customCells.size(); i++) {
                BlockPos cell = customCells.get(i);
                addY(map, cell.getX(), cell.getZ(), cell.getY());
            }
        } else {
            return columnsFromMarks(CoordLog.marks());
        }
        return finish(map);
    }

    private static List<Column> columnsFromMarks(List<int[]> marks) {
        Map<Long, List<Integer>> map = new LinkedHashMap<Long, List<Integer>>();
        for (int i = 0; i < marks.size(); i++) {
            int[] mark = marks.get(i);
            for (int h = 1; h <= PILLAR; h++) {
                addY(map, mark[0], mark[2], mark[1] + h);
            }
        }
        return finish(map);
    }

    private static List<Column> finish(Map<Long, List<Integer>> map) {
        List<Column> cols = new ArrayList<Column>();
        for (Map.Entry<Long, List<Integer>> entry : map.entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int z = (int) (entry.getKey() & 0xFFFFFFFFL);
            List<Integer> ys = entry.getValue();
            Collections.sort(ys);
            int[] raw = new int[ys.size()];
            for (int i = 0; i < ys.size(); i++) {
                raw[i] = ys.get(i).intValue();
            }
            cols.add(new Column(x, z, raw));
        }
        final double cx = centerX(cols);
        final double cz = centerZ(cols);
        Collections.sort(cols, new Comparator<Column>() {
            public int compare(Column a, Column b) {
                double aa = Math.atan2((a.z + 0.5D) - cz, (a.x + 0.5D) - cx);
                double bb = Math.atan2((b.z + 0.5D) - cz, (b.x + 0.5D) - cx);
                return Double.compare(aa, bb);
            }
        });
        return cols;
    }

    private static double centerX(List<Column> cols) {
        if (cols.isEmpty()) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (int i = 0; i < cols.size(); i++) {
            sum += cols.get(i).x + 0.5D;
        }
        return sum / (double) cols.size();
    }

    private static double centerZ(List<Column> cols) {
        if (cols.isEmpty()) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (int i = 0; i < cols.size(); i++) {
            sum += cols.get(i).z + 0.5D;
        }
        return sum / (double) cols.size();
    }

    private static void addY(Map<Long, List<Integer>> map, int x, int z, int y) {
        long key = (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
        List<Integer> ys = map.get(Long.valueOf(key));
        if (ys == null) {
            ys = new ArrayList<Integer>();
            map.put(Long.valueOf(key), ys);
        }
        Integer boxed = Integer.valueOf(y);
        if (!ys.contains(boxed)) {
            ys.add(boxed);
        }
    }

    private static boolean contains(List<BlockPos> list, BlockPos pos) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos lookedBlock() {
        MovingObjectPosition hit = lookHit();
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hit.getBlockPos() == null) {
            return null;
        }
        return hit.getBlockPos();
    }

    private static BlockPos lookedPlace() {
        MovingObjectPosition hit = lookHit();
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hit.getBlockPos() == null) {
            return null;
        }
        EnumFacing face = hit.sideHit == null ? EnumFacing.UP : hit.sideHit;
        return hit.getBlockPos().offset(face);
    }

    private static MovingObjectPosition lookHit() {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null) {
            return null;
        }
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = player.getLook(1.0F);
        Vec3 end = eyes.addVector(look.xCoord * 64.0D, look.yCoord * 64.0D, look.zCoord * 64.0D);
        return player.worldObj.rayTraceBlocks(eyes, end, false, false, false);
    }

    private static Block resolveBlock(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Block block = Block.getBlockFromName(name);
        if (block == null) {
            block = Block.getBlockFromName("minecraft:" + name);
        }
        return block;
    }

    private static void invalidate() {
        cacheTick = Integer.MIN_VALUE;
    }

    private static void load() {
        Configuration config = RingConfig.getConfig();
        if (config == null) {
            return;
        }
        loading = true;
        try {
            itemName = config.get(CATEGORY, "item", "obsidian").getString();
            if (itemName == null || itemName.trim().isEmpty()) {
                itemName = "obsidian";
            }
            itemName = itemName.trim().toLowerCase(Locale.ROOT);
            if (config.hasChanged()) {
                config.save();
            }
        } catch (Exception e) {
            FPSPatcher.LOGGER.error("Failed to load ring plan", e);
        } finally {
            loading = false;
        }
    }

    private static void save() {
        if (loading) {
            return;
        }
        Configuration config = RingConfig.getConfig();
        if (config == null) {
            return;
        }
        try {
            config.get(CATEGORY, "item", "obsidian").set(itemName);
            config.save();
        } catch (Exception e) {
            FPSPatcher.LOGGER.error("Failed to save ring plan", e);
        }
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

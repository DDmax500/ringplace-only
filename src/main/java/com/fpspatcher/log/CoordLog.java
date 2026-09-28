package com.fpspatcher.log;

import com.fpspatcher.FPSPatcher;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One keypress appends the block you are looking at to a coordinate file.
 * Used to mark ring positions before anything is built from them.
 */
public final class CoordLog {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final double LOOK_RANGE = 256.0D;

    private static CoordLog instance;
    private static KeyBinding key;

    private final List<Point> points = new ArrayList<Point>();
    private boolean loaded;
    private boolean toldPath;

    private CoordLog() {
    }

    public static void init() {
        if (instance != null) {
            return;
        }
        instance = new CoordLog();
        key = new KeyBinding("Coords (save block)", Keyboard.KEY_B, "Ring Place");
        ClientRegistry.registerKeyBinding(key);
    }

    public static KeyBinding getKeyBinding() {
        return key;
    }

    public static int count() {
        if (instance == null) {
            return 0;
        }
        instance.ensureLoaded();
        return instance.points.size();
    }

    public static void reload() {
        if (instance == null) {
            init();
        }
        instance.points.clear();
        instance.loaded = false;
        instance.ensureLoaded();
    }

    public static List<int[]> marks() {
        if (instance == null) {
            init();
        }
        instance.ensureLoaded();
        List<int[]> out = new ArrayList<int[]>();
        for (int i = 0; i < instance.points.size(); i++) {
            Point point = instance.points.get(i);
            out.add(new int[]{point.x, point.y, point.z});
        }
        return out;
    }

    public static File file() {
        return new File(mc.mcDataDir, "config/" + FPSPatcher.MODID + "/coords/points.jsonl");
    }

    public static void capture() {
        if (instance == null) {
            init();
        }
        instance.ensureLoaded();
        if (mc.thePlayer == null || mc.theWorld == null) {
            tell("Join a world first");
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        BlockPos feet = new BlockPos(player.posX, player.posY, player.posZ);
        MovingObjectPosition hit = lookHit(player);
        int x;
        int y;
        int z;
        String src;
        String side = "";
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && hit.getBlockPos() != null) {
            BlockPos pos = hit.getBlockPos();
            x = pos.getX();
            y = pos.getY();
            z = pos.getZ();
            src = "look";
            if (hit.sideHit != null) {
                side = hit.sideHit.getName();
            }
        } else {
            x = feet.getX();
            y = feet.getY();
            z = feet.getZ();
            src = "here";
        }
        if (!instance.points.isEmpty()) {
            Point last = instance.points.get(instance.points.size() - 1);
            if (last.x == x && last.y == y && last.z == z) {
                tell("already have " + EnumChatFormatting.YELLOW + "#" + instance.points.size()
                        + "  " + x + " " + y + " " + z
                        + EnumChatFormatting.GRAY + "  —  .coords undo");
                return;
            }
        }
        String block = blockName(new BlockPos(x, y, z));
        Point point = new Point(x, y, z, feet.getX(), feet.getY(), feet.getZ(), src, side, block,
                player.posX, player.posY, player.posZ);
        instance.points.add(point);
        if (!instance.save()) {
            instance.points.remove(instance.points.size() - 1);
            tell(EnumChatFormatting.RED + "Could not write the coordinate file");
            return;
        }
        String where = "look".equals(src) ? "look" : "here (not looking at a block)";
        tell(EnumChatFormatting.YELLOW + "#" + instance.points.size()
                + EnumChatFormatting.RESET + "  " + x + " " + y + " " + z
                + EnumChatFormatting.GRAY + "  " + where
                + (block.isEmpty() ? "" : "  " + block));
        if (!instance.toldPath) {
            instance.toldPath = true;
            tell(EnumChatFormatting.GRAY + file().getAbsolutePath());
        }
    }

    public static void undo() {
        if (instance == null) {
            init();
        }
        instance.ensureLoaded();
        if (instance.points.isEmpty()) {
            tell("No coordinates saved");
            return;
        }
        Point removed = instance.points.remove(instance.points.size() - 1);
        if (!instance.save()) {
            instance.points.add(removed);
            tell(EnumChatFormatting.RED + "Could not update the coordinate file");
            return;
        }
        tell("removed " + EnumChatFormatting.YELLOW + removed.x + " " + removed.y + " " + removed.z
                + EnumChatFormatting.RESET + "  —  " + instance.points.size() + " left");
    }

    public static void deleteLook() {
        if (instance == null) {
            init();
        }
        instance.ensureLoaded();
        if (mc.thePlayer == null || mc.theWorld == null) {
            tell("Join a world first");
            return;
        }
        MovingObjectPosition hit = lookHit(mc.thePlayer);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || hit.getBlockPos() == null) {
            tell("Look at the saved block");
            return;
        }
        BlockPos pos = hit.getBlockPos();
        deleteAt(pos.getX(), pos.getY(), pos.getZ(), true);
    }

    public static void deleteAt(int x, int y, int z, boolean column) {
        if (instance == null) {
            init();
        }
        instance.ensureLoaded();
        if (instance.points.isEmpty()) {
            tell("No coordinates saved");
            return;
        }
        List<Point> removed = takeAt(x, y, z, false);
        String how = "";
        if (removed.isEmpty() && column) {
            removed = takeAt(x, y, z, true);
            how = "  column";
        }
        if (removed.isEmpty()) {
            tell("No saved coord at " + EnumChatFormatting.YELLOW + x + " " + y + " " + z);
            return;
        }
        if (!instance.save()) {
            instance.points.addAll(removed);
            tell(EnumChatFormatting.RED + "Could not update the coordinate file");
            return;
        }
        Point first = removed.get(0);
        tell("removed " + EnumChatFormatting.YELLOW + first.x + " " + first.y + " " + first.z
                + EnumChatFormatting.RESET + how + "  —  " + instance.points.size() + " left");
    }

    private static List<Point> takeAt(int x, int y, int z, boolean column) {
        List<Point> removed = new ArrayList<Point>();
        for (int i = instance.points.size() - 1; i >= 0; i--) {
            Point point = instance.points.get(i);
            if (column) {
                if (point.x != x || point.z != z) {
                    continue;
                }
                if (y < point.y || y > point.y + 4) {
                    continue;
                }
            } else if (point.x != x || point.y != y || point.z != z) {
                continue;
            }
            removed.add(instance.points.remove(i));
        }
        return removed;
    }

    public static void clear() {
        if (instance == null) {
            init();
        }
        instance.ensureLoaded();
        int n = instance.points.size();
        List<Point> copy = new ArrayList<Point>(instance.points);
        instance.points.clear();
        if (!instance.save()) {
            instance.points.addAll(copy);
            tell(EnumChatFormatting.RED + "Could not clear the coordinate file");
            return;
        }
        tell("cleared " + EnumChatFormatting.YELLOW + n + EnumChatFormatting.RESET + " points");
    }

    public static void list() {
        if (instance == null) {
            init();
        }
        instance.ensureLoaded();
        if (instance.points.isEmpty()) {
            tell("No coordinates saved");
            tell(EnumChatFormatting.GRAY + file().getAbsolutePath());
            return;
        }
        for (int i = 0; i < instance.points.size(); i++) {
            Point point = instance.points.get(i);
            tell(EnumChatFormatting.YELLOW + "#" + (i + 1)
                    + EnumChatFormatting.RESET + "  " + point.x + " " + point.y + " " + point.z
                    + EnumChatFormatting.GRAY + "  " + point.src
                    + (point.block.isEmpty() ? "" : "  " + point.block));
        }
        tell(instance.points.size() + " points  " + EnumChatFormatting.GRAY + file().getAbsolutePath());
    }

    public static void path() {
        if (instance == null) {
            init();
        }
        instance.ensureLoaded();
        tell(instance.points.size() + " points  " + EnumChatFormatting.GRAY + file().getAbsolutePath());
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        File in = file();
        if (!in.isFile()) {
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(in), UTF8));
            String line;
            while ((line = reader.readLine()) != null) {
                Point point = parse(line);
                if (point != null) {
                    points.add(point);
                }
            }
        } catch (Exception e) {
            FPSPatcher.LOGGER.error("Failed to read coordinate log", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean save() {
        File out = file();
        File dir = out.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            FPSPatcher.LOGGER.error("Could not create coordinate folder {}", dir.getAbsolutePath());
            return false;
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), UTF8));
            for (int i = 0; i < points.size(); i++) {
                writer.write(points.get(i).toJson(i + 1));
                writer.newLine();
            }
            writer.flush();
            return true;
        } catch (Exception e) {
            FPSPatcher.LOGGER.error("Failed to write coordinate log", e);
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static MovingObjectPosition lookHit(EntityPlayerSP player) {
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 look = player.getLook(1.0F);
        Vec3 end = eyes.addVector(look.xCoord * LOOK_RANGE, look.yCoord * LOOK_RANGE, look.zCoord * LOOK_RANGE);
        return player.worldObj.rayTraceBlocks(eyes, end, false, false, false);
    }

    private static String blockName(BlockPos pos) {
        try {
            if (mc.theWorld == null) {
                return "";
            }
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (block == null) {
                return "";
            }
            Object name = Block.blockRegistry.getNameForObject(block);
            return name == null ? "" : name.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static Point parse(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        int x = fieldInt(line, "x");
        int y = fieldInt(line, "y");
        int z = fieldInt(line, "z");
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
            return null;
        }
        int fx = fieldInt(line, "fx");
        int fy = fieldInt(line, "fy");
        int fz = fieldInt(line, "fz");
        if (fx == Integer.MIN_VALUE) {
            fx = x;
            fy = y;
            fz = z;
        }
        return new Point(x, y, z, fx, fy, fz, fieldString(line, "src"), fieldString(line, "side"),
                fieldString(line, "block"), fieldDouble(line, "px"), fieldDouble(line, "py"), fieldDouble(line, "pz"));
    }

    private static int fieldInt(String line, String key) {
        String token = "\"" + key + "\":";
        int i = line.indexOf(token);
        if (i < 0) {
            return Integer.MIN_VALUE;
        }
        i += token.length();
        int end = i;
        if (end < line.length() && (line.charAt(end) == '-' || line.charAt(end) == '+')) {
            end++;
        }
        int start = end;
        while (end < line.length() && Character.isDigit(line.charAt(end))) {
            end++;
        }
        if (start == end) {
            return Integer.MIN_VALUE;
        }
        try {
            return Integer.parseInt(line.substring(i, end));
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static double fieldDouble(String line, String key) {
        String token = "\"" + key + "\":";
        int i = line.indexOf(token);
        if (i < 0) {
            return 0.0D;
        }
        i += token.length();
        int end = i;
        while (end < line.length()) {
            char c = line.charAt(end);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                end++;
                continue;
            }
            break;
        }
        if (end == i) {
            return 0.0D;
        }
        try {
            return Double.parseDouble(line.substring(i, end));
        } catch (NumberFormatException e) {
            return 0.0D;
        }
    }

    private static String fieldString(String line, String key) {
        String token = "\"" + key + "\":\"";
        int i = line.indexOf(token);
        if (i < 0) {
            return "";
        }
        i += token.length();
        StringBuilder out = new StringBuilder();
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                out.append(line.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '"') {
                break;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String n3(double v) {
        return String.format(Locale.US, "%.3f", Double.valueOf(v));
    }

    private static String esc(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"') {
                out.append('\\').append(c);
            } else if (c == '\n' || c == '\r' || c == '\t') {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static void tell(String message) {
        if (mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.addChatMessage(new ChatComponentText(
                EnumChatFormatting.AQUA + "[RingPlace] " + EnumChatFormatting.RESET + message
        ));
    }

    private static final class Point {
        final int x;
        final int y;
        final int z;
        final int fx;
        final int fy;
        final int fz;
        final String src;
        final String side;
        final String block;
        final double px;
        final double py;
        final double pz;

        Point(int x, int y, int z, int fx, int fy, int fz, String src, String side, String block,
              double px, double py, double pz) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.fx = fx;
            this.fy = fy;
            this.fz = fz;
            this.src = src == null ? "" : src;
            this.side = side == null ? "" : side;
            this.block = block == null ? "" : block;
            this.px = px;
            this.py = py;
            this.pz = pz;
        }

        String toJson(int index) {
            return "{\"i\":" + index
                    + ",\"x\":" + x
                    + ",\"y\":" + y
                    + ",\"z\":" + z
                    + ",\"src\":\"" + esc(src)
                    + "\",\"side\":\"" + esc(side)
                    + "\",\"block\":\"" + esc(block)
                    + "\",\"fx\":" + fx
                    + ",\"fy\":" + fy
                    + ",\"fz\":" + fz
                    + ",\"px\":" + n3(px)
                    + ",\"py\":" + n3(py)
                    + ",\"pz\":" + n3(pz)
                    + "}";
        }
    }
}

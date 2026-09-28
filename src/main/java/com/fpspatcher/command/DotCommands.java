package com.fpspatcher.command;

import com.fpspatcher.bind.Binds;
import com.fpspatcher.log.CoordLog;
import com.fpspatcher.ring.RingPlace;
import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Arrays;
import java.util.Locale;

/** Client chat commands starting with {@code .} so they never go to the server. */
public final class DotCommands {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final CommandCoords COORDS = new CommandCoords();
    private static final CommandRing RING = new CommandRing();

    private DotCommands() {
    }

    public static boolean handle(String raw) {
        if (raw == null || raw.length() < 2 || raw.charAt(0) != '.') {
            return false;
        }
        String body = raw.substring(1).trim();
        if (body.isEmpty()) {
            return false;
        }
        String[] parts = body.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String[] args = parts.length == 1 ? new String[0] : Arrays.copyOfRange(parts, 1, parts.length);
        ICommandSender sender = mc.thePlayer;
        if (sender == null) {
            return true;
        }
        if (cmd.equals("coords") || cmd.equals("cords") || cmd.equals("cord") || cmd.equals("pos")) {
            run(COORDS, sender, args);
            return true;
        }
        if (cmd.equals("ringplace") || cmd.equals("silentplace") || cmd.equals("splace")) {
            if (args.length == 0) {
                RingPlace.toggle();
            } else if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off")) {
                RingPlace.setEnabled(args[0].equalsIgnoreCase("on"));
            } else {
                tell("Usage: .ringplace [on|off]");
            }
            return true;
        }
        if (cmd.equals("ring")) {
            run(RING, sender, args);
            return true;
        }
        if (cmd.equals("dd") || cmd.equals("modules") || cmd.equals("mods") || cmd.equals("help")) {
            printModules();
            return true;
        }
        if (cmd.equals("bind") || cmd.equals("binds")) {
            Binds.handle(args);
            return true;
        }
        tell("Unknown ." + cmd + "  —  .dd for modules");
        return true;
    }

    private static void run(net.minecraft.command.ICommand command, ICommandSender sender, String[] args) {
        try {
            command.processCommand(sender, args);
        } catch (Exception e) {
            tell(EnumChatFormatting.RED + "Command failed");
        }
    }

    private static void printModules() {
        tell("modules  —  prefix every command with " + EnumChatFormatting.YELLOW + ".");
        row("coords", CoordLog.count() > 0, ".coords  (" + Binds.display("coords") + ")  " + CoordLog.count() + " saved");
        row("ringplace", RingPlace.isEnabled(), ".ringplace  (" + Binds.display("ringplace") + ")");
        tell("also  .bind <module> <key>");
    }

    private static void row(String name, boolean on, String hint) {
        EnumChatFormatting color = on ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
        mc.thePlayer.addChatMessage(new ChatComponentText(
                EnumChatFormatting.GRAY + "  " + EnumChatFormatting.WHITE + pad(name)
                        + color + (on ? "ON " : "OFF")
                        + EnumChatFormatting.DARK_GRAY + "  " + hint
        ));
    }

    private static String pad(String name) {
        StringBuilder sb = new StringBuilder(name);
        while (sb.length() < 12) {
            sb.append(' ');
        }
        return sb.toString();
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

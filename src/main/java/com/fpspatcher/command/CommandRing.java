package com.fpspatcher.command;

import com.fpspatcher.ring.RingPlace;
import com.fpspatcher.ring.RingPlan;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandRing extends CommandBase {
    @Override
    public String getCommandName() {
        return "ring";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return ".ring <add|base|clear|file|list|item|place>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("ringplace");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("info")) {
            tell(sender, RingPlan.statusLine());
            tell(sender, "place " + (RingPlace.isEnabled() ? "ON" : "OFF"));
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("place") || sub.equals("silent") || sub.equals("silentplace")) {
            if (args.length == 1) {
                RingPlace.toggle();
                return;
            }
            if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off")) {
                RingPlace.setEnabled(args[1].equalsIgnoreCase("on"));
                return;
            }
            tell(sender, EnumChatFormatting.RED + "Usage: .ring place [on|off]");
            return;
        }
        if (sub.equals("add") || sub.equals("cell")) {
            if (args.length >= 4) {
                int[] xyz = parseXYZ(sender, args);
                if (xyz != null) {
                    RingPlan.addCell(xyz[0], xyz[1], xyz[2]);
                }
                return;
            }
            RingPlan.addLookCell();
            return;
        }
        if (sub.equals("base") || sub.equals("pillar") || sub.equals("foot")) {
            if (args.length >= 4) {
                int[] xyz = parseXYZ(sender, args);
                if (xyz != null) {
                    RingPlan.addBase(xyz[0], xyz[1], xyz[2]);
                }
                return;
            }
            RingPlan.addLookBase();
            return;
        }
        if (sub.equals("clear") || sub.equals("reset")) {
            RingPlan.clearCustom();
            return;
        }
        if (sub.equals("file") || sub.equals("reload")) {
            RingPlan.useFile();
            return;
        }
        if (sub.equals("list") || sub.equals("l")) {
            RingPlan.printList();
            return;
        }
        if (sub.equals("item") || sub.equals("block")) {
            if (args.length < 2) {
                tell(sender, "item  " + EnumChatFormatting.YELLOW + RingPlan.itemName());
                return;
            }
            RingPlan.setItemName(args[1]);
            return;
        }
        tell(sender, EnumChatFormatting.RED + "Usage: .ring add [x y z] | base [x y z] | clear | file | list | item <block> | place [on|off]");
    }

    private static int[] parseXYZ(ICommandSender sender, String[] args) {
        try {
            return new int[]{
                    parseInt(args[1]),
                    parseInt(args[2]),
                    parseInt(args[3])
            };
        } catch (NumberInvalidException e) {
            tell(sender, EnumChatFormatting.RED + "Coordinates must be whole numbers");
            return null;
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "add", "base", "clear", "file", "list", "item", "place", "status");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        return Collections.emptyList();
    }

    private static void tell(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText(
                EnumChatFormatting.AQUA + "[RingPlace] " + EnumChatFormatting.RESET + message
        ));
    }
}

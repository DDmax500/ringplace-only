package com.fpspatcher.command;

import com.fpspatcher.log.CoordLog;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandCoords extends CommandBase {
    @Override
    public String getCommandName() {
        return "coords";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/coords [mark|undo|delete|list|clear|path]";
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
        return Arrays.asList("cords", "cord", "pos");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            CoordLog.capture();
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("mark") || sub.equals("add") || sub.equals("save") || sub.equals("here")) {
            CoordLog.capture();
            return;
        }
        if (sub.equals("undo") || sub.equals("pop") || sub.equals("back")) {
            CoordLog.undo();
            return;
        }
        if (sub.equals("delete") || sub.equals("del") || sub.equals("remove")
                || sub.equals("rm") || sub.equals("unmark")) {
            if (args.length >= 4) {
                try {
                    CoordLog.deleteAt(Integer.parseInt(args[1]), Integer.parseInt(args[2]),
                            Integer.parseInt(args[3]), false);
                } catch (NumberFormatException e) {
                    sender.addChatMessage(new ChatComponentText(
                            EnumChatFormatting.RED + "Usage: .coords delete [x y z]"
                    ));
                }
                return;
            }
            CoordLog.deleteLook();
            return;
        }
        if (sub.equals("list") || sub.equals("l") || sub.equals("show")) {
            CoordLog.list();
            return;
        }
        if (sub.equals("clear") || sub.equals("reset")) {
            CoordLog.clear();
            return;
        }
        if (sub.equals("path") || sub.equals("file") || sub.equals("status") || sub.equals("info")) {
            CoordLog.path();
            return;
        }
        sender.addChatMessage(new ChatComponentText(
                EnumChatFormatting.RED + "Usage: .coords [mark|undo|delete|list|clear|path]"
        ));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "mark", "undo", "delete", "list", "clear", "path");
        }
        return Collections.emptyList();
    }
}

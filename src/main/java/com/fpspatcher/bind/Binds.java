package com.fpspatcher.bind;

import com.fpspatcher.FPSPatcher;
import com.fpspatcher.log.CoordLog;
import com.fpspatcher.ring.RingConfig;
import com.fpspatcher.ring.RingPlace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** {@code .bind <module> <key>} — change module keybinds and persist them. */
public final class Binds {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String CATEGORY = "binds";
    private static final Map<String, Entry> MODULES = new LinkedHashMap<String, Entry>();

    private static Binds instance;
    private static boolean loading;

    private static final class Entry {
        final String id;
        final String[] aliases;
        final KeyBinding key;
        final Runnable toggle;
        final BooleanSupplier on;

        Entry(String id, String[] aliases, KeyBinding key, Runnable toggle, BooleanSupplier on) {
            this.id = id;
            this.aliases = aliases;
            this.key = key;
            this.toggle = toggle;
            this.on = on;
        }

        boolean matches(String name) {
            if (id.equalsIgnoreCase(name)) {
                return true;
            }
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    private interface BooleanSupplier {
        boolean get();
    }

    private Binds() {
    }

    public static void init() {
        if (instance != null) {
            return;
        }
        MODULES.clear();
        add("ringplace", RingPlace.getKeyBinding(), new Runnable() {
            public void run() {
                RingPlace.toggle();
            }
        }, new BooleanSupplier() {
            public boolean get() {
                return RingPlace.isEnabled();
            }
        }, "silentplace", "splace", "rplace");
        add("coords", CoordLog.getKeyBinding(), new Runnable() {
            public void run() {
                CoordLog.capture();
            }
        }, new BooleanSupplier() {
            public boolean get() {
                return CoordLog.count() > 0;
            }
        }, "cords", "cord", "pos");
        load();
        instance = new Binds();
        MinecraftForge.EVENT_BUS.register(instance);
    }

    public static String display(String moduleId) {
        Entry entry = find(moduleId);
        if (entry == null || entry.key == null) {
            return "NONE";
        }
        return GameSettings.getKeyDisplayString(entry.key.getKeyCode());
    }

    public static boolean handle(String[] args) {
        if (args.length == 0) {
            printAll();
            return true;
        }
        Entry entry = find(args[0]);
        if (entry == null) {
            tell("Unknown module. Try: " + joinIds());
            return true;
        }
        if (args.length == 1) {
            tell(entry.id + " bound to " + EnumChatFormatting.YELLOW + keyName(entry.key));
            return true;
        }
        int code = parseKey(args[1]);
        if (code == Integer.MIN_VALUE) {
            tell("Unknown key " + args[1] + "  —  examples: r, lshift, f6, mouse1, none");
            return true;
        }
        if (entry.key == null) {
            tell(entry.id + " has no keybind");
            return true;
        }
        entry.key.setKeyCode(code);
        KeyBinding.resetKeyBindingArrayAndHash();
        save();
        tell(entry.id + " bound to " + EnumChatFormatting.YELLOW + keyName(entry.key)
                + EnumChatFormatting.GRAY + " (saved)");
        return true;
    }

    public static int parseKey(String raw) {
        if (raw == null) {
            return Integer.MIN_VALUE;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("_", "");
        if (s.equals("NONE") || s.equals("UNBIND") || s.equals("OFF") || s.equals("UNBOUND")) {
            return Keyboard.KEY_NONE;
        }
        if (s.equals("CTRL") || s.equals("CONTROL") || s.equals("LCTRL") || s.equals("LCONTROL")) {
            s = "LCONTROL";
        } else if (s.equals("RCTRL") || s.equals("RCONTROL")) {
            s = "RCONTROL";
        } else if (s.equals("ALT") || s.equals("LALT") || s.equals("LMENU")) {
            s = "LMENU";
        } else if (s.equals("RALT") || s.equals("RMENU")) {
            s = "RMENU";
        } else if (s.equals("SHIFT") || s.equals("LSHIFT")) {
            s = "LSHIFT";
        } else if (s.equals("RSHIFT")) {
            s = "RSHIFT";
        } else if (s.equals("ESC") || s.equals("ESCAPE")) {
            s = "ESCAPE";
        } else if (s.equals("SPACE") || s.equals("SPACEBAR")) {
            s = "SPACE";
        } else if (s.equals("CAPS") || s.equals("CAPSLOCK") || s.equals("CAPITAL")) {
            s = "CAPITAL";
        }
        if (s.startsWith("MOUSE") && s.length() > 5) {
            try {
                int button = Integer.parseInt(s.substring(5));
                if (button >= 1 && button <= 16) {
                    return button - 101;
                }
            } catch (NumberFormatException ignored) {
                return Integer.MIN_VALUE;
            }
        }
        int code = Keyboard.getKeyIndex(s);
        if (code != Keyboard.KEY_NONE) {
            return code;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || mc.currentScreen != null) {
            return;
        }
        for (Entry entry : MODULES.values()) {
            if (entry.key != null && entry.key.isPressed()) {
                entry.toggle.run();
            }
        }
    }

    private static void add(String id, KeyBinding key, Runnable toggle, BooleanSupplier on, String... aliases) {
        MODULES.put(id, new Entry(id, aliases, key, toggle, on));
    }

    private static Entry find(String name) {
        if (name == null) {
            return null;
        }
        Entry direct = MODULES.get(name.toLowerCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        for (Entry entry : MODULES.values()) {
            if (entry.matches(name)) {
                return entry;
            }
        }
        return null;
    }

    private static void printAll() {
        tell("binds  —  .bind <module> <key>");
        for (Entry entry : MODULES.values()) {
            EnumChatFormatting color = entry.on.get() ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.GRAY + "  " + EnumChatFormatting.WHITE + pad(entry.id)
                            + color + (entry.on.get() ? "ON " : "OFF")
                            + EnumChatFormatting.YELLOW + "  " + keyName(entry.key)
            ));
        }
    }

    private static String joinIds() {
        StringBuilder sb = new StringBuilder();
        for (String id : MODULES.keySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(id);
        }
        return sb.toString();
    }

    private static String pad(String name) {
        StringBuilder sb = new StringBuilder(name);
        while (sb.length() < 12) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String keyName(KeyBinding key) {
        if (key == null || key.getKeyCode() == 0) {
            return "NONE";
        }
        return GameSettings.getKeyDisplayString(key.getKeyCode());
    }

    public static void load() {
        Configuration config = RingConfig.getConfig();
        if (config == null) {
            return;
        }
        loading = true;
        try {
            for (Entry entry : MODULES.values()) {
                if (entry.key == null) {
                    continue;
                }
                int code = config.get(CATEGORY, entry.id, entry.key.getKeyCode()).getInt();
                entry.key.setKeyCode(code);
            }
            KeyBinding.resetKeyBindingArrayAndHash();
            if (config.hasChanged()) {
                config.save();
            }
        } catch (Exception e) {
            FPSPatcher.LOGGER.error("Failed to load binds", e);
        } finally {
            loading = false;
        }
    }

    public static void save() {
        if (loading) {
            return;
        }
        Configuration config = RingConfig.getConfig();
        if (config == null) {
            return;
        }
        try {
            for (Entry entry : MODULES.values()) {
                if (entry.key != null) {
                    config.get(CATEGORY, entry.id, 0).set(entry.key.getKeyCode());
                }
            }
            config.save();
        } catch (Exception e) {
            FPSPatcher.LOGGER.error("Failed to save binds", e);
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

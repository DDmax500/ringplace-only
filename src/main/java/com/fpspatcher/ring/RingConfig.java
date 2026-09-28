package com.fpspatcher.ring;

import com.fpspatcher.FPSPatcher;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

/** Forge config file shared by ring place and keybinds. */
public final class RingConfig {
    private static Configuration config;

    private RingConfig() {
    }

    public static void init(File file) {
        config = new Configuration(file);
        try {
            config.load();
            if (config.hasChanged()) {
                config.save();
            }
        } catch (Exception e) {
            FPSPatcher.LOGGER.error("Failed to load ring config", e);
        }
    }

    public static Configuration getConfig() {
        return config;
    }
}

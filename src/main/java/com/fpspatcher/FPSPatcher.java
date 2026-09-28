package com.fpspatcher;

import com.fpspatcher.bind.Binds;
import com.fpspatcher.command.CommandCoords;
import com.fpspatcher.command.CommandRing;
import com.fpspatcher.gl.GuiGl;
import com.fpspatcher.log.CoordLog;
import com.fpspatcher.ring.RingConfig;
import com.fpspatcher.ring.RingPlace;
import com.fpspatcher.rotation.Rotations;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = FPSPatcher.MODID,
        name = FPSPatcher.NAME,
        version = FPSPatcher.VERSION,
        acceptedMinecraftVersions = "[1.8.9]",
        clientSideOnly = true
)
public class FPSPatcher {
    public static final String MODID = "ringplace";
    public static final String NAME = "Ring Place";
    public static final String VERSION = "1.6.11";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Loading {} ring-place only for Minecraft 1.8.9", NAME);
        RingConfig.init(event.getSuggestedConfigurationFile());
        GuiGl.init();
        CoordLog.init();
        RingPlace.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        GuiGl.disableFastRender();
        Rotations.init();
        Binds.init();
        ClientCommandHandler.instance.registerCommand(new CommandCoords());
        ClientCommandHandler.instance.registerCommand(new CommandRing());
        LOGGER.info("{} ring-place initialized", NAME);
    }
}

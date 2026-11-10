package com.dima16263690d.bleckshlng;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.Mod.EventHandler;
import com.dima16263690d.bleckshlng.fluid.network.FluidNetwork;
import com.dima16263690d.bleckshlng.coremod.FluidCoreHooks;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = BleckShlng.MODID,
        name = BleckShlng.NAME,
        version = BleckShlng.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:mixinbooter@[12.2,)"
)
public final class BleckShlng {
    public static final String MODID = "bleckshlng";
    public static final String NAME = "BleckShlng";
    public static final String VERSION = "1.0.0";

    public static Logger LOGGER;

    @SidedProxy(clientSide = "com.dima16263690d.bleckshlng.client.ClientProxy",
            serverSide = "com.dima16263690d.bleckshlng.CommonProxy")
    public static CommonProxy PROXY;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        FluidCoreHooks.enableFlowingLavaRandomTicks();
        FluidNetwork.init();
        if (PROXY != null) {
            PROXY.preInit(event);
        }
        LOGGER.info("{} {} initialized for Minecraft 1.12.2", NAME, VERSION);
    }
}

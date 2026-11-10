package com.dima16263690d.bleckshlng;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import com.dima16263690d.bleckshlng.fluid.network.FluidStateSyncMessage;

/** Common-side proxy contains no client-only Minecraft references. */
public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {
    }

    public void handleFluidStateSync(FluidStateSyncMessage message) {
        // Server never receives this server-to-client message.
    }
}

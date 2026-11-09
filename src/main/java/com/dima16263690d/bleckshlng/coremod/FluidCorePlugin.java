package com.dima16263690d.bleckshlng.coremod;

import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

/**
 * Loads the fluid tick and chunk-render transformers before normal mod init.
 */
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions({
        "com.dima16263690d.bleckshlng.coremod"
})
public final class FluidCorePlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
                "com.dima16263690d.bleckshlng.coremod.FluidTickTransformer",
                "com.dima16263690d.bleckshlng.coremod.FluidRenderTransformer",
                "com.dima16263690d.bleckshlng.coremod.FluidChunkRenderTransformer"
        };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}

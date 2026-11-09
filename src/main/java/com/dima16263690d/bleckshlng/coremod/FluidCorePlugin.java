package com.dima16263690d.bleckshlng.coremod;

import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

/**
 * Loads legacy fluid tick/render transformers. Chunk waterlogging rendering
 * is injected through MixinBooter to avoid depending on an already-started
 * translucent layer.
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
                "com.dima16263690d.bleckshlng.coremod.FluidRenderTransformer"
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

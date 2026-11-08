package com.dima16263690d.bleckshlng.fluid;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import com.dima16263690d.bleckshlng.BleckShlng;
import com.dima16263690d.bleckshlng.block.BlockFlowingFluid;
import com.dima16263690d.bleckshlng.block.BlockStaticFluid;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * Installs the ported 1.21 fluid physics into the vanilla water and lava blocks.
 *
 * <p>Because Forge 1.12.2 block registries allow name overrides, the mod registers its own liquid
 * blocks under the vanilla names {@code minecraft:flowing_water}, {@code minecraft:water},
 * {@code minecraft:flowing_lava} and {@code minecraft:lava}. The vanilla {@code Blocks} constants
 * are then repointed at the new instances so that the rest of the game (world generation, buckets,
 * entity interactions) keeps operating on the same block names and states while the ported
 * {@link FlowingFluid} engine drives all liquid behaviour.</p>
 */
@Mod.EventBusSubscriber(modid = BleckShlng.MODID)
public final class FluidPhysics {
    public static BlockFlowingFluid FLOWING_WATER;
    public static BlockStaticFluid WATER;
    public static BlockFlowingFluid FLOWING_LAVA;
    public static BlockStaticFluid LAVA;

    private FluidPhysics() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        FLOWING_WATER = new BlockFlowingFluid(Fluids.FLOWING_WATER, Material.WATER, "flowing_water", "water");
        WATER = new BlockStaticFluid(Fluids.WATER, Material.WATER, "water", "water");
        FLOWING_LAVA = new BlockFlowingFluid(Fluids.FLOWING_LAVA, Material.LAVA, "flowing_lava", "lava");
        LAVA = new BlockStaticFluid(Fluids.LAVA, Material.LAVA, "lava", "lava");

        IForgeRegistry<Block> registry = event.getRegistry();
        registry.register(FLOWING_WATER);
        registry.register(WATER);
        registry.register(FLOWING_LAVA);
        registry.register(LAVA);

        replaceVanillaConstants();
        retargetBuckets();
    }

    private static void replaceVanillaConstants() {
        setStaticField("FLOWING_WATER", "field_150358_i", FLOWING_WATER);
        setStaticField("WATER", "field_150355_j", WATER);
        setStaticField("FLOWING_LAVA", "field_150356_k", FLOWING_LAVA);
        setStaticField("LAVA", "field_150353_l", LAVA);
    }

    /**
     * The water and lava buckets captured the vanilla liquid blocks when {@code Items} was
     * initialised, before this mod's override ran, so repoint them at the ported blocks.
     */
    private static void retargetBuckets() {
        setInstanceField(Items.WATER_BUCKET, "containedBlock", "field_77876_a", FLOWING_WATER);
        setInstanceField(Items.LAVA_BUCKET, "containedBlock", "field_77876_a", FLOWING_LAVA);
    }

    private static void setStaticField(String deobfName, String srgName, Object value) {
        Field field = null;
        try {
            field = Blocks.class.getDeclaredField(deobfName);
        } catch (NoSuchFieldException ignored) {
            try {
                field = Blocks.class.getDeclaredField(srgName);
            } catch (NoSuchFieldException e) {
                BleckShlng.LOGGER.error("Unable to locate vanilla block field {} / {}", deobfName, srgName, e);
                return;
            }
        }
        field.setAccessible(true);
        try {
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (Exception ignored) {
            // Field is not final in this environment; plain set below is enough.
        }
        try {
            field.set(null, value);
        } catch (IllegalAccessException e) {
            BleckShlng.LOGGER.error("Unable to repoint vanilla block field {}", deobfName, e);
        }
    }

    private static void setInstanceField(Object instance, String deobfName, String srgName, Object value) {
        Field field = null;
        try {
            field = instance.getClass().getDeclaredField(deobfName);
        } catch (NoSuchFieldException ignored) {
            try {
                field = instance.getClass().getDeclaredField(srgName);
            } catch (NoSuchFieldException e) {
                BleckShlng.LOGGER.error("Unable to locate field {} / {} on {}", deobfName, srgName, instance.getClass(), e);
                return;
            }
        }
        field.setAccessible(true);
        try {
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        } catch (Exception ignored) {
            // Field is not final in this environment; plain set below is enough.
        }
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            BleckShlng.LOGGER.error("Unable to repoint field {}", deobfName, e);
        }
    }
}

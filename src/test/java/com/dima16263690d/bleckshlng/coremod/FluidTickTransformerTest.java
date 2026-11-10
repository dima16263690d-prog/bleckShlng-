package com.dima16263690d.bleckshlng.coremod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that the 1.12.2 Coremod transformer actually finds the expected
 * vanilla methods in the Forge development class files and inserts the hooks.
 * These are bytecode integration tests; they do not launch a game world.
 */
public class FluidTickTransformerTest {
    private static final String HOOK_OWNER =
            "com/dima16263690d/bleckshlng/coremod/FluidCoreHooks";

    @Test
    public void transformsBaseBlockRandomTick() throws Exception {
        assertHook(net.minecraft.block.Block.class, "tryRandomTickDynamicLiquid");
    }

    @Test
    public void transformsDynamicLiquidScheduledTick() throws Exception {
        assertHook(net.minecraft.block.BlockDynamicLiquid.class,
                "tryUpdateDynamicLiquid");
    }

    @Test
    public void transformsStaticLavaNeighborReaction() throws Exception {
        assertHook(net.minecraft.block.BlockStaticLiquid.class,
                "tryReactStaticLava");
    }

    @Test
    public void transformsLiquidPlacementAndAcceleration() throws Exception {
        byte[] transformed = transform(net.minecraft.block.BlockLiquid.class);
        assertTrue("static lava placement hook missing",
                hasHook(transformed, "tryReactStaticLava"));
        assertTrue("fluid acceleration hook missing",
                hasHook(transformed, "tryModifyAcceleration"));
    }

    @Test
    public void transformsWorldBlockPlacement() throws Exception {
        assertHook(net.minecraft.world.World.class, "onWorldBlockSet");
    }

    private static void assertHook(Class<?> target, String hookName) throws Exception {
        byte[] transformed = transform(target);
        assertTrue(target.getName() + " did not receive " + hookName,
                hasHook(transformed, hookName));
    }

    private static byte[] transform(Class<?> target) throws Exception {
        String resource = "/" + target.getName().replace('.', '/') + ".class";
        InputStream input = target.getResourceAsStream(resource);
        assertNotNull("Missing class bytes for " + target.getName(), input);

        byte[] original;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            original = output.toByteArray();
        } finally {
            input.close();
        }

        FluidTickTransformer transformer = new FluidTickTransformer();
        return transformer.transform(target.getName(), target.getName(), original);
    }

    private static boolean hasHook(byte[] bytecode, String hookName) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytecode).accept(classNode, 0);

        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (HOOK_OWNER.equals(call.owner) && hookName.equals(call.name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

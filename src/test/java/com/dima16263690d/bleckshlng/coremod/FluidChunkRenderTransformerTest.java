package com.dima16263690d.bleckshlng.coremod;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import net.minecraft.client.renderer.chunk.RenderChunk;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Bytecode integration test for the 1.12.2 chunk translucent-layer hook.
 * It verifies that the transformer matches the actual Forge/MCP RenderChunk
 * method instead of silently falling back to WorldLast rendering.
 */
public class FluidChunkRenderTransformerTest {
    private static final String HOOK_OWNER =
            "com/dima16263690d/bleckshlng/client/render/WaterloggedChunkRenderHooks";

    @Test
    public void injectsHookIntoRenderChunkPostRenderBlocks() throws Exception {
        String resource = "/"
                + RenderChunk.class.getName().replace('.', '/') + ".class";
        InputStream input = RenderChunk.class.getResourceAsStream(resource);
        assertNotNull("Missing RenderChunk class bytes", input);

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

        byte[] transformed = new FluidChunkRenderTransformer().transform(
                RenderChunk.class.getName(), RenderChunk.class.getName(), original);
        assertTrue("RenderChunk.postRenderBlocks did not receive the translucent fluid hook",
                hasHook(transformed));
    }

    private static boolean hasHook(byte[] bytecode) {
        ClassNode classNode = new ClassNode();
        new ClassReader(bytecode).accept(classNode, 0);

        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (HOOK_OWNER.equals(call.owner)
                            && "renderTranslucentLayer".equals(call.name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

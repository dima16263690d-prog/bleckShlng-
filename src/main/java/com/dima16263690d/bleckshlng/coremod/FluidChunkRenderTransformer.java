package com.dima16263690d.bleckshlng.coremod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds waterlogged geometry to RenderChunk's TRANSLUCENT buffer before the
 * layer is finalized. This is the chunk-rebuild path; it replaces the
 * old RenderWorldLast-only overlay as the normal renderer.
 */
public final class FluidChunkRenderTransformer implements IClassTransformer {
    private static final String TARGET =
            "net.minecraft.client.renderer.chunk.RenderChunk";
    private static final String HOOK =
            "com/dima16263690d/bleckshlng/client/render/WaterloggedChunkRenderHooks";
    private static final String DESC =
            "(Lnet/minecraft/util/BlockRenderLayer;FFFLnet/minecraft/client/renderer/BufferBuilder;"
            + "Lnet/minecraft/client/renderer/chunk/CompiledChunk;)V";
    private static final String HOOK_DESC =
            "(Lnet/minecraft/client/renderer/chunk/RenderChunk;"
            + "Lnet/minecraft/util/BlockRenderLayer;"
            + "Lnet/minecraft/client/renderer/BufferBuilder;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || transformedName == null
                || !TARGET.equals(transformedName)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;

            for (MethodNode method : node.methods) {
                if (!isPostRenderBlocks(method) || containsHook(method)) {
                    continue;
                }

                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 5));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        HOOK, "renderTranslucentLayer", HOOK_DESC, false));
                method.instructions.insert(hook);
                method.maxStack = Math.max(method.maxStack, 3);
                changed = true;
            }

            if (!changed) {
                return basicClass;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable error) {
            error.printStackTrace();
            return basicClass;
        }
    }

    private static boolean isPostRenderBlocks(MethodNode method) {
        if (method.desc == null || !DESC.equals(method.desc)) {
            return false;
        }
        return "postRenderBlocks".equals(method.name)
                || "func_178584_a".equals(method.name)
                || "a".equals(method.name);
    }

    private static boolean containsHook(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (HOOK.equals(call.owner)
                        && "renderTranslucentLayer".equals(call.name)) {
                    return true;
                }
            }
        }
        return false;
    }
}

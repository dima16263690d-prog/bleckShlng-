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
 * Injects fluid meshing into BlockRendererDispatcher.renderBlock so waterlogged
 * cells are written into the same BufferBuilder as vanilla blocks (chunk mesh).
 */
public final class FluidRenderTransformer implements IClassTransformer {
    private static final String BRD = "net.minecraft.client.renderer.BlockRendererDispatcher";
    private static final String HOOK = "com/dima16263690d/bleckshlng/coremod/FluidRenderHooks";
    private static final String HOOK_DESC =
            "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/world/IBlockAccess;"
                    + "Lnet/minecraft/client/renderer/BufferBuilder;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        if (transformedName == null || (!BRD.equals(transformedName)
                && !transformedName.endsWith("BlockRendererDispatcher"))) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!isRenderBlock(method) || containsHook(method)) {
                    continue;
                }
                inject(method);
                changed = true;
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable t) {
            t.printStackTrace();
            return basicClass;
        }
    }

    private static boolean isRenderBlock(MethodNode method) {
        if (method.desc == null) {
            return false;
        }
        boolean nameOk = "renderBlock".equals(method.name)
                || "func_175018_a".equals(method.name);
        boolean descOk = method.desc.startsWith(
                "(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;"
                        + "Lnet/minecraft/world/IBlockAccess;"
                        + "Lnet/minecraft/client/renderer/BufferBuilder;)");
        return nameOk && descOk;
    }

    private static boolean containsHook(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) insn;
                if (HOOK.equals(mi.owner) && "afterRenderBlock".equals(mi.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void inject(MethodNode method) {
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new VarInsnNode(Opcodes.ALOAD, 2));
        list.add(new VarInsnNode(Opcodes.ALOAD, 3));
        list.add(new VarInsnNode(Opcodes.ALOAD, 4));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "afterRenderBlock",
                HOOK_DESC, false));

        AbstractInsnNode insn = method.instructions.getFirst();
        while (insn != null) {
            AbstractInsnNode next = insn.getNext();
            int op = insn.getOpcode();
            if (op == Opcodes.IRETURN || op == Opcodes.RETURN) {
                method.instructions.insertBefore(insn, cloneList(list));
            }
            insn = next;
        }
    }

    private static InsnList cloneList(InsnList src) {
        InsnList copy = new InsnList();
        for (AbstractInsnNode n = src.getFirst(); n != null; n = n.getNext()) {
            copy.add(n.clone(null));
        }
        return copy;
    }
}

package com.dima16263690d.bleckshlng.coremod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Injects an optional pre-hook into BlockDynamicLiquid.updateTick.
 *
 * If the mod hook declines or throws, the original bytecode remains available
 * and executes unchanged. This is intentionally not a full method replacement.
 */
public final class FluidTickTransformer implements IClassTransformer {
    private static final String TARGET = "net.minecraft.block.BlockDynamicLiquid";
    private static final String HOOK_OWNER =
            "com/dima16263690d/bleckshlng/coremod/FluidCoreHooks";
    private static final String HOOK_DESC =
            "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !isTarget(name, transformedName)) {
            return basicClass;
        }

        try {
            ClassNode classNode = new ClassNode();
            new ClassReader(basicClass).accept(classNode, 0);

            boolean changed = false;
            for (MethodNode method : classNode.methods) {
                if (isUpdateTick(method)) {
                    injectHook(method);
                    changed = true;
                    break;
                }
            }

            if (!changed) {
                return basicClass;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(writer);
            return writer.toByteArray();
        } catch (Throwable error) {
            System.err.println("[BleckShlng] Fluid transformer skipped BlockDynamicLiquid: "
                    + error.getClass().getName() + ": " + error.getMessage());
            return basicClass;
        }
    }

    private static boolean isTarget(String name, String transformedName) {
        return TARGET.equals(name)
                || TARGET.equals(transformedName)
                || (transformedName != null && transformedName.endsWith(".BlockDynamicLiquid"));
    }

    private static boolean isUpdateTick(MethodNode method) {
        if (!(method.name.equals("updateTick")
                || method.name.equals("func_180650_b")
                || method.name.equals("a"))) {
            return false;
        }

        Type[] arguments = Type.getArgumentTypes(method.desc);
        return Type.VOID_TYPE.equals(Type.getReturnType(method.desc))
                && arguments.length == 4
                && arguments[3].getSort() == Type.OBJECT
                && "java/util/Random".equals(arguments[3].getInternalName());
    }

    private static void injectHook(MethodNode method) {
        LabelNode originalBody = new LabelNode();
        InsnList hook = new InsnList();

        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 3));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 4));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOK_OWNER, "tryUpdateDynamicLiquid", HOOK_DESC, false));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, originalBody));
        hook.add(new InsnNode(Opcodes.RETURN));
        hook.add(originalBody);
        hook.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));

        method.instructions.insert(hook);
        method.maxStack = Math.max(method.maxStack, 4);
    }
}

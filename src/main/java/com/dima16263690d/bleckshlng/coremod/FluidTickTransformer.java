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
 * Installs two guarded hooks:
 * - BlockDynamicLiquid.updateTick: modernized server-side liquid update.
 * - BlockLiquid.modifyAcceleration: modernized liquid current vector.
 *
 * Unknown/modded liquids fall through to their original methods.
 */
public final class FluidTickTransformer implements IClassTransformer {
    private static final String DYNAMIC_TARGET = "net.minecraft.block.BlockDynamicLiquid";
    private static final String LIQUID_TARGET = "net.minecraft.block.BlockLiquid";
    private static final String ENTITY_TARGET = "net.minecraft.entity.Entity";

    private static final String HOOK_OWNER =
            "com/dima16263690d/bleckshlng/coremod/FluidCoreHooks";
    private static final String TICK_HOOK_DESC =
            "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)Z";
    private static final String ACCELERATION_HOOK_DESC =
            "(Lnet/minecraft/block/BlockLiquid;Lnet/minecraft/world/World;"
            + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;"
            + "Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;";
    private static final String ENTITY_MOVEMENT_DESC =
            "(ZLnet/minecraft/entity/Entity;)Z";
    private static final String ENTITY_MATERIAL_DESC =
            "(ZLnet/minecraft/entity/Entity;Lnet/minecraft/block/material/Material;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        boolean dynamicTarget = isTarget(name, transformedName, DYNAMIC_TARGET);
        boolean liquidTarget = isTarget(name, transformedName, LIQUID_TARGET);
        boolean entityTarget = isTarget(name, transformedName, ENTITY_TARGET);
        if (!dynamicTarget && !liquidTarget && !entityTarget) {
            return basicClass;
        }

        try {
            ClassNode classNode = new ClassNode();
            new ClassReader(basicClass).accept(classNode, 0);
            boolean changed = false;

            if (dynamicTarget) {
                for (MethodNode method : classNode.methods) {
                    if (isUpdateTick(method) && !containsHook(method, "tryUpdateDynamicLiquid")) {
                        injectTickHook(method);
                        changed = true;
                        break;
                    }
                }
            }

            if (liquidTarget) {
                for (MethodNode method : classNode.methods) {
                    if (isModifyAcceleration(method)
                            && !containsHook(method, "tryModifyAcceleration")) {
                        injectAccelerationHook(method);
                        changed = true;
                        break;
                    }
                }
            }

            if (entityTarget) {
                for (MethodNode method : classNode.methods) {
                    if (isHandleWaterMovement(method)
                            && !containsHook(method, "applyWaterloggedMovement")) {
                        injectBooleanEntityHook(method, "applyWaterloggedMovement",
                                ENTITY_MOVEMENT_DESC, false);
                        changed = true;
                    } else if (isInWater(method)
                            && !containsHook(method, "applyWaterloggedInWater")) {
                        injectBooleanEntityHook(method, "applyWaterloggedInWater",
                                ENTITY_MOVEMENT_DESC, false);
                        changed = true;
                    } else if (isInsideOfMaterial(method)
                            && !containsHook(method, "applyWaterloggedMaterialCheck")) {
                        injectBooleanEntityHook(method, "applyWaterloggedMaterialCheck",
                                ENTITY_MATERIAL_DESC, true);
                        changed = true;
                    }
                }
            }

            if (!changed) {
                return basicClass;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(writer);
            return writer.toByteArray();
        } catch (Throwable error) {
            System.err.println("[BleckShlng] Fluid transformer skipped "
                    + transformedName + ": " + error.getClass().getName()
                    + ": " + error.getMessage());
            return basicClass;
        }
    }

    private static boolean isTarget(String name, String transformedName, String target) {
        return target.equals(name) || target.equals(transformedName)
                || (transformedName != null
                && transformedName.endsWith("." + target.substring(target.lastIndexOf('.') + 1)));
    }

    private static boolean isUpdateTick(MethodNode method) {
        if (!(method.name.equals("updateTick")
                || method.name.equals("func_180650_b")
                || method.name.equals("a"))) {
            return false;
        }

        Type[] args = Type.getArgumentTypes(method.desc);
        return Type.VOID_TYPE.equals(Type.getReturnType(method.desc))
                && args.length == 4
                && "net/minecraft/world/World".equals(args[0].getInternalName())
                && "net/minecraft/util/math/BlockPos".equals(args[1].getInternalName())
                && "net/minecraft/block/state/IBlockState".equals(args[2].getInternalName())
                && "java/util/Random".equals(args[3].getInternalName());
    }

    private static boolean isModifyAcceleration(MethodNode method) {
        if (!(method.name.equals("modifyAcceleration")
                || method.name.equals("func_176197_a")
                || method.name.equals("a"))) {
            return false;
        }

        Type[] args = Type.getArgumentTypes(method.desc);
        return args.length == 4
                && "net/minecraft/world/World".equals(args[0].getInternalName())
                && "net/minecraft/util/math/BlockPos".equals(args[1].getInternalName())
                && "net/minecraft/entity/Entity".equals(args[2].getInternalName())
                && "net/minecraft/util/math/Vec3d".equals(args[3].getInternalName())
                && "net/minecraft/util/math/Vec3d".equals(
                        Type.getReturnType(method.desc).getInternalName());
    }

    private static boolean isHandleWaterMovement(MethodNode method) {
        return (method.name.equals("handleWaterMovement")
                || method.name.equals("func_70072_I"))
                && Type.BOOLEAN_TYPE.equals(Type.getReturnType(method.desc))
                && Type.getArgumentTypes(method.desc).length == 0;
    }

    private static boolean isInWater(MethodNode method) {
        return (method.name.equals("isInWater")
                || method.name.equals("func_70090_H"))
                && Type.BOOLEAN_TYPE.equals(Type.getReturnType(method.desc))
                && Type.getArgumentTypes(method.desc).length == 0;
    }

    private static boolean isInsideOfMaterial(MethodNode method) {
        if (!(method.name.equals("isInsideOfMaterial")
                || method.name.equals("func_70055_a"))) {
            return false;
        }
        Type[] args = Type.getArgumentTypes(method.desc);
        return Type.BOOLEAN_TYPE.equals(Type.getReturnType(method.desc))
                && args.length == 1
                && "net/minecraft/block/material/Material".equals(args[0].getInternalName());
    }

    private static void injectBooleanEntityHook(MethodNode method, String hookName,
                                                String descriptor, boolean withMaterial) {
        for (org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; ) {
            org.objectweb.asm.tree.AbstractInsnNode next = instruction.getNext();
            if (instruction.getOpcode() == Opcodes.IRETURN) {
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                if (withMaterial) {
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                }
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        HOOK_OWNER, hookName, descriptor, false));
                method.instructions.insertBefore(instruction, hook);
                method.maxStack = Math.max(method.maxStack, withMaterial ? 3 : 2);
            }
            instruction = next;
        }
    }

    private static boolean containsHook(MethodNode method, String hookName) {
        for (org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (HOOK_OWNER.equals(call.owner) && hookName.equals(call.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void injectTickHook(MethodNode method) {
        LabelNode originalBody = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 3));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 4));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOK_OWNER, "tryUpdateDynamicLiquid", TICK_HOOK_DESC, false));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, originalBody));
        hook.add(new InsnNode(Opcodes.RETURN));
        hook.add(originalBody);
        hook.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(hook);
        method.maxStack = Math.max(method.maxStack, 4);
    }

    private static void injectAccelerationHook(MethodNode method) {
        LabelNode originalBody = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 3));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 4));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOK_OWNER, "tryModifyAcceleration", ACCELERATION_HOOK_DESC, false));
        hook.add(new InsnNode(Opcodes.DUP));
        hook.add(new JumpInsnNode(Opcodes.IFNULL, originalBody));
        hook.add(new InsnNode(Opcodes.ARETURN));
        hook.add(originalBody);
        hook.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1,
                new Object[] {"net/minecraft/util/math/Vec3d"}));
        hook.add(new InsnNode(Opcodes.POP));
        method.instructions.insert(hook);
        method.maxStack = Math.max(method.maxStack, 5);
    }
}

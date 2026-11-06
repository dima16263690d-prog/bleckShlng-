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
    private static final String WORLD_TARGET = "net.minecraft.world.World";
    private static final String LIQUID_TARGET = "net.minecraft.block.BlockLiquid";
    private static final String ENTITY_TARGET = "net.minecraft.entity.Entity";
    private static final String FARMLAND_TARGET = "net.minecraft.block.BlockFarmland";
    private static final String REED_TARGET = "net.minecraft.block.BlockReed";
    private static final String LILYPAD_TARGET = "net.minecraft.block.BlockLilyPad";
    private static final String BUSH_TARGET = "net.minecraft.block.BlockBush";
    private static final String SPONGE_TARGET = "net.minecraft.block.BlockSponge";
    private static final String FISH_HOOK_TARGET = "net.minecraft.entity.projectile.EntityFishHook";

    private static final String HOOK_OWNER =
            "com/dima16263690d/bleckshlng/coremod/FluidCoreHooks";
    private static final String WORLD_SET_HOOK_DESC =
            "(ZLnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/block/state/IBlockState;)V";
    private static final String WORLD_BEGIN_HOOK_DESC =
            "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/block/state/IBlockState;)V";
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
        boolean worldTarget = isTarget(name, transformedName, WORLD_TARGET);
        boolean liquidTarget = isTarget(name, transformedName, LIQUID_TARGET);
        boolean entityTarget = isTarget(name, transformedName, ENTITY_TARGET);
        boolean farmlandTarget = isTarget(name, transformedName, FARMLAND_TARGET);
        boolean reedTarget = isTarget(name, transformedName, REED_TARGET);
        boolean lilyPadTarget = isTarget(name, transformedName, LILYPAD_TARGET);
        boolean bushTarget = isTarget(name, transformedName, BUSH_TARGET);
        boolean spongeTarget = isTarget(name, transformedName, SPONGE_TARGET);
        boolean fishHookTarget = isTarget(name, transformedName, FISH_HOOK_TARGET);
        if (!dynamicTarget && !worldTarget && !liquidTarget && !entityTarget
                && !farmlandTarget && !reedTarget && !lilyPadTarget
                && !bushTarget && !spongeTarget && !fishHookTarget) {
            return basicClass;
        }

        try {
            ClassNode classNode = new ClassNode();
            new ClassReader(basicClass).accept(classNode, 0);
            boolean changed = false;

            if (worldTarget) {
                for (MethodNode method : classNode.methods) {
                    if (isSetBlockState(method) && !containsHook(method, "onWorldBlockSet")) {
                        injectWorldBlockSetHook(method);
                        changed = true;
                        break;
                    }
                }
            }

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

            if (farmlandTarget) {
                for (MethodNode method : classNode.methods) {
                    if (isFarmlandWaterCheck(method)
                            && !containsHook(method, "applyWaterloggedFarmlandWater")) {
                        injectBooleanFarmlandHook(method);
                        changed = true;
                        break;
                    }
                }
            }

            if (reedTarget) {
                for (MethodNode method : classNode.methods) {
                    if (isReedWaterCheck(method)
                            && !containsHook(method, "applyWaterloggedReedWater")) {
                        injectBooleanFarmlandHook(method, "applyWaterloggedReedWater");
                        changed = true;
                        break;
                    }
                }
            }

            if (lilyPadTarget) {
                for (MethodNode method : classNode.methods) {
                    if (isLilyPadWaterCheck(method)
                            && !containsHook(method, "applyWaterloggedLilyPadStay")) {
                        injectBooleanLilyPadHook(method);
                        changed = true;
                        break;
                    }
                }
            }

            if (bushTarget) {
                for (MethodNode method : classNode.methods) {
                    if (isBushCanPlace(method)
                            && !containsHook(method, "applyWaterloggedLilyPadPlacement")) {
                        injectBooleanBushHook(method);
                        changed = true;
                        break;
                    }
                }
            }

            if (spongeTarget) {
                for (MethodNode method : classNode.methods) {
                    if (isSpongeAbsorb(method)
                            && !containsHook(method, "tryAbsorbSpongeWater")) {
                        injectBooleanSpongeHook(method);
                        changed = true;
                        break;
                    }
                }
            }

            if (fishHookTarget) {
                for (MethodNode method : classNode.methods) {
                    if (isFishHookUpdate(method)
                            && !containsHook(method, "getFishHookFluidState")) {
                        injectFishHookFluidState(method);
                        changed = true;
                        break;
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

    private static boolean isSetBlockState(MethodNode method) {
        if (!(method.name.equals("setBlockState")
                || method.name.equals("func_180501_a")
                || method.name.equals("a"))) {
            return false;
        }

        Type[] args = Type.getArgumentTypes(method.desc);
        return Type.BOOLEAN_TYPE.equals(Type.getReturnType(method.desc))
                && args.length == 3
                && "net/minecraft/util/math/BlockPos".equals(args[0].getInternalName())
                && "net/minecraft/block/state/IBlockState".equals(args[1].getInternalName())
                && Type.INT_TYPE.equals(args[2]);
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


    private static boolean isFishHookUpdate(MethodNode method) {
        return (method.name.equals("onUpdate")
                || method.name.equals("func_70071_h_")
                || method.name.equals("h"))
                && Type.VOID_TYPE.equals(Type.getReturnType(method.desc))
                && Type.getArgumentTypes(method.desc).length == 0;
    }

    private static void injectFishHookFluidState(MethodNode method) {
        for (org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }

            MethodInsnNode call = (MethodInsnNode) instruction;
            if (!"net/minecraft/world/World".equals(call.owner)
                    || !("getBlockState".equals(call.name)
                    || "func_180495_p".equals(call.name))
                    || !"(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;"
                    .equals(call.desc)) {
                continue;
            }

            // In EntityFishHook.onUpdate, the first World.getBlockState call
            // reads the block occupied by the bobber. The immediately preceding
            // ALOAD (other than the world field load) is its BlockPos local.
            int posLocal = -1;
            for (org.objectweb.asm.tree.AbstractInsnNode previous = instruction.getPrevious();
                 previous != null; previous = previous.getPrevious()) {
                if (previous instanceof VarInsnNode
                        && previous.getOpcode() == Opcodes.ALOAD) {
                    posLocal = ((VarInsnNode) previous).var;
                    break;
                }
            }

            org.objectweb.asm.tree.AbstractInsnNode store = instruction.getNext();
            while (store != null && !(store instanceof VarInsnNode
                    && store.getOpcode() == Opcodes.ASTORE)) {
                store = store.getNext();
            }
            if (posLocal < 0 || !(store instanceof VarInsnNode)) {
                return;
            }

            int stateLocal = ((VarInsnNode) store).var;
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.GETFIELD,
                    "net/minecraft/entity/Entity", "world", "Lnet/minecraft/world/World;"));
            hook.add(new VarInsnNode(Opcodes.ALOAD, posLocal));
            hook.add(new VarInsnNode(Opcodes.ALOAD, stateLocal));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    HOOK_OWNER, "getFishHookFluidState",
                    "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/block/state/IBlockState;)"
                    + "Lnet/minecraft/block/state/IBlockState;", false));
            hook.add(new VarInsnNode(Opcodes.ASTORE, stateLocal));
            method.instructions.insert(store, hook);
            method.maxStack = Math.max(method.maxStack, 4);
            return;
        }
    }

    private static boolean isSpongeAbsorb(MethodNode method) {
        if (!(method.name.equals("absorb")
                || method.name.equals("absorbWater")
                || method.name.equals("func_176312_b")
                || method.name.equals("b"))) {
            return false;
        }
        Type[] args = Type.getArgumentTypes(method.desc);
        return Type.BOOLEAN_TYPE.equals(Type.getReturnType(method.desc))
                && args.length == 2
                && "net/minecraft/world/World".equals(args[0].getInternalName())
                && "net/minecraft/util/math/BlockPos".equals(args[1].getInternalName());
    }

    private static void injectBooleanSpongeHook(MethodNode method) {
        LabelNode vanillaBody = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOK_OWNER, "tryAbsorbSpongeWater",
                "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Z",
                false));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, vanillaBody));
        hook.add(new InsnNode(Opcodes.ICONST_1));
        hook.add(new InsnNode(Opcodes.IRETURN));
        hook.add(vanillaBody);
        hook.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(hook);
        method.maxStack = Math.max(method.maxStack, 2);
    }

    private static boolean isBushCanPlace(MethodNode method) {
        if (!(method.name.equals("canPlaceBlockAt")
                || method.name.equals("func_176196_c")
                || method.name.equals("a"))) {
            return false;
        }
        Type[] args = Type.getArgumentTypes(method.desc);
        return Type.BOOLEAN_TYPE.equals(Type.getReturnType(method.desc))
                && args.length == 2
                && "net/minecraft/world/World".equals(args[0].getInternalName())
                && "net/minecraft/util/math/BlockPos".equals(args[1].getInternalName());
    }

    private static void injectBooleanBushHook(MethodNode method) {
        for (org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; ) {
            org.objectweb.asm.tree.AbstractInsnNode next = instruction.getNext();
            if (instruction.getOpcode() == Opcodes.IRETURN) {
                InsnList hook = new InsnList();
                // Vanilla boolean result is already on the stack.
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        HOOK_OWNER, "applyWaterloggedLilyPadPlacement",
                        "(ZLnet/minecraft/block/BlockBush;Lnet/minecraft/world/World;"
                        + "Lnet/minecraft/util/math/BlockPos;)Z", false));
                method.instructions.insertBefore(instruction, hook);
                method.maxStack = Math.max(method.maxStack, 4);
            }
            instruction = next;
        }
    }

    private static boolean isLilyPadWaterCheck(MethodNode method) {
        if (!(method.name.equals("canBlockStay")
                || method.name.equals("func_180671_f")
                || method.name.equals("f"))) {
            return false;
        }
        Type[] args = Type.getArgumentTypes(method.desc);
        return Type.BOOLEAN_TYPE.equals(Type.getReturnType(method.desc))
                && args.length == 3
                && "net/minecraft/world/World".equals(args[0].getInternalName())
                && "net/minecraft/util/math/BlockPos".equals(args[1].getInternalName())
                && "net/minecraft/block/state/IBlockState".equals(args[2].getInternalName());
    }

    private static void injectBooleanLilyPadHook(MethodNode method) {
        for (org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; ) {
            org.objectweb.asm.tree.AbstractInsnNode next = instruction.getNext();
            if (instruction.getOpcode() == Opcodes.IRETURN) {
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 3));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        HOOK_OWNER, "applyWaterloggedLilyPadStay",
                        "(ZLnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                        + "Lnet/minecraft/block/state/IBlockState;)Z", false));
                method.instructions.insertBefore(instruction, hook);
                method.maxStack = Math.max(method.maxStack, 4);
            }
            instruction = next;
        }
    }

    private static boolean isReedWaterCheck(MethodNode method) {
        if (!(method.name.equals("canPlaceBlockAt")
                || method.name.equals("func_176196_c")
                || method.name.equals("a"))) {
            return false;
        }
        Type[] args = Type.getArgumentTypes(method.desc);
        return Type.BOOLEAN_TYPE.equals(Type.getReturnType(method.desc))
                && args.length == 2
                && "net/minecraft/world/World".equals(args[0].getInternalName())
                && "net/minecraft/util/math/BlockPos".equals(args[1].getInternalName());
    }

    private static boolean isFarmlandWaterCheck(MethodNode method) {
        if (!(method.name.equals("func_176530_e")
                || method.name.equals("hasWater")
                || method.name.equals("e"))) {
            return false;
        }
        Type[] args = Type.getArgumentTypes(method.desc);
        return Type.BOOLEAN_TYPE.equals(Type.getReturnType(method.desc))
                && args.length == 2
                && "net/minecraft/world/World".equals(args[0].getInternalName())
                && "net/minecraft/util/math/BlockPos".equals(args[1].getInternalName());
    }

    private static void injectBooleanFarmlandHook(MethodNode method) {
        injectBooleanFarmlandHook(method, "applyWaterloggedFarmlandWater");
    }

    private static void injectBooleanFarmlandHook(MethodNode method, String hookName) {
        for (org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; ) {
            org.objectweb.asm.tree.AbstractInsnNode next = instruction.getNext();
            if (instruction.getOpcode() == Opcodes.IRETURN) {
                InsnList hook = new InsnList();
                // The vanilla boolean result is already on the operand stack.
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        HOOK_OWNER, hookName,
                        "(ZLnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Z",
                        false));
                method.instructions.insertBefore(instruction, hook);
                method.maxStack = Math.max(method.maxStack, 3);
            }
            instruction = next;
        }
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

    /**
     * Captures a water source before World.setBlockState replaces it with a
     * waterloggable host. The sidecar is committed only after successful placement.
     */
    private static void injectWorldBlockSetHook(MethodNode method) {
        InsnList begin = new InsnList();
        begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
        begin.add(new VarInsnNode(Opcodes.ALOAD, 1));
        begin.add(new VarInsnNode(Opcodes.ALOAD, 2));
        begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                HOOK_OWNER, "beginWorldBlockSet",
                WORLD_BEGIN_HOOK_DESC, false));
        method.instructions.insert(begin);
        method.maxStack = Math.max(method.maxStack, 3);

        for (org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null; ) {
            org.objectweb.asm.tree.AbstractInsnNode next = instruction.getNext();
            if (instruction.getOpcode() == Opcodes.IRETURN) {
                InsnList callback = new InsnList();
                // Preserve the original return value; the hook pops the
                // matching capture from its thread-local call stack.
                callback.add(new InsnNode(Opcodes.DUP));
                callback.add(new VarInsnNode(Opcodes.ALOAD, 0));
                callback.add(new VarInsnNode(Opcodes.ALOAD, 1));
                callback.add(new VarInsnNode(Opcodes.ALOAD, 2));
                callback.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        HOOK_OWNER, "onWorldBlockSet",
                        WORLD_SET_HOOK_DESC, false));
                method.instructions.insertBefore(instruction, callback);
                method.maxStack = Math.max(method.maxStack, 5);
            } else if (instruction.getOpcode() == Opcodes.ATHROW) {
                InsnList cleanup = new InsnList();
                cleanup.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        HOOK_OWNER, "discardWorldBlockSet", "()V", false));
                method.instructions.insertBefore(instruction, cleanup);
                method.maxStack = Math.max(method.maxStack, 1);
            }
            instruction = next;
        }
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

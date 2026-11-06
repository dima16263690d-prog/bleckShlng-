package com.dima16263690d.bleckshlng.block;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Four-slot campfire cooking storage modeled after 1.18 CampfireBlockEntity.
 *
 * 1.12.2 has no CampfireCooking recipe type. To avoid allowing ores and
 * arbitrary furnace inputs, the adapter accepts only raw-food items that the
 * vanilla 1.18 campfire supports and uses the corresponding 1.12 smelting
 * output. Cooking time is 600 ticks; progress cools by two while extinguished.
 */
public final class TileEntityCampfire extends TileEntity implements IInventory, ITickable {
    private static final int SLOT_COUNT = 4;
    private static final int COOK_TIME = 600;

    private final ItemStack[] items = new ItemStack[] {
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
    };
    private final int[] cookingProgress = new int[SLOT_COUNT];

    @Override
    public void update() {
        if (world == null || world.isRemote || isInvalid()) {
            return;
        }

        if (!(world.getBlockState(pos).getBlock() instanceof BlockCampfire)) {
            return;
        }

        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        boolean active = state.getValue(BlockCampfire.LIT).booleanValue()
                && !state.getValue(BlockCampfire.WATERLOGGED).booleanValue();
        boolean changed = false;

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack input = items[slot];
            if (input.isEmpty()) {
                if (cookingProgress[slot] != 0) {
                    cookingProgress[slot] = 0;
                    changed = true;
                }
                continue;
            }

            if (!active) {
                int cooled = Math.max(0, cookingProgress[slot] - 2);
                if (cooled != cookingProgress[slot]) {
                    cookingProgress[slot] = cooled;
                    changed = true;
                }
                continue;
            }

            ItemStack result = getCookingResult(input);
            if (result.isEmpty()) {
                continue;
            }

            cookingProgress[slot]++;
            changed = true;
            if (cookingProgress[slot] >= COOK_TIME) {
                ItemStack output = result.copy();
                EntityItem dropped = new EntityItem(world,
                        pos.getX() + 0.5D, pos.getY() + 0.35D, pos.getZ() + 0.5D,
                        output);
                dropped.motionX = (world.rand.nextDouble() - 0.5D) * 0.08D;
                dropped.motionY = 0.15D;
                dropped.motionZ = (world.rand.nextDouble() - 0.5D) * 0.08D;
                world.spawnEntity(dropped);

                input.shrink(1);
                if (input.isEmpty()) {
                    items[slot] = ItemStack.EMPTY;
                }
                cookingProgress[slot] = 0;
            }
        }

        if (changed) {
            markDirty();
        }
    }

    public boolean addFood(ItemStack offered) {
        if (offered == null || offered.isEmpty() || getCookingResult(offered).isEmpty()) {
            return false;
        }

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!items[slot].isEmpty()) {
                continue;
            }

            ItemStack one = offered.copy();
            one.setCount(1);
            items[slot] = one;
            cookingProgress[slot] = 0;
            markDirty();
            if (world != null) {
                world.notifyBlockUpdate(pos, world.getBlockState(pos),
                        world.getBlockState(pos), 3);
            }
            return true;
        }
        return false;
    }

    private static ItemStack getCookingResult(ItemStack input) {
        if (input == null || input.isEmpty()) {
            return ItemStack.EMPTY;
        }

        // CampfireCookingRecipe is food-only in 1.18.2. Keep this whitelist
        // so 1.12 furnace recipes for ores, sand, logs, etc. do not cook here.
        if (input.getItem() != Items.BEEF
                && input.getItem() != Items.PORKCHOP
                && input.getItem() != Items.CHICKEN
                && input.getItem() != Items.RABBIT
                && input.getItem() != Items.MUTTON
                && input.getItem() != Items.FISH
                && input.getItem() != Items.POTATO) {
            return ItemStack.EMPTY;
        }

        ItemStack result = FurnaceRecipes.instance().getSmeltingResult(input);
        return result == null ? ItemStack.EMPTY : result;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        NBTTagList list = new NBTTagList();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (items[slot].isEmpty()) {
                continue;
            }
            NBTTagCompound entry = new NBTTagCompound();
            entry.setByte("Slot", (byte) slot);
            items[slot].writeToNBT(entry);
            list.appendTag(entry);
        }
        compound.setTag("Items", list);
        compound.setIntArray("CookingProgress", cookingProgress);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            items[slot] = ItemStack.EMPTY;
            cookingProgress[slot] = 0;
        }

        NBTTagList list = compound.getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot < SLOT_COUNT) {
                items[slot] = new ItemStack(entry);
            }
        }

        if (compound.hasKey("CookingProgress", 11)) {
            int[] saved = compound.getIntArray("CookingProgress");
            System.arraycopy(saved, 0, cookingProgress, 0,
                    Math.min(saved.length, cookingProgress.length));
        }
    }

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return index >= 0 && index < SLOT_COUNT ? items[index] : ItemStack.EMPTY;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (index < 0 || index >= SLOT_COUNT || count <= 0 || items[index].isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = items[index];
        ItemStack result;
        if (source.getCount() <= count) {
            result = source;
            items[index] = ItemStack.EMPTY;
        } else {
            result = source.splitStack(count);
        }
        markDirty();
        return result;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        if (index < 0 || index >= SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack result = items[index];
        items[index] = ItemStack.EMPTY;
        cookingProgress[index] = 0;
        markDirty();
        return result;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index < 0 || index >= SLOT_COUNT) return;
        items[index] = stack == null ? ItemStack.EMPTY : stack;
        if (!items[index].isEmpty() && items[index].getCount() > getInventoryStackLimit()) {
            items[index].setCount(getInventoryStackLimit());
        }
        markDirty();
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return world != null && world.getTileEntity(pos) == this
                && player.getDistanceSq(pos.getX() + 0.5D,
                        pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return index >= 0 && index < SLOT_COUNT && !getCookingResult(stack).isEmpty();
    }

    @Override
    public int getField(int id) {
        return id >= 0 && id < SLOT_COUNT ? cookingProgress[id] : 0;
    }

    @Override
    public void setField(int id, int value) {
        if (id >= 0 && id < SLOT_COUNT) {
            cookingProgress[id] = Math.max(0, Math.min(COOK_TIME, value));
            markDirty();
        }
    }

    @Override
    public int getFieldCount() {
        return SLOT_COUNT;
    }

    @Override
    public void clear() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            items[slot] = ItemStack.EMPTY;
            cookingProgress[slot] = 0;
        }
        markDirty();
    }

    @Override
    public String getName() {
        return "container.bleckshlng.campfire";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentTranslation(getName());
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.cargo;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public record LinkedCargoSnapshot(
        boolean hasItemStorage,
        boolean hasFluidStorage,
        List<ItemStack> itemStacks,
        List<FluidStack> fluidStacks,
        int remainingItemCapacity,
        long remainingFluidCapacity
) {
    public static LinkedCargoSnapshot capture(Level level, List<LinkedCargoEntry> entries) {
        if (level == null || entries.isEmpty()) {
            return empty();
        }

        Set<Object> seenItemRoots = new HashSet<>();
        Set<Object> seenFluidRoots = new HashSet<>();
        List<ItemStack> itemStacks = new ArrayList<>();
        List<FluidStack> fluidStacks = new ArrayList<>();
        int remainingItemCapacity = 0;
        long remainingFluidCapacity = 0L;
        boolean hasItemStorage = false;
        boolean hasFluidStorage = false;

        for (LinkedCargoEntry entry : entries) {
            CargoStorageRootResolver.StorageRoot root = CargoStorageRootResolver.resolve(level, entry.pos());
            BlockPos accessPos = root.accessPos();
            CustomCargoEndpointSupport.EndpointCapture customCapture = CustomCargoEndpointSupport.capture(level, accessPos);
            if (customCapture != null) {
                if (customCapture.hasItemStorage() && seenItemRoots.add(customCapture.itemRootKey())) {
                    hasItemStorage = true;
                    for (ItemStack stack : customCapture.itemStacks()) {
                        itemStacks.add(stack.copy());
                    }
                    remainingItemCapacity = safeAdd(remainingItemCapacity, customCapture.remainingItemCapacity());
                }
                if (customCapture.hasFluidStorage() && seenFluidRoots.add(customCapture.fluidRootKey())) {
                    hasFluidStorage = true;
                    for (FluidStack stack : customCapture.fluidStacks()) {
                        fluidStacks.add(stack.copy());
                    }
                    remainingFluidCapacity = safeAdd(remainingFluidCapacity, customCapture.remainingFluidCapacity());
                }
                continue;
            }

            IItemHandler itemHandler = CargoCapabilityAccess.findItemHandler(level, accessPos);
            Object itemRootKey = itemHandler == null
                    ? null
                    : root.kind().equals("self")
                            ? CustomCargoEndpointSupport.rootIdentityKey(itemHandler)
                            : root;
            if (itemHandler != null && seenItemRoots.add(itemRootKey)) {
                hasItemStorage = true;
                for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                    ItemStack stack = itemHandler.getStackInSlot(slot).copy();
                    itemStacks.add(stack);
                    int slotLimit = Math.max(0, itemHandler.getSlotLimit(slot));
                    int slotCapacity = stack.isEmpty() ? slotLimit : Math.min(slotLimit, Math.max(0, stack.getMaxStackSize()));
                    int used = Math.max(0, stack.getCount());
                    remainingItemCapacity = safeAdd(remainingItemCapacity, Math.max(0, slotCapacity - used));
                }
            }

            IFluidHandler fluidHandler = CargoCapabilityAccess.findFluidHandler(level, accessPos);
            Object fluidRootKey = fluidHandler == null
                    ? null
                    : root.kind().equals("self")
                            ? CustomCargoEndpointSupport.rootIdentityKey(fluidHandler)
                            : root;
            if (fluidHandler != null && seenFluidRoots.add(fluidRootKey)) {
                hasFluidStorage = true;
                for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                    FluidStack fluidStack = fluidHandler.getFluidInTank(tank).copy();
                    fluidStacks.add(fluidStack);
                    int tankCapacity = Math.max(0, fluidHandler.getTankCapacity(tank));
                    remainingFluidCapacity = safeAdd(
                            remainingFluidCapacity,
                            Math.max(0L, (long) tankCapacity - fluidStack.getAmount())
                    );
                }
            }
        }

        return new LinkedCargoSnapshot(
                hasItemStorage,
                hasFluidStorage,
                itemStacks.stream().map(ItemStack::copy).toList(),
                fluidStacks.stream().map(FluidStack::copy).toList(),
                remainingItemCapacity,
                remainingFluidCapacity
        );
    }

    public static LinkedCargoSnapshot empty() {
        return new LinkedCargoSnapshot(false, false, List.of(), List.of(), 0, 0L);
    }

    public int totalItemCount() {
        int total = 0;
        for (ItemStack stack : itemStacks) {
            total += Math.max(0, stack.getCount());
        }
        return total;
    }

    public int itemAmount(Level level, ItemStack filter, boolean stacks) {
        FilterItemStack filterStack = FilterItemStack.of(filter);
        int amount = 0;
        for (ItemStack stack : itemStacks) {
            if (!filterStack.test(level, stack)) {
                continue;
            }
            amount += stacks
                    ? stack.getCount() == stack.getMaxStackSize() ? 1 : 0
                    : stack.getCount();
        }
        return amount;
    }

    public long totalFluidAmount() {
        long total = 0L;
        for (FluidStack stack : fluidStacks) {
            total += Math.max(0, stack.getAmount());
        }
        return total;
    }

    public int fluidBuckets(Level level, ItemStack filter) {
        FilterItemStack filterStack = FilterItemStack.of(filter);
        long amount = 0L;
        for (FluidStack fluidStack : fluidStacks) {
            if (!filterStack.test(level, fluidStack)) {
                continue;
            }
            amount += fluidStack.getAmount();
        }
        return (int) Math.min(Integer.MAX_VALUE, amount / 1000L);
    }

    public boolean itemFull() {
        return hasItemStorage && remainingItemCapacity <= 0;
    }

    public boolean fluidFull() {
        return hasFluidStorage && remainingFluidCapacity <= 0L;
    }

    public static boolean canAcceptAnyItems(Level level, List<LinkedCargoEntry> targetEntries, LinkedCargoSnapshot offered) {
        if (!offered.itemStacks().isEmpty() && CustomCargoEndpointSupport.canAcceptAnyItems(level, targetEntries, offered)) {
            return true;
        }

        Set<Object> seenRoots = new HashSet<>();
        for (LinkedCargoEntry entry : targetEntries) {
            CargoStorageRootResolver.StorageRoot root = CargoStorageRootResolver.resolve(level, entry.pos());
            BlockPos accessPos = root.accessPos();
            IItemHandler itemHandler = CargoCapabilityAccess.findItemHandler(level, accessPos);
            Object rootKey = itemHandler == null
                    ? null
                    : root.kind().equals("self") ? CustomCargoEndpointSupport.rootIdentityKey(itemHandler) : root;
            if (itemHandler == null || !seenRoots.add(rootKey)) {
                continue;
            }
            for (ItemStack offeredStack : offered.itemStacks()) {
                if (offeredStack.isEmpty()) {
                    continue;
                }
                if (canInsertAny(itemHandler, offeredStack)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean canAcceptAnyFluids(Level level, List<LinkedCargoEntry> targetEntries, LinkedCargoSnapshot offered) {
        if (!offered.fluidStacks().isEmpty() && CustomCargoEndpointSupport.canAcceptAnyFluids(level, targetEntries, offered)) {
            return true;
        }

        Set<Object> seenRoots = new HashSet<>();
        for (LinkedCargoEntry entry : targetEntries) {
            CargoStorageRootResolver.StorageRoot root = CargoStorageRootResolver.resolve(level, entry.pos());
            BlockPos accessPos = root.accessPos();
            IFluidHandler fluidHandler = CargoCapabilityAccess.findFluidHandler(level, accessPos);
            Object rootKey = fluidHandler == null
                    ? null
                    : root.kind().equals("self") ? CustomCargoEndpointSupport.rootIdentityKey(fluidHandler) : root;
            if (fluidHandler == null || !seenRoots.add(rootKey)) {
                continue;
            }
            for (FluidStack offeredStack : offered.fluidStacks()) {
                if (offeredStack.isEmpty()) {
                    continue;
                }
                if (fluidHandler.fill(offeredStack.copy(), IFluidHandler.FluidAction.SIMULATE) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean canInsertAny(IItemHandler itemHandler, ItemStack offeredStack) {
        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            ItemStack remainder = itemHandler.insertItem(slot, offeredStack.copy(), true);
            if (remainder.getCount() < offeredStack.getCount()) {
                return true;
            }
        }
        return false;
    }

    private static int safeAdd(int left, int right) {
        return (int) Math.min(Integer.MAX_VALUE, (long) left + Math.max(0L, right));
    }

    private static long safeAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public record DockTransferSnapshot(
        List<String> items,
        List<String> fluids,
        List<ItemStack> itemStacks,
        List<FluidStack> fluidStacks,
        int itemCount,
        long fluidAmount
) {
    public static DockTransferSnapshot capture(
            DockingConnectorBlockEntity stationDock,
            DockingConnectorBlockEntity shipDock
    ) {
        List<String> itemState = new ArrayList<>();
        List<String> fluidState = new ArrayList<>();
        List<ItemStack> itemStacks = new ArrayList<>();
        List<FluidStack> fluidStacks = new ArrayList<>();
        ConnectorTotals stationTotals = captureConnector(stationDock, itemState, fluidState, itemStacks, fluidStacks);
        ConnectorTotals shipTotals = captureConnector(shipDock, itemState, fluidState, itemStacks, fluidStacks);
        return new DockTransferSnapshot(
                List.copyOf(itemState),
                List.copyOf(fluidState),
                itemStacks.stream().map(ItemStack::copy).toList(),
                fluidStacks.stream().map(FluidStack::copy).toList(),
                stationTotals.itemCount() + shipTotals.itemCount(),
                stationTotals.fluidAmount() + shipTotals.fluidAmount()
        );
    }

    private static ConnectorTotals captureConnector(
            DockingConnectorBlockEntity connector,
            List<String> itemState,
            List<String> fluidState,
            List<ItemStack> itemStacks,
            List<FluidStack> fluidStacks
    ) {
        Level level = connector.getLevel();
        if (level == null) {
            itemState.add("unavailable");
            fluidState.add("unavailable");
            return new ConnectorTotals(0, 0L);
        }

        int itemCount = 0;
        IItemHandler itemHandler = level.getCapability(
                Capabilities.ItemHandler.BLOCK,
                connector.getBlockPos(),
                null
        );
        if (itemHandler == null) {
            itemState.add("unavailable");
        } else {
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                ItemStack stack = itemHandler.getStackInSlot(slot).copy();
                itemStacks.add(stack.copy());
                itemState.add(slot + "|" + itemKey(stack));
                itemCount += stack.getCount();
            }
        }

        long fluidAmount = 0L;
        IFluidHandler fluidHandler = level.getCapability(
                Capabilities.FluidHandler.BLOCK,
                connector.getBlockPos(),
                null
        );
        if (fluidHandler == null) {
            fluidState.add("unavailable");
        } else {
            for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                FluidStack fluidStack = fluidHandler.getFluidInTank(tank).copy();
                fluidStacks.add(fluidStack.copy());
                fluidState.add(tank + "|" + fluidKey(fluidStack));
                fluidAmount += Math.max(0, fluidStack.getAmount());
            }
        }
        return new ConnectorTotals(itemCount, fluidAmount);
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

    private static String itemKey(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
                + "|"
                + stack.getCount()
                + "|"
                + stack.getComponents();
    }

    private static String fluidKey(FluidStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        return BuiltInRegistries.FLUID.getKey(stack.getFluid())
                + "|"
                + stack.getAmount()
                + "|"
                + stack.getComponents();
    }

    private record ConnectorTotals(int itemCount, long fluidAmount) {
    }
}

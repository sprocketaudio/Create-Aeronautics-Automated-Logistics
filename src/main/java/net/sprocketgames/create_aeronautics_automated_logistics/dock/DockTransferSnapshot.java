package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import dev.simulated_team.simulated.multiloader.tanks.CFluidType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

public record DockTransferSnapshot(ConnectorContents station, ConnectorContents ship) {
    public static DockTransferSnapshot capture(
            DockingConnectorBlockEntity stationDock,
            DockingConnectorBlockEntity shipDock
    ) {
        return new DockTransferSnapshot(captureConnector(stationDock), captureConnector(shipDock));
    }

    private static ConnectorContents captureConnector(DockingConnectorBlockEntity connector) {
        List<String> itemState = new ArrayList<>();
        List<String> fluidState = new ArrayList<>();
        List<ItemStack> itemStacks = new ArrayList<>();
        List<FluidStack> fluidStacks = new ArrayList<>();
        // Do not request the connector's exposed capability here. Simulated's paired
        // capability rebuilds the active dock inventory; this snapshot must only observe
        // the local buffers while cargo waits are running.
        ItemStack stack = connector.inventory.getItem(0).copy();
        itemStacks.add(stack.copy());
        itemState.add("0|" + itemKey(stack));
        int itemCount = stack.getCount();

        CFluidType type = connector.tank.type;
        long storedAmount = Math.max(0L, connector.tank.amount);
        FluidStack fluidStack = storedAmount == 0L || type == null || type.isBlank()
                ? FluidStack.EMPTY
                : new FluidStack(type.fluid, (int) Math.min(Integer.MAX_VALUE, storedAmount));
        fluidStacks.add(fluidStack.copy());
        fluidState.add("0|" + fluidKey(fluidStack));
        long fluidAmount = storedAmount;
        return new ConnectorContents(itemState, fluidState, itemStacks, fluidStacks, itemCount, fluidAmount);
    }

    /**
     * Moves cargo that entered a connector's local slot before the pair locked into
     * the receiving connector's local slot. Simulated's paired inventory normally
     * routes new inserts to that receiving slot, but Create belts can leave one
     * pre-lock stack behind where automation on the other connector cannot extract it.
     */
    static int handoffLocalItem(
            DockingConnectorBlockEntity source,
            DockingConnectorBlockEntity destination
    ) {
        ItemStack sourceStack = source.inventory.getItem(0).copy();
        if (sourceStack.isEmpty()) {
            return 0;
        }

        ItemStack destinationStack = destination.inventory.getItem(0).copy();
        if (!destinationStack.isEmpty() && !ItemStack.isSameItemSameComponents(sourceStack, destinationStack)) {
            return 0;
        }

        int destinationLimit = Math.min(
                destination.inventory.getMaxStackSize(),
                sourceStack.getMaxStackSize()
        );
        int available = destinationStack.isEmpty() ? destinationLimit : destinationLimit - destinationStack.getCount();
        int moved = Math.min(sourceStack.getCount(), Math.max(0, available));
        if (moved <= 0) {
            return 0;
        }

        if (destinationStack.isEmpty()) {
            destinationStack = sourceStack.copyWithCount(moved);
        } else {
            destinationStack.grow(moved);
        }
        sourceStack.shrink(moved);
        source.inventory.setItem(0, sourceStack);
        destination.inventory.setItem(0, destinationStack);
        source.setChanged();
        destination.setChanged();
        source.notifyUpdate();
        destination.notifyUpdate();
        return moved;
    }

    public List<String> items() {
        return combined(station.items(), ship.items());
    }

    public List<String> fluids() {
        return combined(station.fluids(), ship.fluids());
    }

    public List<ItemStack> itemStacks() {
        return combined(station.itemStacks(), ship.itemStacks());
    }

    public List<FluidStack> fluidStacks() {
        return combined(station.fluidStacks(), ship.fluidStacks());
    }

    public int itemCount() {
        return station.itemCount() + ship.itemCount();
    }

    public long fluidAmount() {
        return station.fluidAmount() + ship.fluidAmount();
    }

    public int itemAmount(Level level, ItemStack filter, boolean stacks) {
        FilterItemStack filterStack = FilterItemStack.of(filter);
        int amount = 0;
        for (ItemStack stack : itemStacks()) {
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
        for (FluidStack fluidStack : fluidStacks()) {
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

    private static <T> List<T> combined(List<T> first, List<T> second) {
        List<T> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }

    public record ConnectorContents(
            List<String> items,
            List<String> fluids,
            List<ItemStack> itemStacks,
            List<FluidStack> fluidStacks,
            int itemCount,
            long fluidAmount
    ) {
        public ConnectorContents {
            items = List.copyOf(items);
            fluids = List.copyOf(fluids);
            itemStacks = itemStacks.stream().map(ItemStack::copy).toList();
            fluidStacks = fluidStacks.stream().map(FluidStack::copy).toList();
        }
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.cargo;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

public record LinkedCargoEntry(BlockPos pos, boolean itemStorage, boolean fluidStorage) {
    private static final String POS = "pos";
    private static final String ITEM = "item";
    private static final String FLUID = "fluid";

    public LinkedCargoEntry {
        pos = pos.immutable();
    }

    public boolean hasAnyStorage() {
        return itemStorage || fluidStorage;
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.put(POS, NbtUtils.writeBlockPos(pos));
        tag.putBoolean(ITEM, itemStorage);
        tag.putBoolean(FLUID, fluidStorage);
        return tag;
    }

    public static Optional<LinkedCargoEntry> read(CompoundTag tag) {
        return NbtUtils.readBlockPos(tag, POS)
                .map(pos -> new LinkedCargoEntry(pos, tag.getBoolean(ITEM), tag.getBoolean(FLUID)))
                .filter(LinkedCargoEntry::hasAnyStorage);
    }
}

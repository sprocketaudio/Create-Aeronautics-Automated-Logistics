package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.ShipTransponderScreen;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.ShipTransponderMenu;

import java.util.List;

public record SyncTransponderMenuStatePayload(
        BlockPos transponderPos,
        ShipTransponderMenu.StatusSnapshot snapshot,
        List<LinkedCargoEntry> linkedCargoEntries
) implements CustomPacketPayload {
    public static final Type<SyncTransponderMenuStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateAeronauticsAutomatedLogistics.MOD_ID,
                    "sync_transponder_menu_state"
            )
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncTransponderMenuStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(SyncTransponderMenuStatePayload::write, SyncTransponderMenuStatePayload::read);

    private static SyncTransponderMenuStatePayload read(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        ShipTransponderMenu.StatusSnapshot snapshot = ShipTransponderMenu.readStatusSnapshot(buffer);
        List<LinkedCargoEntry> linkedCargoEntries = buffer.readableBytes() >= Integer.BYTES
                ? java.util.stream.IntStream.range(0, buffer.readInt())
                .mapToObj(ignored -> new LinkedCargoEntry(
                        buffer.readBlockPos(),
                        buffer.readBoolean(),
                        buffer.readBoolean()
                ))
                .toList()
                : List.of();
        return new SyncTransponderMenuStatePayload(
                pos,
                snapshot,
                linkedCargoEntries
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(transponderPos);
        ShipTransponderMenu.writeStatusSnapshot(buffer, snapshot);
        ShipTransponderMenu.writeLinkedCargoEntries(buffer, linkedCargoEntries == null ? List.of() : linkedCargoEntries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncTransponderMenuStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Transponder sync payload received pos={} text='{}' color={} tooltipLines={} linkedCargoEntries={}",
                    payload.transponderPos(),
                    payload.snapshot().text(),
                    Integer.toHexString(payload.snapshot().color()),
                    payload.snapshot().tooltip().size(),
                    payload.linkedCargoEntries().size()
            );
            BlockPos zero = BlockPos.ZERO;
            if (Minecraft.getInstance().player != null
                    && Minecraft.getInstance().player.containerMenu instanceof ShipTransponderMenu menu
                    && (menu.transponderPos().equals(payload.transponderPos()) || menu.transponderPos().equals(zero))) {
                if (menu.transponderPos().equals(zero)) {
                    menu.setClientResolvedTransponderPos(payload.transponderPos());
                }
                menu.setClientStatusSnapshot(payload.snapshot());
                menu.setClientLinkedCargoEntries(payload.linkedCargoEntries());
            }
            if (Minecraft.getInstance().screen instanceof ShipTransponderScreen screen
                    && (screen.getMenu().transponderPos().equals(payload.transponderPos())
                    || screen.getMenu().transponderPos().equals(zero))) {
                if (screen.getMenu().transponderPos().equals(zero)) {
                    screen.getMenu().setClientResolvedTransponderPos(payload.transponderPos());
                }
                screen.getMenu().setClientStatusSnapshot(payload.snapshot());
                screen.getMenu().setClientLinkedCargoEntries(payload.linkedCargoEntries());
            }
        });
    }
}

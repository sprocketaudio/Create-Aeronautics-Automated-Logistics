package net.sprocketgames.create_aeronautics_automated_logistics.network;

import net.minecraft.client.Minecraft;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.ShipTransponderScreen;

public record SetTransponderRecordingStatePayload(boolean recording, Optional<UUID> destinationStationId) implements CustomPacketPayload {
    public static final Type<SetTransponderRecordingStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "set_transponder_recording_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SetTransponderRecordingStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(SetTransponderRecordingStatePayload::write, SetTransponderRecordingStatePayload::read);

    private static SetTransponderRecordingStatePayload read(RegistryFriendlyByteBuf buffer) {
        boolean recording = buffer.readBoolean();
        Optional<UUID> destinationStationId = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
        return new SetTransponderRecordingStatePayload(recording, destinationStationId);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(recording);
        buffer.writeBoolean(destinationStationId.isPresent());
        destinationStationId.ifPresent(buffer::writeUUID);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetTransponderRecordingStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof ShipTransponderScreen screen) {
                screen.setRecordingSessionActive(payload.recording(), payload.destinationStationId());
            }
        });
    }
}

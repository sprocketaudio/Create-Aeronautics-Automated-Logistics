package net.sprocketgames.create_aeronautics_automated_logistics.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityDirectorySavedData;
import net.sprocketgames.create_aeronautics_automated_logistics.menu.AirshipScheduleMenu;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;

public record SelectAirshipScheduleStationPayload(int entryIndex, String filter) implements CustomPacketPayload {
    public static final Type<SelectAirshipScheduleStationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "select_airship_schedule_station")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectAirshipScheduleStationPayload> STREAM_CODEC =
            StreamCodec.ofMember(SelectAirshipScheduleStationPayload::write, SelectAirshipScheduleStationPayload::read);

    private static SelectAirshipScheduleStationPayload read(RegistryFriendlyByteBuf buffer) {
        return new SelectAirshipScheduleStationPayload(buffer.readVarInt(), buffer.readUtf(64));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entryIndex);
        buffer.writeUtf(filter, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectAirshipScheduleStationPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.containerMenu instanceof AirshipScheduleMenu menu)) {
            return;
        }
        if (menu.openedFromTransponder()) {
            return;
        }

        AirshipSchedule schedule = menu.schedule(player);
        if (payload.entryIndex < 0 || payload.entryIndex >= schedule.entries().size()) {
            return;
        }

        String filter = payload.filter == null ? "" : payload.filter.trim().toLowerCase(java.util.Locale.ROOT);
        List<AirshipStationSnapshot> stations = stationChoices(player).stream()
                .filter(station -> filter.isBlank() || station.stationName().toLowerCase(java.util.Locale.ROOT).contains(filter))
                .toList();
        if (stations.isEmpty()) {
            net.minecraft.network.chat.Component message = net.minecraft.network.chat.Component.translatable(
                    "message.create_aeronautics_automated_logistics.airship_schedule.no_matching_stations"
            );
            SetMenuActionBarMessagePayload.send(player, message);
            return;
        }

        AirshipStationSnapshot station = stations.stream()
                .filter(candidate -> candidate.stationName().equalsIgnoreCase(filter))
                .findFirst()
                .orElseGet(stations::getFirst);
        List<AirshipScheduleEntry> entries = new ArrayList<>(schedule.entries());
        entries.set(payload.entryIndex, entries.get(payload.entryIndex).withTargetStation(station.stationId(), station.stationName()));
        menu.writeSchedule(player, schedule.withEntries(entries));
    }

    private static List<AirshipStationSnapshot> stationChoices(ServerPlayer player) {
        List<AirshipStationSnapshot> persisted = IdentityDirectorySavedData.get(player.server)
                .stationSnapshots(player.level().dimension());
        return persisted.isEmpty()
                ? AirshipStationRegistry.knownStations(player.level().dimension())
                : persisted;
    }
}

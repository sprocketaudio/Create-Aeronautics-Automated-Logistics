package net.sprocketgames.create_aeronautics_automated_logistics.menu;

import com.simibubi.create.AllSoundEvents;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.CargoLinkSupport;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSummary;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.AirshipStationSnapshot;
import net.sprocketgames.create_aeronautics_automated_logistics.dock.DockLinkStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.identity.IdentityNames;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetFlightPathPreviewPayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SetMenuActionBarMessagePayload;
import net.sprocketgames.create_aeronautics_automated_logistics.network.SyncTransponderMenuStatePayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sprocketgames.create_aeronautics_automated_logistics.AutomatedLogisticsConfig;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipSchedule;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleEntry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.AirshipScheduleNbtSerializer;
import net.sprocketgames.create_aeronautics_automated_logistics.route.CargoWaitTarget;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteStatus;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegment;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentRegistry;
import net.sprocketgames.create_aeronautics_automated_logistics.route.RouteSegmentResolver;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitCondition;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AirshipScheduleExecutionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.AutomatedLogisticsServices;
import net.sprocketgames.create_aeronautics_automated_logistics.service.CargoFailureContext;
import net.sprocketgames.create_aeronautics_automated_logistics.service.DockLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.CargoLinkInteractionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RecordingSession;
import java.util.Locale;
import java.util.ArrayList;
import net.sprocketgames.create_aeronautics_automated_logistics.service.PlaybackFailure;
import net.sprocketgames.create_aeronautics_automated_logistics.service.RuntimeProjectionService;
import net.sprocketgames.create_aeronautics_automated_logistics.service.TransponderPermissionService;

public class ShipTransponderMenu extends AbstractContainerMenu {
    public static final int ACTION_START_INSTALLED_SCHEDULE = 0;
    public static final int ACTION_STOP_SCHEDULE = 1;
    public static final int ACTION_TOGGLE_PREVIEW = 2;
    public static final int ACTION_BEGIN_LINK_DOCK = 3;
    public static final int ACTION_CLEAR_DOCK_LINK = 4;
    public static final int ACTION_EDIT_INSTALLED_SCHEDULE = 5;
    public static final int ACTION_LINK_CARGO = 6;
    public static final int ACTION_CLEAR_CARGO = 7;
    public static final int ACTION_SHOW_CARGO = 8;
    private static final int PLAYER_INVENTORY_START = 0;
    private static final int PLAYER_INVENTORY_END = 28;
    private static final int HOTBAR_START = 28;
    private static final int HOTBAR_END = 37;

    private final BlockPos transponderPos;
    private final boolean initialRecordingMode;
    private final boolean initialRecordingSessionActive;
    private final boolean initialAppendToSchedule;
    private final Optional<UUID> initialRecordingDestinationStationId;
    private final RouteStatus initialRuntimeStatus;
    private final boolean initialDockOutputActive;
    private final boolean initialHasOwnedStops;
    private final AirshipSchedule initialOwnedSchedule;
    private final int initialCargoRevision;
    private final LinkedCargoSummary initialCargoSummary;
    private final List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> initialLinkedCargoEntries;
    private final Optional<CargoFailureContext> initialCargoFailureContext;
    private final Optional<PlaybackFailure> initialFailure;
    private final StatusSnapshot initialStatusSnapshot;
    private final Player menuPlayer;
    private AirshipSchedule clientOwnedScheduleOverride = null;
    private StatusSnapshot clientStatusSnapshotOverride = null;
    private List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> clientLinkedCargoEntriesOverride = null;
    private BlockPos clientResolvedTransponderPos = null;
    private int lastSyncedStatusSnapshotHash;
    private int lastSyncedLinkedCargoEntriesHash;

    public ShipTransponderMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, readOpenData(buffer));
    }

    private ShipTransponderMenu(int containerId, Inventory playerInventory, OpenData data) {
        this(
                containerId,
                playerInventory,
                data.transponderPos(),
                data.initialRecordingMode(),
                data.initialRecordingSessionActive(),
                data.initialAppendToSchedule(),
                data.initialRecordingDestinationStationId(),
                data.initialRuntimeStatus(),
                data.initialDockOutputActive(),
                data.initialHasOwnedStops(),
                data.initialOwnedSchedule(),
                data.initialCargoRevision(),
                data.initialCargoSummary(),
                data.initialLinkedCargoEntries(),
                data.initialCargoFailureContext(),
                data.initialFailure(),
                data.initialStatusSnapshot()
        );
    }

    public ShipTransponderMenu(int containerId, Inventory playerInventory, BlockPos transponderPos) {
        this(containerId, playerInventory, transponderPos, false, false, false, Optional.empty(), RouteStatus.IDLE, false, false, AirshipSchedule.empty(), 0, new LinkedCargoSummary(0, 0, 0, 0, 0), List.of(), Optional.empty(), Optional.empty(), StatusSnapshot.idle());
    }

    public ShipTransponderMenu(int containerId, Inventory playerInventory, BlockPos transponderPos, boolean initialRecordingMode) {
        this(containerId, playerInventory, transponderPos, initialRecordingMode, false, false, Optional.empty(), RouteStatus.IDLE, false, false, AirshipSchedule.empty(), 0, new LinkedCargoSummary(0, 0, 0, 0, 0), List.of(), Optional.empty(), Optional.empty(), StatusSnapshot.idle());
    }

    public ShipTransponderMenu(int containerId, Inventory playerInventory, BlockPos transponderPos, boolean initialRecordingMode, boolean initialRecordingSessionActive, boolean initialAppendToSchedule, Optional<UUID> initialRecordingDestinationStationId, RouteStatus initialRuntimeStatus, boolean initialDockOutputActive, boolean initialHasOwnedStops, AirshipSchedule initialOwnedSchedule, int initialCargoRevision, LinkedCargoSummary initialCargoSummary, List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> initialLinkedCargoEntries, Optional<CargoFailureContext> initialCargoFailureContext, Optional<PlaybackFailure> initialFailure, StatusSnapshot initialStatusSnapshot) {
        super(ModMenus.SHIP_TRANSPONDER.get(), containerId);
        this.transponderPos = transponderPos;
        this.initialRecordingMode = initialRecordingMode;
        this.initialRecordingSessionActive = initialRecordingSessionActive;
        this.initialAppendToSchedule = initialAppendToSchedule;
        this.initialRecordingDestinationStationId = initialRecordingDestinationStationId == null ? Optional.empty() : initialRecordingDestinationStationId;
        this.initialRuntimeStatus = initialRuntimeStatus == null ? RouteStatus.IDLE : initialRuntimeStatus;
        this.initialDockOutputActive = initialDockOutputActive;
        this.initialHasOwnedStops = initialHasOwnedStops;
        this.initialOwnedSchedule = initialOwnedSchedule == null ? AirshipSchedule.empty() : initialOwnedSchedule;
        this.initialCargoRevision = initialCargoRevision;
        this.initialCargoSummary = initialCargoSummary == null ? new LinkedCargoSummary(0, 0, 0, 0, 0) : initialCargoSummary;
        this.initialLinkedCargoEntries = initialLinkedCargoEntries == null ? List.of() : List.copyOf(initialLinkedCargoEntries);
        this.initialCargoFailureContext = initialCargoFailureContext == null ? Optional.empty() : initialCargoFailureContext;
        this.initialFailure = initialFailure == null ? Optional.empty() : initialFailure;
        this.initialStatusSnapshot = initialStatusSnapshot == null ? StatusSnapshot.idle() : initialStatusSnapshot;
        this.menuPlayer = playerInventory.player;
        addPlayerInventorySlots(playerInventory, 17, 198);
    }

    private static OpenData readOpenData(FriendlyByteBuf buffer) {
        FriendlyByteBuf openBuffer = buffer == null ? new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer(0)) : buffer;
        BlockPos transponderPos = openBuffer.readableBytes() >= Long.BYTES ? openBuffer.readBlockPos() : BlockPos.ZERO;
        boolean initialRecordingMode = openBuffer.readableBytes() > 0 && openBuffer.readBoolean();
        boolean initialRecordingSessionActive = openBuffer.readableBytes() > 0 && openBuffer.readBoolean();
        boolean initialAppendToSchedule = openBuffer.readableBytes() > 0 && openBuffer.readBoolean();
        Optional<UUID> initialRecordingDestinationStationId =
                openBuffer.readableBytes() > 0 && openBuffer.readBoolean()
                        ? Optional.of(openBuffer.readUUID())
                        : Optional.empty();
        RouteStatus initialRuntimeStatus = openBuffer.readableBytes() > 0
                ? openBuffer.readEnum(RouteStatus.class)
                : RouteStatus.IDLE;
        boolean initialDockOutputActive = openBuffer.readableBytes() > 0 && openBuffer.readBoolean();
        boolean initialHasOwnedStops = openBuffer.readableBytes() > 0 && openBuffer.readBoolean();
        AirshipSchedule initialOwnedSchedule = openBuffer.readableBytes() > 0 ? readSchedule(openBuffer) : AirshipSchedule.empty();
        int initialCargoRevision = openBuffer.readableBytes() >= Integer.BYTES ? openBuffer.readInt() : 0;
        LinkedCargoSummary initialCargoSummary = openBuffer.readableBytes() >= Integer.BYTES * 5
                ? new LinkedCargoSummary(
                        openBuffer.readInt(),
                        openBuffer.readInt(),
                        openBuffer.readInt(),
                        openBuffer.readInt(),
                        openBuffer.readInt()
                )
                : new LinkedCargoSummary(0, 0, 0, 0, 0);
        List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> initialLinkedCargoEntries =
                openBuffer.readableBytes() >= Integer.BYTES
                        ? IntStream.range(0, openBuffer.readInt())
                                .mapToObj(ignored -> new net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry(
                                        openBuffer.readBlockPos(),
                                        openBuffer.readBoolean(),
                                        openBuffer.readBoolean()
                                ))
                                .toList()
                        : List.of();
        Optional<CargoFailureContext> initialCargoFailureContext = readCargoFailureContext(openBuffer);
        Optional<PlaybackFailure> initialFailure = readPlaybackFailure(openBuffer);
        StatusSnapshot initialStatusSnapshot = readStatusSnapshot(openBuffer);
        return new OpenData(
                transponderPos,
                initialRecordingMode,
                initialRecordingSessionActive,
                initialAppendToSchedule,
                initialRecordingDestinationStationId,
                initialRuntimeStatus,
                initialDockOutputActive,
                initialHasOwnedStops,
                initialOwnedSchedule,
                initialCargoRevision,
                initialCargoSummary,
                initialLinkedCargoEntries,
                initialCargoFailureContext,
                initialFailure,
                initialStatusSnapshot
        );
    }

    private record OpenData(
            BlockPos transponderPos,
            boolean initialRecordingMode,
            boolean initialRecordingSessionActive,
            boolean initialAppendToSchedule,
            Optional<UUID> initialRecordingDestinationStationId,
            RouteStatus initialRuntimeStatus,
            boolean initialDockOutputActive,
            boolean initialHasOwnedStops,
            AirshipSchedule initialOwnedSchedule,
            int initialCargoRevision,
            LinkedCargoSummary initialCargoSummary,
            List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> initialLinkedCargoEntries,
            Optional<CargoFailureContext> initialCargoFailureContext,
            Optional<PlaybackFailure> initialFailure,
            StatusSnapshot initialStatusSnapshot
    ) {
    }

    private static AirshipSchedule readSchedule(FriendlyByteBuf buffer) {
        var tag = buffer.readNbt();
        return tag == null ? AirshipSchedule.empty() : AirshipScheduleNbtSerializer.read(tag);
    }

    public BlockPos transponderPos() {
        return clientResolvedTransponderPos != null ? clientResolvedTransponderPos : transponderPos;
    }

    public void setClientResolvedTransponderPos(BlockPos transponderPos) {
        if (transponderPos == null || transponderPos.equals(BlockPos.ZERO)) {
            return;
        }
        this.clientResolvedTransponderPos = transponderPos;
    }

    public void setClientLinkedCargoEntries(List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> entries) {
        this.clientLinkedCargoEntriesOverride = entries == null ? List.of() : List.copyOf(entries);
    }

    private BlockPos lookupTransponderPos(Player player) {
        return player instanceof ServerPlayer ? transponderPos : transponderPos();
    }

    public boolean initialRecordingMode() {
        return initialRecordingMode;
    }

    public boolean initialRecordingSessionActive() {
        return initialRecordingSessionActive;
    }

    public boolean initialAppendToSchedule() {
        return initialAppendToSchedule;
    }

    public Optional<UUID> initialRecordingDestinationStationId() {
        return initialRecordingDestinationStationId;
    }

    public static void writeCargoSummary(FriendlyByteBuf buffer, LinkedCargoSummary summary) {
        buffer.writeInt(summary.totalLinks());
        buffer.writeInt(summary.validLinks());
        buffer.writeInt(summary.staleLinks());
        buffer.writeInt(summary.itemLinks());
        buffer.writeInt(summary.fluidLinks());
    }

    public static void writeCargoRevision(FriendlyByteBuf buffer, int revision) {
        buffer.writeInt(revision);
    }

    public static void writeLinkedCargoEntries(FriendlyByteBuf buffer, List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> entries) {
        buffer.writeInt(entries.size());
        for (net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry entry : entries) {
            buffer.writeBlockPos(entry.pos());
            buffer.writeBoolean(entry.itemStorage());
            buffer.writeBoolean(entry.fluidStorage());
        }
    }

    public static void writeCargoFailureContext(FriendlyByteBuf buffer, Optional<CargoFailureContext> context) {
        buffer.writeBoolean(context.isPresent());
        if (context.isEmpty()) {
            return;
        }
        buffer.writeEnum(context.get().target());
        buffer.writeEnum(context.get().waitType());
    }

    public static Optional<CargoFailureContext> readCargoFailureContext(FriendlyByteBuf buffer) {
        if (buffer == null) {
            return Optional.empty();
        }
        if (buffer.readableBytes() <= 0 || !buffer.readBoolean()) {
            return Optional.empty();
        }
        return Optional.of(new CargoFailureContext(
                buffer.readEnum(CargoWaitTarget.class),
                buffer.readEnum(net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType.class)
        ));
    }

    public static void writePlaybackFailure(FriendlyByteBuf buffer, Optional<PlaybackFailure> failure) {
        buffer.writeBoolean(failure.isPresent());
        failure.ifPresent(buffer::writeEnum);
    }

    public static Optional<PlaybackFailure> readPlaybackFailure(FriendlyByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() <= 0 || !buffer.readBoolean()) {
            return Optional.empty();
        }
        return Optional.of(buffer.readEnum(PlaybackFailure.class));
    }

    public static void writeStatusSnapshot(FriendlyByteBuf buffer, StatusSnapshot snapshot) {
        buffer.writeUtf(snapshot.shipName(), 128);
        buffer.writeUtf(snapshot.text(), 256);
        buffer.writeInt(snapshot.color());
        buffer.writeInt(snapshot.tooltip().size());
        for (StatusTooltipLine line : snapshot.tooltip()) {
            buffer.writeUtf(line.text(), 512);
            buffer.writeInt(line.color());
        }
        buffer.writeBoolean(snapshot.scheduleActive());
        buffer.writeBoolean(snapshot.scheduleHeld());
        buffer.writeBoolean(snapshot.hasOwnedStops());
        buffer.writeBoolean(snapshot.readyRoute());
        buffer.writeBoolean(snapshot.canPreviewRoute());
        buffer.writeBoolean(snapshot.canControl());
        buffer.writeBoolean(snapshot.operationalShip());
        writeViewSnapshot(buffer, snapshot.dockView());
        writeViewSnapshot(buffer, snapshot.cargoView());
    }

    public static StatusSnapshot readStatusSnapshot(FriendlyByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() <= 0) {
            return StatusSnapshot.idle();
        }
        String shipName = buffer.readUtf(128);
        String text = buffer.readUtf(256);
        int color = buffer.readInt();
        int count = buffer.readInt();
        List<StatusTooltipLine> tooltip = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            tooltip.add(new StatusTooltipLine(buffer.readUtf(512), buffer.readInt()));
        }
        boolean scheduleActive = buffer.readableBytes() > 0 && buffer.readBoolean();
        boolean scheduleHeld = buffer.readableBytes() > 0 && buffer.readBoolean();
        boolean hasOwnedStops = buffer.readableBytes() > 0 && buffer.readBoolean();
        boolean readyRoute = buffer.readableBytes() > 0 && buffer.readBoolean();
        boolean canPreviewRoute = buffer.readableBytes() > 0 && buffer.readBoolean();
        boolean canControl = buffer.readableBytes() > 0 && buffer.readBoolean();
        boolean operationalShip = buffer.readableBytes() > 0 && buffer.readBoolean();
        ViewSnapshot dockView = buffer.readableBytes() > 0 ? readViewSnapshot(buffer, ViewSnapshot.dockNone()) : ViewSnapshot.dockNone();
        ViewSnapshot cargoView = buffer.readableBytes() > 0 ? readViewSnapshot(buffer, ViewSnapshot.cargoNone()) : ViewSnapshot.cargoNone();
        return new StatusSnapshot(shipName, text, color, List.copyOf(tooltip), scheduleActive, scheduleHeld, hasOwnedStops, readyRoute, canPreviewRoute, canControl, operationalShip, dockView, cargoView);
    }

    public static void writeViewSnapshot(FriendlyByteBuf buffer, ViewSnapshot snapshot) {
        buffer.writeUtf(snapshot.text(), 256);
        buffer.writeInt(snapshot.color());
        buffer.writeBoolean(snapshot.active());
        buffer.writeInt(snapshot.tooltip().size());
        for (StatusTooltipLine line : snapshot.tooltip()) {
            buffer.writeUtf(line.text(), 512);
            buffer.writeInt(line.color());
        }
    }

    public static ViewSnapshot readViewSnapshot(FriendlyByteBuf buffer, ViewSnapshot fallback) {
        if (buffer == null || buffer.readableBytes() <= 0) {
            return fallback;
        }
        String text = buffer.readUtf(256);
        int color = buffer.readInt();
        boolean active = buffer.readBoolean();
        int count = buffer.readInt();
        List<StatusTooltipLine> tooltip = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            tooltip.add(new StatusTooltipLine(buffer.readUtf(512), buffer.readInt()));
        }
        return new ViewSnapshot(text, color, active, List.copyOf(tooltip));
    }

    public static InitialRecordingState resolveInitialRecordingState(ServerPlayer player, ShipTransponderBlockEntity transponder, boolean preferredMode) {
        boolean sessionActive = isActiveRecordingForTransponder(player, transponder);
        return new InitialRecordingState(preferredMode || sessionActive, sessionActive, transponder.appendToSchedule());
    }

    private static boolean isActiveRecordingForTransponder(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        Optional<RecordingSession> session = AutomatedLogisticsServices.RECORDING.activeRecordingForPlayer(player.getUUID());
        if (session.isEmpty()) {
            return false;
        }
        RecordingSession active = session.get();
        Optional<UUID> activeVehicleId = active.controllerRef().vehicleId();
        Optional<UUID> runtimeVehicleId = transponder.runtimeShipId();
        if (activeVehicleId.isPresent() && runtimeVehicleId.isPresent() && activeVehicleId.get().equals(runtimeVehicleId.get())) {
            return true;
        }
        if (player.serverLevel().getBlockEntity(active.stationPos()) instanceof AirshipStationBlockEntity station) {
            return station.selectedTransponderId().filter(transponder.transponderId()::equals).isPresent();
        }
        return false;
    }

    public record InitialRecordingState(boolean recordingMode, boolean recordingSessionActive, boolean appendToSchedule) {
    }

    public String shipName(Player player) {
        if (!isServerView(player)) {
            StatusSnapshot snapshot = resolvedClientStatusSnapshot();
            if (!snapshot.shipName().isBlank()) {
                return snapshot.shipName();
            }
        }
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return transponder.shipName();
        }
        return initialOwnedSchedule.title().isBlank() ? "" : initialOwnedSchedule.title();
    }

    public Component shipIdText(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return Component.literal(IdentityNames.shortId(transponder.transponderId()));
        }
        return Component.literal("-");
    }

    public Component runtimeShipText(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            return transponder.runtimeShipId()
                    .map(id -> Component.literal(IdentityNames.shortId(id)))
                    .orElseGet(() -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.unavailable"));
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.missing");
    }

    public Component lastKnownPositionText(Player player) {
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            Optional<Vec3> position = transponder.lastKnownPosition();
            if (position.isPresent()) {
                Vec3 pos = position.get();
                return Component.literal((int) pos.x + ", " + (int) pos.y + ", " + (int) pos.z);
            }
        }
        return Component.literal("-");
    }

    public Component dockText(Player player) {
        if (!isServerView(player)) {
            List<Component> tooltip = dockTooltip(player);
            return tooltip.isEmpty() ? Component.empty() : tooltip.getFirst();
        }
        return dockTooltip(player).isEmpty() ? Component.empty() : dockTooltip(player).getFirst();
    }

    public List<Component> dockTooltip(Player player) {
        if (!isServerView(player)) {
            return componentsFromSnapshot(resolvedClientStatusSnapshot().dockView().tooltip());
        }
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return List.of();
        }
        Component statusLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover.status",
                dockCompactText(player)
        );
        Component outputLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover.redstone_output",
                Component.translatable(
                        transponder.dockOutputActive()
                                ? "gui.create_aeronautics_automated_logistics.dock.output.on"
                                : "gui.create_aeronautics_automated_logistics.dock.output.off"
                )
        );
        Component distanceLine = transponder.shipDockPos()
                .map(dockPos -> {
                    double distance = Math.sqrt(transponderPos.distSqr(dockPos));
                    int meters = (int) Math.round(distance);
                    return Component.translatable(
                            "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover.distance",
                            meters
                    );
                })
                .orElseGet(() -> Component.translatable(
                        "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover.distance_unknown"
                ));
        Component hintLine = Component.translatable(
                "gui.create_aeronautics_automated_logistics.ship_transponder.dock.hover." + transponder.shipDockStatus().name().toLowerCase(Locale.ROOT)
        );
        return List.of(
                statusLine.copy().withStyle(transponder.shipDockStatus() == DockLinkStatus.LINKED ? ChatFormatting.GRAY : ChatFormatting.YELLOW),
                outputLine.copy().withStyle(transponder.dockOutputActive() ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
                distanceLine.copy().withStyle(ChatFormatting.DARK_GRAY),
                hintLine.copy().withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    public Component dockCompactText(Player player) {
        if (!isServerView(player)) {
            return Component.literal(resolvedClientStatusSnapshot().dockView().text());
        }
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.dock.not_connected");
        }
        DockLinkStatus status = transponder.shipDockStatus();
        if (status == DockLinkStatus.AMBIGUOUS) {
            status = DockLinkStatus.INVALID;
        }
        return status == DockLinkStatus.LINKED && transponder.shipDockPos().isPresent()
                ? Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.dock.compact.linked")
                : Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.dock.compact." + status.name().toLowerCase(Locale.ROOT));
    }

    public int dockStatusColor(Player player) {
        if (!isServerView(player)) {
            return resolvedClientStatusSnapshot().dockView().color();
        }
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return 0xFFAFC7DE;
        }
        return switch (transponder.shipDockStatus()) {
            case LINKED -> 0xFFAFC7DE;
            case UNKNOWN, MISSING -> 0xFFFFD98A;
            case AMBIGUOUS, INVALID -> 0xFFFFB4B4;
        };
    }

    public Component cargoCompactText(Player player) {
        if (!isServerView(player)) {
            return Component.literal(resolvedClientStatusSnapshot().cargoView().text());
        }
        LinkedCargoSummary summary = resolveCargoSummary(player);
        if (!summary.hasLinks()) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.cargo.none");
        }
        if (summary.staleLinks() > 0) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.cargo.compact.partial",
                    summary.validLinks(),
                    summary.totalLinks()
            );
        }
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.cargo.compact.linked",
                summary.totalLinks()
        );
    }

    public List<Component> cargoTooltip(Player player) {
        if (!isServerView(player)) {
            return componentsFromSnapshot(resolvedClientStatusSnapshot().cargoView().tooltip());
        }
        LinkedCargoSummary summary = resolveCargoSummary(player);
        if (!summary.hasLinks()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.cargo.none").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.cargo.hover.none").withStyle(ChatFormatting.GRAY)
            );
        }
        String hintKey = summary.staleLinks() > 0
                ? "gui.create_aeronautics_automated_logistics.cargo.hover.partial"
                : "gui.create_aeronautics_automated_logistics.cargo.hover.linked";
        return List.of(
                Component.translatable("gui.create_aeronautics_automated_logistics.cargo.hover.status", cargoCompactText(player))
                        .withStyle(summary.staleLinks() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
                Component.translatable(
                        "gui.create_aeronautics_automated_logistics.cargo.hover.contents",
                        summary.itemLinks(),
                        summary.fluidLinks()
                ).withStyle(ChatFormatting.GRAY),
                Component.translatable(
                        "gui.create_aeronautics_automated_logistics.cargo.hover.validity",
                        summary.validLinks(),
                        summary.staleLinks()
                ).withStyle(ChatFormatting.DARK_GRAY),
                Component.translatable(hintKey).withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    public int cargoStatusColor(Player player) {
        if (!isServerView(player)) {
            return resolvedClientStatusSnapshot().cargoView().color();
        }
        LinkedCargoSummary summary = resolveCargoSummary(player);
        if (!summary.hasLinks()) {
            return 0xFFFFD98A;
        }
        return summary.staleLinks() > 0 ? 0xFFFFD98A : 0xFFAFC7DE;
    }

    public boolean hasLinkedCargo(Player player) {
        if (!isServerView(player)) {
            if (resolvedClientStatusSnapshot().cargoView().active()) {
                return true;
            }
        }
        return resolveCargoSummary(player).hasLinks();
    }

    public boolean hasLinkedDock(Player player) {
        if (!isServerView(player)) {
            if (resolvedClientStatusSnapshot().dockView().active()) {
                return true;
            }
        }
        return liveTransponder(player)
                .flatMap(ShipTransponderBlockEntity::shipDockPos)
                .isPresent();
    }

    public boolean isCargoLinkPending(Player player) {
        return player instanceof ServerPlayer serverPlayer
                && CargoLinkInteractionService.hasPendingTransponderLink(serverPlayer, transponderPos);
    }

    private LinkedCargoSummary resolveCargoSummary(Player player) {
        if (player.level().getBlockEntity(lookupTransponderPos(player)) instanceof ShipTransponderBlockEntity transponder) {
            LinkedCargoSummary liveSummary = transponder.linkedCargoSummary();
            if (transponder.linkedCargoRevision() >= initialCargoRevision
                    || liveSummary.hasLinks()
                    || !initialCargoSummary.hasLinks()) {
                return liveSummary;
            }
        }
        return initialCargoSummary;
    }

    private List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> resolveLinkedCargoEntries(Player player) {
        if (!isServerView(player) && clientLinkedCargoEntriesOverride != null) {
            return clientLinkedCargoEntriesOverride;
        }
        if (player.level().getBlockEntity(lookupTransponderPos(player)) instanceof ShipTransponderBlockEntity transponder) {
            List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> liveEntries = transponder.linkedCargo();
            if (player instanceof ServerPlayer) {
                return liveEntries;
            }
            if (!liveEntries.isEmpty() || initialLinkedCargoEntries.isEmpty()) {
                return liveEntries;
            }
            if (!resolveCargoSummary(player).hasLinks()) {
                return List.of();
            }
        }
        return initialLinkedCargoEntries;
    }

    public Component installedScheduleText(Player player) {
        return Component.literal(resolveOwnedSchedule(player).title());
    }

    public boolean hasInstalledSchedule(Player player) {
        return player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity;
    }

    public boolean hasOwnedStops(Player player) {
        if (!isServerView(player)) {
            return resolvedClientStatusSnapshot().hasOwnedStops() || !resolveOwnedSchedule(player).entries().isEmpty();
        }
        return !resolveOwnedSchedule(player).entries().isEmpty();
    }

    public int ownedStopCount(Player player) {
        return resolveOwnedSchedule(player).entries().size();
    }

    private Optional<ShipTransponderBlockEntity> liveTransponder(Player player) {
        if (player == null || player.level() == null) {
            return Optional.empty();
        }
        return player.level().getBlockEntity(lookupTransponderPos(player)) instanceof ShipTransponderBlockEntity transponder
                ? Optional.of(transponder)
                : Optional.empty();
    }

    private boolean isServerView(Player player) {
        return player instanceof ServerPlayer;
    }

    public StatusSnapshot buildStatusSnapshot(Player player) {
        boolean operationalShip = hasOperationalShip(player);
        List<StatusTooltipLine> tooltip = runtimeStatusTooltip(player).stream()
                .map(component -> new StatusTooltipLine(component.getString(), componentColor(component, 0xFFFFFF)))
                .toList();
        return new StatusSnapshot(
                shipName(player),
                runtimeStateText(player).getString(),
                runtimeStateColor(player),
                tooltip,
                fallbackScheduleActive(player),
                fallbackScheduleHeld(player),
                hasOwnedStops(player),
                hasReadyInstalledRoutePlan(player),
                canPreviewOwnedRoute(player),
                canControlTransponderLocally(player),
                operationalShip,
                buildDockViewSnapshot(player),
                buildCargoViewSnapshot(player)
        );
    }

    private ViewSnapshot buildDockViewSnapshot(Player player) {
        return new ViewSnapshot(
                dockCompactText(player).getString(),
                dockStatusColor(player),
                dockStatusColor(player) == 0xFFAFC7DE,
                dockTooltip(player).stream()
                        .map(component -> new StatusTooltipLine(component.getString(), componentColor(component, 0xFFFFFF)))
                        .toList()
        );
    }

    private ViewSnapshot buildCargoViewSnapshot(Player player) {
        return new ViewSnapshot(
                cargoCompactText(player).getString(),
                cargoStatusColor(player),
                resolveCargoSummary(player).hasLinks(),
                cargoTooltip(player).stream()
                        .map(component -> new StatusTooltipLine(component.getString(), componentColor(component, 0xFFFFFF)))
                        .toList()
        );
    }

    private static int componentColor(Component component, int fallback) {
        TextColor color = component.getStyle().getColor();
        return color == null ? fallback : color.getValue();
    }

    private static Component componentFromLine(StatusTooltipLine line) {
        return Component.literal(line.text()).withStyle(style -> style.withColor(TextColor.fromRgb(line.color())));
    }

    private static List<Component> componentsFromSnapshot(List<StatusTooltipLine> lines) {
        return lines.stream().map(ShipTransponderMenu::componentFromLine).toList();
    }

    private RouteStatus fallbackRuntimeStatus(Player player) {
        Optional<ShipTransponderBlockEntity> live = liveTransponder(player);
        if (live.isPresent()) {
            if (player.level() instanceof ServerLevel serverLevel) {
                return AutomatedLogisticsServices.SCHEDULES.projectedRuntimeStatus(serverLevel, live.get());
            }
            return live.get().runtimeStatus();
        }
        return initialRuntimeStatus;
    }

    private boolean fallbackScheduleActive(Player player) {
        Optional<ShipTransponderBlockEntity> live = liveTransponder(player);
        if (live.isPresent()) {
            if (player.level() instanceof ServerLevel serverLevel) {
                return AutomatedLogisticsServices.SCHEDULES.projectedScheduleActive(serverLevel, live.get());
            }
            return live.get().scheduleActive();
        }
        return initialRuntimeStatus == RouteStatus.RUNNING
                || initialRuntimeStatus == RouteStatus.WAITING
                || initialRuntimeStatus == RouteStatus.HELD
                || initialRuntimeStatus == RouteStatus.HELD_FAULTED;
    }

    private boolean fallbackScheduleHeld(Player player) {
        Optional<ShipTransponderBlockEntity> live = liveTransponder(player);
        if (live.isPresent()) {
            if (player.level() instanceof ServerLevel serverLevel) {
                return AutomatedLogisticsServices.SCHEDULES.projectedScheduleHeld(serverLevel, live.get());
            }
            return live.get().scheduleHeld();
        }
        return initialRuntimeStatus == RouteStatus.HELD || initialRuntimeStatus == RouteStatus.HELD_FAULTED;
    }

    private boolean fallbackDockOutputActive(Player player) {
        Optional<ShipTransponderBlockEntity> live = liveTransponder(player);
        if (live.isPresent()) {
            return live.get().dockOutputActive();
        }
        return initialDockOutputActive;
    }

    private boolean fallbackRecordingActive(Player player) {
        Optional<ShipTransponderBlockEntity> live = liveTransponder(player);
        if (live.isPresent()) {
            return live.get().recordingDestinationStationId().isPresent();
        }
        return initialRecordingDestinationStationId.isPresent() || initialRecordingSessionActive;
    }

    private Optional<PlaybackFailure> fallbackFailure(Player player) {
        if (!isServerView(player)) {
            return initialFailure;
        }
        Optional<ShipTransponderBlockEntity> live = liveTransponder(player);
        if (live.isPresent()) {
            return AutomatedLogisticsServices.SCHEDULES.lastFailure(live.get().transponderId());
        }
        return initialFailure;
    }

    public void setClientOwnedSchedule(AirshipSchedule schedule) {
        this.clientOwnedScheduleOverride = schedule == null ? AirshipSchedule.empty() : schedule;
    }

    public void setClientStatusSnapshot(StatusSnapshot snapshot) {
        this.clientStatusSnapshotOverride = snapshot == null ? StatusSnapshot.idle() : snapshot;
    }

    public boolean canPreviewOwnedRoute(Player player) {
        if (!isServerView(player)) {
            return resolvedClientStatusSnapshot(player).canPreviewRoute();
        }
        return hasOperationalShip(player) && hasOwnedStops(player) && hasResolvableInstalledRouteChain(player);
    }

    public boolean isScheduleRunning(Player player) {
        if (!isServerView(player)) {
            return resolvedClientStatusSnapshot(player).scheduleActive();
        }
        return fallbackScheduleActive(player);
    }

    public boolean isScheduleHeld(Player player) {
        if (!isServerView(player)) {
            return resolvedClientStatusSnapshot(player).scheduleHeld();
        }
        return fallbackScheduleHeld(player);
    }

    public boolean isScheduleSlotLocked(Player player) {
        return isScheduleRunning(player);
    }

    public boolean hasOperationalShip(Player player) {
        if (!isServerView(player)) {
            return resolvedClientStatusSnapshot(player).operationalShip();
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        return liveTransponder(player)
                .flatMap(transponder -> transponder.controllerRef(level))
                .isPresent();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return false;
        }
        if (!TransponderPermissionService.ensureCanControl(serverPlayer, transponder)) {
            return false;
        }

        return switch (id) {
            case ACTION_START_INSTALLED_SCHEDULE -> startInstalledSchedule(serverPlayer, transponder);
            case ACTION_STOP_SCHEDULE -> stopSchedule(serverPlayer, transponder);
            case ACTION_TOGGLE_PREVIEW -> togglePreview(serverPlayer, transponder);
            case ACTION_BEGIN_LINK_DOCK -> beginLinkDock(serverPlayer, transponder);
            case ACTION_CLEAR_DOCK_LINK -> clearDockLink(serverPlayer, transponder);
            case ACTION_EDIT_INSTALLED_SCHEDULE -> editInstalledSchedule(serverPlayer, transponder);
            case ACTION_LINK_CARGO -> linkCargo(serverPlayer, transponder);
            case ACTION_CLEAR_CARGO -> clearCargo(serverPlayer, transponder);
            case ACTION_SHOW_CARGO -> true;
            default -> false;
        };
    }

    public boolean canControlTransponderLocally(Player player) {
        if (!isServerView(player)) {
            return resolvedClientStatusSnapshot(player).canControl();
        }
        if (!AutomatedLogisticsConfig.RESTRICT_TRANSPONDER_CONTROL_TO_OWNER.get()) {
            return true;
        }
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return true;
        }
        return player instanceof ServerPlayer serverPlayer
                ? TransponderPermissionService.canControl(serverPlayer, transponder)
                : transponder.ownerId().isEmpty() || transponder.ownerId().get().equals(player.getUUID());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = this.slots.get(index);
        if (sourceSlot == null || !sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack stackCopy = sourceStack.copy();

        if (index >= PLAYER_INVENTORY_START && index < PLAYER_INVENTORY_END) {
            if (!moveItemStackTo(sourceStack, HOTBAR_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= HOTBAR_START && index < HOTBAR_END) {
            if (!moveItemStackTo(sourceStack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (sourceStack.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        sourceSlot.onTake(player, sourceStack);
        return stackCopy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity)) {
            return false;
        }
        return player.distanceToSqr(
                transponderPos.getX() + 0.5D,
                transponderPos.getY() + 0.5D,
                transponderPos.getZ() + 0.5D
        ) <= 64.0D;
    }

    private boolean startInstalledSchedule(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (!hasOperationalShip(player)) {
            showOperationalShipRequired(player, transponder);
            return false;
        }
        if (AutomatedLogisticsServices.SCHEDULES.isHeld(transponder.transponderId())) {
            boolean resumed = AutomatedLogisticsServices.SCHEDULES.resumeHeldPlayback(player.serverLevel(), transponder.transponderId());
            if (resumed) {
                AllSoundEvents.CONFIRM.playOnServer(player.level(), transponder.getBlockPos(), 0.6f, 1.0f);
                return true;
            }
            PlaybackFailure failure = AutomatedLogisticsServices.SCHEDULES
                    .heldFailure(transponder.transponderId())
                    .orElse(PlaybackFailure.INVALID_ROUTE);
            showMenuWarning(player, Component.translatable(
                    "message.create_aeronautics_automated_logistics.playback.failed",
                    Component.translatable("failure.create_aeronautics_automated_logistics.playback." + failure.name().toLowerCase(Locale.ROOT))
            ));
            return false;
        }
        if (!transponder.hasOwnedStops()) {
            showMenuWarning(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.transponder_no_stops"));
            return false;
        }
        if (!hasReadyInstalledRoutePlan(player)) {
            showMenuWarning(player, Component.translatable("message.create_aeronautics_automated_logistics.airship_schedule.transponder_partial_route"));
            return false;
        }
        var result = AutomatedLogisticsServices.SCHEDULES
                .startFromTransponder(player, transponder, transponder.ownedSchedule());
        result.value().ifPresent(routeId ->
                AllSoundEvents.CONFIRM.playOnServer(player.level(), transponder.getBlockPos(), 0.6f, 1.0f));
        result.failure().ifPresent(failure -> {
            Component message = switch (failure) {
                case START_TOO_FAR_FROM_ROUTE -> Component.translatable(
                        "message.create_aeronautics_automated_logistics.playback.move_to_valid_start_station"
                );
                case WRONG_START_STATION -> AutomatedLogisticsServices.SCHEDULES
                        .currentStartStationName(player.serverLevel(), transponder)
                        .map(stationName -> Component.translatable(
                                "message.create_aeronautics_automated_logistics.playback.wrong_start_station",
                                stationName
                        ))
                        .orElseGet(() -> Component.translatable(
                                "message.create_aeronautics_automated_logistics.playback.move_to_valid_start_station"
                        ));
                case INVALID_ROUTE -> {
                    Optional<String> currentStationName = AutomatedLogisticsServices.SCHEDULES
                            .currentStartStationName(player.serverLevel(), transponder);
                    Optional<String> nextStopName = AutomatedLogisticsServices.SCHEDULES
                            .nextStopNameForCurrentStation(player.serverLevel(), transponder, transponder.ownedSchedule());
                    yield currentStationName.isPresent() && nextStopName.isPresent()
                            ? Component.translatable(
                                    "message.create_aeronautics_automated_logistics.playback.invalid_route_from_station",
                                    currentStationName.get(),
                                    nextStopName.get()
                            )
                            : Component.translatable(
                                    "message.create_aeronautics_automated_logistics.playback.failed",
                                    Component.translatable("failure.create_aeronautics_automated_logistics.playback." + failure.name().toLowerCase(Locale.ROOT))
                            );
                }
                default -> Component.translatable(
                        "message.create_aeronautics_automated_logistics.playback.failed",
                        Component.translatable("failure.create_aeronautics_automated_logistics.playback." + failure.name().toLowerCase(Locale.ROOT))
                );
            };
            actionBar(player, message);
            AllSoundEvents.DENY.playOnServer(player.level(), transponder.getBlockPos(), 0.5f, 1.0f);
        });
        return result.value().isPresent();
    }

    private boolean stopSchedule(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        UUID transponderId = transponder.transponderId();
        if (!AutomatedLogisticsServices.SCHEDULES.hasActiveRuntime(player.serverLevel(), transponderId)) {
            return false;
        }
        AutomatedLogisticsServices.SCHEDULES.stop(player.serverLevel(), transponderId);
        AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(player.level(), transponder.getBlockPos(), 0.45f, 1.2f);
        return true;
    }

    private boolean togglePreview(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (!hasOperationalShip(player)) {
            PacketDistributor.sendToPlayer(
                    player,
                    new SetFlightPathPreviewPayload(
                            false,
                            List.of(),
                            List.of(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty()
                    )
            );
            showOperationalShipRequired(player, transponder);
            return false;
        }
        if (!transponder.hasOwnedStops()) {
            PacketDistributor.sendToPlayer(
                    player,
                    new SetFlightPathPreviewPayload(
                            false,
                            List.of(),
                            List.of(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty()
                    )
            );
            return false;
        }
        PreviewPath preview = previewPath(player.serverLevel(), transponder, transponder.ownedSchedule());
        boolean enabled = !preview.points().isEmpty();
        PacketDistributor.sendToPlayer(
                player,
                new SetFlightPathPreviewPayload(
                        enabled,
                        preview.points(),
                        preview.legEndIndices(),
                        enabled ? Optional.of(transponder.getBlockPos().immutable()) : Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        enabled ? Optional.of(transponder.transponderId()) : Optional.empty()
                )
        );
        return enabled;
    }

    private PreviewPath previewPath(ServerLevel level, ShipTransponderBlockEntity transponder, AirshipSchedule schedule) {
        if (schedule.entries().isEmpty()) {
            return PreviewPath.empty();
        }
        Optional<AirshipScheduleExecutionService.ResolvedStartContext> startContext =
                AutomatedLogisticsServices.SCHEDULES.resolveStartContext(level, transponder, schedule);
        if (startContext.isEmpty()) {
            return PreviewPath.empty();
        }
        List<Vec3> points = new ArrayList<>();
        List<Integer> legEndIndices = new ArrayList<>();
        UUID stationId = startContext.get().stationId();
        AirshipSchedule runtimeSchedule = startContext.get().runtimeSchedule();
        for (AirshipScheduleEntry entry : runtimeSchedule.entries()) {
            if (entry.targetStationId().isEmpty()) {
                break;
            }
            UUID fromStationId = stationId;
            Optional<RouteSegment> segment = entry.pinnedSegmentId()
                    .flatMap(segmentId -> AutomatedLogisticsServices.ROUTES.byId(level.getServer(), segmentId))
                    .filter(candidate -> candidate.startStationId().equals(fromStationId))
                    .filter(candidate -> candidate.endStationId().equals(entry.targetStationId().get()))
                    .filter(candidate -> candidate.dimension().equals(level.dimension()))
                    .filter(candidate -> candidate.transponderId().equals(transponder.transponderId()))
                    .or(() -> RouteSegmentResolver.newestFor(
                            level.getServer(),
                            fromStationId,
                            entry.targetStationId().get(),
                            level.dimension(),
                            Optional.of(transponder.transponderId())
                    ));
            if (segment.isEmpty()) {
                break;
            }
            segment.get().points().forEach(routePoint -> points.add(routePoint.position()));
            if (points.size() >= 2) {
                legEndIndices.add(points.size() - 1);
            }
            stationId = entry.targetStationId().get();
        }
        return new PreviewPath(points, legEndIndices);
    }

    private Optional<UUID> resolveStartStationId(ServerLevel level, ShipTransponderBlockEntity transponder, AirshipSchedule schedule) {
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            Optional<UUID> runningStation = AutomatedLogisticsServices.SCHEDULES.currentStationId(transponder.transponderId());
            if (runningStation.isPresent()) {
                return runningStation;
            }
        }
        return AutomatedLogisticsServices.SCHEDULES.resolveStartContext(level, transponder, schedule)
                .map(AirshipScheduleExecutionService.ResolvedStartContext::stationId);
    }

    private record PreviewPath(List<Vec3> points, List<Integer> legEndIndices) {
        private static PreviewPath empty() {
            return new PreviewPath(List.of(), List.of());
        }
    }

    public Component runtimeStateText(Player player) {
        if (!isServerView(player)) {
            StatusSnapshot snapshot = resolvedClientStatusSnapshot(player);
            return Component.literal(snapshot.text()).setStyle(Style.EMPTY.withColor(snapshot.color()));
        }
        RouteStatus runtimeStatus = fallbackRuntimeStatus(player);
        if (fallbackScheduleActive(player)) {
            if (runtimeStatus == RouteStatus.HELD) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.paused");
            }
            if (runtimeStatus == RouteStatus.HELD_FAULTED) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.paused_fault");
            }
            if (fallbackDockOutputActive(player)) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.docked");
            }
            if (runtimeStatus == RouteStatus.WAITING) {
                return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.waiting");
            }
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.running");
        }
        if (!hasOperationalShip(player)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.ship_missing_status");
        }
        if (!hasOwnedStops(player)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_route_status");
        }
        if (!hasReadyInstalledRoutePlan(player)) {
            return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.partial_route_status");
        }
        Optional<PlaybackFailure> failure = fallbackFailure(player);
        if (failure.isPresent()) {
            return failureStatusText(failure.get());
        }
        return Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.ready");
    }

    public int runtimeStateColor(Player player) {
        if (!isServerView(player)) {
            return resolvedClientStatusSnapshot(player).color();
        }
        RouteStatus runtimeStatus = fallbackRuntimeStatus(player);
        if (fallbackScheduleActive(player)) {
            if (runtimeStatus == RouteStatus.HELD) {
                return 0xFFE7C46E;
            }
            if (runtimeStatus == RouteStatus.HELD_FAULTED) {
                return 0xFFFFB4B4;
            }
            return 0xFFE7C46E;
        }
        if (!hasOperationalShip(player)) {
            return 0xFFFFB4B4;
        }
        if (!hasOwnedStops(player)) {
            return 0xFFFFD98A;
        }
        if (!hasReadyInstalledRoutePlan(player)) {
            return 0xFFFFD98A;
        }
        Optional<PlaybackFailure> failure = fallbackFailure(player);
        if (failure.isPresent()) {
            return 0xFFFFB4B4;
        }
        return 0xAFC7DE;
    }

    public Optional<BlockPos> dockPreviewPos(Player player) {
        if (!canControlTransponderLocally(player) || !hasOperationalShip(player)) {
            return Optional.empty();
        }
        return liveTransponder(player)
                .flatMap(ShipTransponderBlockEntity::shipDockPos);
    }

    public List<BlockPos> cargoPreviewPositions(Player player) {
        return cargoPreviewPositionGroups(player).stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
    }

    public List<List<BlockPos>> cargoPreviewPositionGroups(Player player) {
        boolean canControl = canControlTransponderLocally(player);
        boolean operationalShip = hasOperationalShip(player);
        if (!canControl || !operationalShip) {
            if (!canControl) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                        "Transponder cargo preview blocked localControl=false player={} spectator={} menuPos={}",
                        player.getName().getString(),
                        player.isSpectator(),
                        transponderPos()
                );
            }
            return List.of();
        }
        List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> linkedEntries = resolveLinkedCargoEntries(player);
        if (linkedEntries.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Transponder cargo preview empty linkedEntries player={} spectator={} menuPos={} lookupPos={} initialEntries={} summary={}",
                    player.getName().getString(),
                    player.isSpectator(),
                    transponderPos(),
                    lookupTransponderPos(player),
                    initialLinkedCargoEntries.size(),
                    resolveCargoSummary(player)
            );
            return List.of();
        }
        List<List<BlockPos>> expanded = CargoLinkSupport.expandPreviewPositionGroups(player.level(), lookupTransponderPos(player), 6, linkedEntries);
        if (!expanded.isEmpty()) {
            CreateAeronauticsAutomatedLogistics.debugUi(
                    "Transponder cargo preview expanded player={} spectator={} menuPos={} lookupPos={} linkedEntries={} groups={} flatPositions={}",
                    player.getName().getString(),
                    player.isSpectator(),
                    transponderPos(),
                    lookupTransponderPos(player),
                    linkedEntries.size(),
                    expanded.size(),
                    expanded.stream().mapToInt(List::size).sum()
            );
            return expanded;
        }
        List<List<BlockPos>> fallback = linkedEntries.stream()
                .map(entry -> List.of(entry.pos()))
                .toList();
        CreateAeronauticsAutomatedLogistics.debugUi(
                "Transponder cargo preview fallback player={} spectator={} menuPos={} lookupPos={} linkedEntries={} groups={} firstPos={}",
                player.getName().getString(),
                player.isSpectator(),
                transponderPos(),
                lookupTransponderPos(player),
                linkedEntries.size(),
                fallback.size(),
                fallback.isEmpty() ? "none" : fallback.getFirst().getFirst()
        );
        return fallback;
    }

    public List<Component> runtimeFailureTooltip(Player player) {
        return runtimeStatusTooltip(player);
    }

    public List<Component> runtimeStatusTooltip(Player player) {
        if (!isServerView(player)) {
            StatusSnapshot snapshot = resolvedClientStatusSnapshot(player);
            return snapshot.tooltip().stream()
                    .<Component>map(line -> Component.literal(line.text()).setStyle(Style.EMPTY.withColor(line.color())))
                    .toList();
        }
        Optional<ShipTransponderBlockEntity> live = liveTransponder(player);
        if (fallbackRecordingActive(player)) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.recording.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.recording.2").withStyle(ChatFormatting.GRAY)
            );
        }
        RouteStatus runtimeStatus = fallbackRuntimeStatus(player);
        if (fallbackScheduleActive(player)) {
            if (runtimeStatus == RouteStatus.HELD) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.paused.1").withStyle(ChatFormatting.YELLOW),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.paused.2").withStyle(ChatFormatting.GRAY)
                );
            }
            if (runtimeStatus == RouteStatus.HELD_FAULTED) {
                PlaybackFailure heldFailure = live.flatMap(transponder ->
                                AutomatedLogisticsServices.SCHEDULES.heldFailure(transponder.transponderId()))
                        .orElse(PlaybackFailure.MOVEMENT_FAILURE);
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.paused_fault").withStyle(ChatFormatting.RED),
                        live.map(transponder -> failureReasonText(transponder, heldFailure))
                                .orElseGet(() -> Component.translatable(
                                        "gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason."
                                                + heldFailure.name().toLowerCase(java.util.Locale.ROOT)
                                ).withStyle(ChatFormatting.GRAY)),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.paused_fault.2")
                                .withStyle(ChatFormatting.DARK_GRAY)
                );
            }
            if (fallbackDockOutputActive(player)) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.docked.1").withStyle(ChatFormatting.YELLOW),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.docked.2").withStyle(ChatFormatting.GRAY)
                );
            }
            if (runtimeStatus == RouteStatus.WAITING) {
                return List.of(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.waiting.1").withStyle(ChatFormatting.YELLOW),
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.waiting.2").withStyle(ChatFormatting.GRAY)
                );
            }
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.running.1").withStyle(ChatFormatting.YELLOW),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.running.2").withStyle(ChatFormatting.GRAY)
            );
        }
        if (!hasOperationalShip(player)) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.ship_missing_status").withStyle(ChatFormatting.RED),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason.vehicle_missing").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_hint.vehicle_missing").withStyle(ChatFormatting.DARK_GRAY)
            );
        }
        Optional<PlaybackFailure> failure = fallbackFailure(player);
        if (!hasOwnedStops(player)) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.no_route.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.no_route.2").withStyle(ChatFormatting.GRAY)
            );
        }
        if (!hasReadyInstalledRoutePlan(player)) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.partial_route.1").withStyle(ChatFormatting.YELLOW),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.partial_route.2").withStyle(ChatFormatting.GRAY)
            );
        }
        if (failure.isEmpty()) {
            return List.of(
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.ready.1").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.ready.2").withStyle(ChatFormatting.DARK_GRAY)
            );
        }
        PlaybackFailure value = failure.get();
        String suffix = value.name().toLowerCase(Locale.ROOT);
        return List.of(
                failureStatusText(value).copy().withStyle(ChatFormatting.RED),
                live.map(transponder -> failureReasonText(transponder, value))
                        .orElseGet(() -> Component.translatable(
                                "gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason." + suffix
                        ).withStyle(ChatFormatting.GRAY)),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.failure_hint." + suffix)
                        .withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    private Component failureReasonText(ShipTransponderBlockEntity transponder, PlaybackFailure failure) {
        Optional<CargoFailureContext> context = AutomatedLogisticsServices.SCHEDULES.lastCargoFailureContext(transponder.transponderId())
                .or(() -> transponder.syncedCargoFailureContext())
                .or(() -> initialCargoFailureContext)
                .or(() -> inferCargoFailureContext(transponder, failure));
        if (failure == PlaybackFailure.CARGO_STORAGE_MISSING && context.isPresent()) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason.cargo_storage_missing.detail",
                    cargoFailureResourceText(context.get()),
                    cargoFailureSideText(context.get())
            ).withStyle(ChatFormatting.GRAY);
        }
        if (failure == PlaybackFailure.CARGO_CONDITION_TIMEOUT && context.isPresent()) {
            return Component.translatable(
                    "gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason.cargo_condition_timeout.detail",
                    cargoFailureResourceText(context.get()),
                    cargoFailureSideText(context.get())
            ).withStyle(ChatFormatting.GRAY);
        }
        if (failure == PlaybackFailure.MISSING_DOCK) {
            return inferDockFailureReasonText(transponder).orElseGet(() -> Component.translatable(
                    "gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason.missing_dock"
            ).withStyle(ChatFormatting.GRAY));
        }
        return Component.translatable(
                "gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason."
                        + failure.name().toLowerCase(java.util.Locale.ROOT)
        ).withStyle(ChatFormatting.GRAY);
    }

    private Optional<Component> inferDockFailureReasonText(ShipTransponderBlockEntity transponder) {
        if (transponder.shipDockStatus() != DockLinkStatus.LINKED || transponder.shipDockPos().isEmpty()) {
            return Optional.of(Component.translatable(
                    "gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason.missing_dock.ship"
            ).withStyle(ChatFormatting.GRAY));
        }

        AirshipSchedule schedule = transponder.ownedSchedule();
        for (AirshipScheduleEntry entry : schedule.entries()) {
            if (!entryRequiresDockLock(entry) || entry.targetStationId().isEmpty()) {
                continue;
            }
            Optional<AirshipStationSnapshot> stationSnapshot = entry.targetStationId().flatMap(AirshipStationRegistry::snapshot);
            if (stationSnapshot.isEmpty()) {
                continue;
            }
            if (!(transponder.getLevel().getBlockEntity(stationSnapshot.get().stationPos()) instanceof AirshipStationBlockEntity station)) {
                return Optional.of(Component.translatable(
                        "gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason.missing_dock.station_named",
                        Component.literal(stationSnapshot.get().stationName())
                ).withStyle(ChatFormatting.GRAY));
            }
            if (station.groundDockStatus() != DockLinkStatus.LINKED || station.groundDockPos().isEmpty()) {
                return Optional.of(Component.translatable(
                        "gui.create_aeronautics_automated_logistics.ship_transponder.failure_reason.missing_dock.station_named",
                        Component.literal(stationSnapshot.get().stationName())
                ).withStyle(ChatFormatting.GRAY));
            }
        }
        return Optional.empty();
    }

    private Optional<CargoFailureContext> inferCargoFailureContext(ShipTransponderBlockEntity transponder, PlaybackFailure failure) {
        if (failure != PlaybackFailure.CARGO_STORAGE_MISSING && failure != PlaybackFailure.CARGO_CONDITION_TIMEOUT) {
            return Optional.empty();
        }
        LinkedCargoSummary shipSummary = transponder.linkedCargoSummary();
        for (AirshipScheduleEntry entry : transponder.ownedSchedule().entries()) {
            for (List<AirshipScheduleCondition> group : entry.effectiveConditionGroups()) {
                for (AirshipScheduleCondition condition : group) {
                    WaitCondition waitCondition = condition.waitCondition();
                    if (!isCargoWaitType(waitCondition.type())) {
                        continue;
                    }
                    LinkedCargoSummary summary = waitCondition.cargoTarget() == CargoWaitTarget.SHIP_CARGO
                            ? shipSummary
                            : stationCargoSummary(transponder, entry);
                    if (summary == null || summary.staleLinks() > 0 || !summarySupportsWait(summary, waitCondition.type())) {
                        return Optional.of(new CargoFailureContext(waitCondition.cargoTarget(), waitCondition.type()));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private LinkedCargoSummary stationCargoSummary(ShipTransponderBlockEntity transponder, AirshipScheduleEntry entry) {
        if (transponder.getLevel() == null || entry.targetStationId().isEmpty()) {
            return null;
        }
        return entry.targetStationId()
                .flatMap(AirshipStationRegistry::snapshot)
                .flatMap(snapshot -> transponder.getLevel().getBlockEntity(snapshot.stationPos()) instanceof AirshipStationBlockEntity station
                        ? Optional.of(station.linkedCargoSummary())
                        : Optional.empty())
                .orElse(null);
    }

    private boolean summarySupportsWait(LinkedCargoSummary summary, WaitConditionType type) {
        return switch (type) {
            case UNTIL_ITEM_THRESHOLD, UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_EMPTY, UNTIL_FULL -> summary.itemLinks() > 0;
            case UNTIL_FLUID_THRESHOLD, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL -> summary.fluidLinks() > 0;
            default -> true;
        };
    }

    private boolean isCargoWaitType(WaitConditionType type) {
        return type == WaitConditionType.UNTIL_ITEM_THRESHOLD
                || type == WaitConditionType.UNTIL_FLUID_THRESHOLD
                || type == WaitConditionType.UNTIL_ITEM_EMPTY
                || type == WaitConditionType.UNTIL_ITEM_FULL
                || type == WaitConditionType.UNTIL_FLUID_EMPTY
                || type == WaitConditionType.UNTIL_FLUID_FULL
                || type == WaitConditionType.UNTIL_EMPTY
                || type == WaitConditionType.UNTIL_FULL;
    }

    private boolean entryRequiresDockLock(AirshipScheduleEntry entry) {
        return entry.effectiveConditionGroups().stream()
                .flatMap(List::stream)
                .map(AirshipScheduleCondition::waitCondition)
                .anyMatch(this::requiresDockLock);
    }

    private boolean requiresDockLock(WaitCondition waitCondition) {
        return switch (waitCondition.type()) {
            case UNTIL_DOCKED,
                    UNTIL_IDLE,
                    UNTIL_ITEM_THRESHOLD,
                    UNTIL_FLUID_THRESHOLD,
                    UNTIL_ITEM_EMPTY,
                    UNTIL_ITEM_FULL,
                    UNTIL_FLUID_EMPTY,
                    UNTIL_FLUID_FULL,
                    UNTIL_EMPTY,
                    UNTIL_FULL -> true;
            default -> false;
        };
    }

    private Component cargoFailureSideText(CargoFailureContext context) {
        return Component.translatable(
                context.target() == CargoWaitTarget.STATION_CARGO
                        ? "gui.create_aeronautics_automated_logistics.cargo_failure.side.station"
                        : "gui.create_aeronautics_automated_logistics.cargo_failure.side.ship"
        );
    }

    private Component cargoFailureResourceText(CargoFailureContext context) {
        return switch (context.waitType()) {
            case UNTIL_FLUID_THRESHOLD, UNTIL_FLUID_EMPTY, UNTIL_FLUID_FULL ->
                    Component.translatable("gui.create_aeronautics_automated_logistics.cargo_failure.resource.fluid");
            case UNTIL_ITEM_THRESHOLD, UNTIL_ITEM_EMPTY, UNTIL_ITEM_FULL, UNTIL_EMPTY, UNTIL_FULL ->
                    Component.translatable("gui.create_aeronautics_automated_logistics.cargo_failure.resource.item");
            default -> Component.translatable("gui.create_aeronautics_automated_logistics.cargo_failure.resource.cargo");
        };
    }

    private Component failureStatusText(PlaybackFailure failure) {
        return switch (failure) {
            case INVALID_ROUTE, DIMENSION_MISMATCH -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.no_route_status");
            case STATION_MISSING -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.station_missing_status");
            case VEHICLE_MISSING, VEHICLE_UNLOADED, MISSING_CONTROLLER -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.ship_missing_status");
            case START_TOO_FAR_FROM_ROUTE -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.start_blocked_status");
            case WRONG_START_STATION -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.wrong_station_status");
            case COLLISION_OR_OBSTRUCTION, STUCK -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.blocked_status");
            case MISSING_DOCK, AMBIGUOUS_DOCK, DOCK_LOCK_FAILED -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.dock_problem_status");
            case REDSTONE_LINK_UNCONFIGURED, CARGO_STORAGE_MISSING, CARGO_CONDITION_TIMEOUT, MOVEMENT_FAILURE, ALREADY_RUNNING, MAX_ACTIVE_VEHICLES_REACHED -> Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.needs_attention_status");
        };
    }

    public boolean shouldSuggestRecordingMode(Player player) {
        if (!isServerView(player)) {
            StatusSnapshot snapshot = resolvedClientStatusSnapshot(player);
            if (snapshot.scheduleActive() || snapshot.scheduleHeld()) {
                return false;
            }
            return !snapshot.readyRoute();
        }
        return !hasReadyInstalledRoutePlan(player);
    }

    private boolean hasReadyInstalledRoutePlan(Player player) {
        if (!(player.level() instanceof ServerLevel level)
                || !(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return false;
        }
        return AutomatedLogisticsServices.SCHEDULES.hasValidScheduleDefinition(level, transponder, resolveOwnedSchedule(player));
    }

    private boolean hasResolvableInstalledRouteChain(Player player) {
        if (!(player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return false;
        }
        AirshipSchedule schedule = resolveOwnedSchedule(player);
        if (schedule.entries().isEmpty()) {
            return false;
        }
        return hasRecordedRouteChain(player, schedule, transponder.transponderId());
    }

    private AirshipSchedule resolveOwnedSchedule(Player player) {
        if (!isServerView(player) && clientOwnedScheduleOverride != null) {
            return clientOwnedScheduleOverride;
        }
        if (player.level().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder) {
            AirshipSchedule liveSchedule = transponder.ownedSchedule();
            if (!liveSchedule.entries().isEmpty()
                    || liveSchedule.equals(initialOwnedSchedule)
                    || initialOwnedSchedule.entries().isEmpty()) {
                return liveSchedule;
            }
        }
        return initialOwnedSchedule;
    }

    private boolean hasRecordedRouteChain(Player player, AirshipSchedule schedule, UUID transponderId) {
        if (schedule.entries().isEmpty()) {
            return false;
        }
        AirshipScheduleEntry firstEntry = schedule.entries().getFirst();
        if (firstEntry.targetStationId().isEmpty()) {
            return false;
        }
        Optional<RouteSegment> firstSegment = player instanceof ServerPlayer serverPlayer
                ? firstEntry.pinnedSegmentId()
                        .flatMap(segmentId -> AutomatedLogisticsServices.ROUTES.byId(serverPlayer.serverLevel().getServer(), segmentId))
                        .filter(candidate -> candidate.endStationId().equals(firstEntry.targetStationId().get()))
                        .filter(candidate -> candidate.dimension().equals(player.level().dimension()))
                        .filter(candidate -> candidate.transponderId().equals(transponderId))
                : firstEntry.pinnedSegmentId()
                        .flatMap(RouteSegmentRegistry::byId)
                        .filter(candidate -> candidate.endStationId().equals(firstEntry.targetStationId().get()))
                        .filter(candidate -> candidate.dimension().equals(player.level().dimension()))
                        .filter(candidate -> candidate.transponderId().equals(transponderId));
        if (firstSegment.isEmpty()) {
            return false;
        }

        UUID currentStationId = firstEntry.targetStationId().get();
        for (int i = 1; i < schedule.entries().size(); i++) {
            AirshipScheduleEntry entry = schedule.entries().get(i);
            if (entry.targetStationId().isEmpty()) {
                return false;
            }
            UUID fromStationId = currentStationId;
            UUID nextStationId = entry.targetStationId().get();
            Optional<RouteSegment> segment = player instanceof ServerPlayer serverPlayer
                    ? entry.pinnedSegmentId()
                            .flatMap(segmentId -> AutomatedLogisticsServices.ROUTES.byId(serverPlayer.serverLevel().getServer(), segmentId))
                            .filter(candidate -> candidate.startStationId().equals(fromStationId))
                            .filter(candidate -> candidate.endStationId().equals(nextStationId))
                            .filter(candidate -> candidate.dimension().equals(player.level().dimension()))
                            .filter(candidate -> candidate.transponderId().equals(transponderId))
                            .or(() -> RouteSegmentResolver.newestFor(
                                    serverPlayer.serverLevel().getServer(),
                                    fromStationId,
                                    nextStationId,
                                    player.level().dimension(),
                                    Optional.of(transponderId)
                            ))
                    : entry.pinnedSegmentId()
                            .flatMap(RouteSegmentRegistry::byId)
                            .filter(candidate -> candidate.startStationId().equals(fromStationId))
                            .filter(candidate -> candidate.endStationId().equals(nextStationId))
                            .filter(candidate -> candidate.dimension().equals(player.level().dimension()))
                            .filter(candidate -> candidate.transponderId().equals(transponderId))
                            .or(() -> RouteSegmentResolver.newestFor(
                                    fromStationId,
                                    nextStationId,
                                    player.level().dimension(),
                                    Optional.of(transponderId)
                            ));
            if (segment.isEmpty()) {
                return false;
            }
            currentStationId = nextStationId;
        }
        return true;
    }

    private void showMenuWarning(Player player, Component message) {
        actionBar(player, message);
    }

    private void actionBar(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            SetMenuActionBarMessagePayload.send(serverPlayer, message);
        }
    }

    private boolean beginLinkDock(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (!hasOperationalShip(player)) {
            showOperationalShipRequired(player, transponder);
            return false;
        }
        DockLinkInteractionService.beginTransponderLink(player, transponder.getBlockPos());
        return true;
    }

    private boolean clearDockLink(net.minecraft.server.level.ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (DockLinkInteractionService.hasPendingTransponderLink(player, transponder.getBlockPos())) {
            return DockLinkInteractionService.cancelPending(player);
        }
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.locked_while_running"));
            return false;
        }
        transponder.clearShipDockLink();
        DockLinkInteractionService.clearPending(player);
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.dock_link.cleared"));
        return true;
    }

    private boolean editInstalledSchedule(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        AirshipSchedule schedule = transponder.ownedSchedule();
        player.openMenu(
                new net.minecraft.world.SimpleMenuProvider(
                        (containerId, inventory, ignoredPlayer) -> new AirshipScheduleMenu(containerId, inventory, transponder.getBlockPos(), false),
                        Component.literal(schedule.title())
                ),
                buffer -> {
                    buffer.writeBoolean(true);
                    buffer.writeBlockPos(transponder.getBlockPos());
                    buffer.writeBoolean(false);
                    buffer.writeNbt(AirshipScheduleNbtSerializer.write(schedule));
                }
        );
        return true;
    }

    private boolean linkCargo(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (!hasOperationalShip(player)) {
            showOperationalShipRequired(player, transponder);
            return false;
        }
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.locked_while_running"));
            AllSoundEvents.DENY.playOnServer(player.level(), transponder.getBlockPos(), 0.5f, 1.0f);
            return false;
        }
        CargoLinkInteractionService.beginTransponderLink(player, transponder.getBlockPos());
        return true;
    }

    private void showOperationalShipRequired(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.transponder.requires_valid_ship"));
        AllSoundEvents.DENY.playOnServer(player.level(), transponder.getBlockPos(), 0.5f, 1.0f);
    }

    private boolean clearCargo(ServerPlayer player, ShipTransponderBlockEntity transponder) {
        if (AutomatedLogisticsServices.SCHEDULES.isRunning(transponder.transponderId())) {
            actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.locked_while_running"));
            AllSoundEvents.DENY.playOnServer(player.level(), transponder.getBlockPos(), 0.5f, 1.0f);
            return false;
        }
        if (CargoLinkInteractionService.hasPendingTransponderLink(player, transponder.getBlockPos())) {
            return CargoLinkInteractionService.cancelPending(player);
        }
        if (!transponder.clearLinkedCargo()) {
            return false;
        }
        actionBar(player, Component.translatable("message.create_aeronautics_automated_logistics.cargo_link.cleared"));
        AllSoundEvents.CONFIRM.playOnServer(player.level(), transponder.getBlockPos(), 0.6f, 1.0f);
        return true;
    }

    private void addPlayerInventorySlots(Inventory inventory, int leftX, int topY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, leftX + col * 18, topY + row * 18));
            }
        }
        int hotbarY = topY + 58;
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, leftX + col * 18, hotbarY));
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(menuPlayer instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(serverPlayer.serverLevel().getBlockEntity(transponderPos) instanceof ShipTransponderBlockEntity transponder)) {
            return;
        }
        StatusSnapshot snapshot = RuntimeProjectionService.buildTransponderStatusSnapshot(
                serverPlayer,
                transponder,
                initialRecordingMode
        );
        int snapshotHash = snapshot.hashCode();
        List<net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoEntry> linkedCargoEntries = List.copyOf(transponder.linkedCargo());
        int linkedCargoEntriesHash = linkedCargoEntries.hashCode();
        if (snapshotHash == lastSyncedStatusSnapshotHash && linkedCargoEntriesHash == lastSyncedLinkedCargoEntriesHash) {
            return;
        }
        lastSyncedStatusSnapshotHash = snapshotHash;
        lastSyncedLinkedCargoEntriesHash = linkedCargoEntriesHash;
        PacketDistributor.sendToPlayer(
                serverPlayer,
                new SyncTransponderMenuStatePayload(transponderPos, snapshot, linkedCargoEntries)
        );
    }

    public record StatusTooltipLine(String text, int color) {
        public StatusTooltipLine {
            text = text == null ? "" : text;
        }
    }

    private StatusSnapshot resolvedClientStatusSnapshot() {
        return clientStatusSnapshotOverride == null ? initialStatusSnapshot : clientStatusSnapshotOverride;
    }

    private StatusSnapshot resolvedClientStatusSnapshot(Player player) {
        StatusSnapshot snapshot = resolvedClientStatusSnapshot();
        if (isServerView(player)
                || snapshot.scheduleActive()
                || snapshot.scheduleHeld()
                || !snapshot.operationalShip()
                || !snapshot.readyRoute()) {
            return snapshot;
        }

        AirshipSchedule clientSchedule = resolveOwnedSchedule(player);
        if (clientSchedule.entries().isEmpty() || hasResolvableInstalledRouteChain(player)) {
            return snapshot;
        }

        List<StatusTooltipLine> tooltip = List.of(
                new StatusTooltipLine(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.partial_route.1").getString(),
                        0xFFFFD98A
                ),
                new StatusTooltipLine(
                        Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.status_hover.partial_route.2").getString(),
                        0xFF9AA6B2
                )
        );
        return new StatusSnapshot(
                snapshot.shipName(),
                Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.partial_route_status").getString(),
                0xFFFFD98A,
                tooltip,
                snapshot.scheduleActive(),
                snapshot.scheduleHeld(),
                true,
                false,
                false,
                snapshot.canControl(),
                snapshot.operationalShip(),
                snapshot.dockView(),
                snapshot.cargoView()
        );
    }

    public record StatusSnapshot(
            String shipName,
            String text,
            int color,
            List<StatusTooltipLine> tooltip,
            boolean scheduleActive,
            boolean scheduleHeld,
            boolean hasOwnedStops,
            boolean readyRoute,
            boolean canPreviewRoute,
            boolean canControl,
            boolean operationalShip,
            ViewSnapshot dockView,
            ViewSnapshot cargoView
    ) {
        public StatusSnapshot {
            shipName = shipName == null ? "" : shipName;
            text = text == null ? Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.idle").getString() : text;
            tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
            dockView = dockView == null ? ViewSnapshot.dockNone() : dockView;
            cargoView = cargoView == null ? ViewSnapshot.cargoNone() : cargoView;
        }

        public static StatusSnapshot idle() {
            return new StatusSnapshot(
                    "",
                    Component.translatable("gui.create_aeronautics_automated_logistics.ship_transponder.idle").getString(),
                    0xAFC7DE,
                    List.of(),
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    ViewSnapshot.dockNone(),
                    ViewSnapshot.cargoNone()
            );
        }
    }

    public record ViewSnapshot(String text, int color, boolean active, List<StatusTooltipLine> tooltip) {
        public ViewSnapshot {
            text = text == null ? "" : text;
            tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
        }

        public static ViewSnapshot dockNone() {
            return new ViewSnapshot(
                    Component.translatable("gui.create_aeronautics_automated_logistics.dock.not_connected").getString(),
                    0xFFAFC7DE,
                    false,
                    List.of()
            );
        }

        public static ViewSnapshot cargoNone() {
            String text = Component.translatable("gui.create_aeronautics_automated_logistics.cargo.none").getString();
            return new ViewSnapshot(
                    text,
                    0xFFFFD98A,
                    false,
                    List.of(
                            new StatusTooltipLine(text, ChatFormatting.YELLOW.getColor()),
                            new StatusTooltipLine(
                                    Component.translatable("gui.create_aeronautics_automated_logistics.cargo.hover.none").getString(),
                                    ChatFormatting.GRAY.getColor()
                            )
                    )
            );
        }
    }
}

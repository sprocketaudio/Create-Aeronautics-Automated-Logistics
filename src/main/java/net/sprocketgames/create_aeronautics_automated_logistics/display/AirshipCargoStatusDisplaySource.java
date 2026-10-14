package net.sprocketgames.create_aeronautics_automated_logistics.display;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.cargo.LinkedCargoSummary;

/** Station-wide cargo-link output for Create Display Links. */
public final class AirshipCargoStatusDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof AirshipStationBlockEntity station)) {
            return EMPTY;
        }
        LinkedCargoSummary cargo = station.linkedCargoSummary();
        String status = !cargo.hasLinks()
                ? "Cargo: Not linked"
                : cargo.staleLinks() > 0
                        ? "Cargo: " + cargo.validLinks() + " ready, " + cargo.staleLinks() + " missing"
                        : "Cargo: " + cargo.validLinks() + " linked";
        return List.of(Component.literal(station.stationName()), Component.literal(status));
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 40;
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.registry;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.display.AirshipStationSummaryDisplaySource;

public final class ModDisplaySources {
    public static final DeferredRegister<DisplaySource> DISPLAY_SOURCES = DeferredRegister.create(
            CreateRegistries.DISPLAY_SOURCE,
            CreateAeronauticsAutomatedLogistics.MOD_ID
    );

    public static final DeferredHolder<DisplaySource, DisplaySource> AIRSHIP_STATION_SUMMARY = DISPLAY_SOURCES.register(
            "airship_station_summary",
            () -> {
                DisplaySource source = new AirshipStationSummaryDisplaySource();
                DisplaySource.BY_BLOCK.add(ModBlocks.AIRSHIP_STATION.value(), source);
                return source;
            }
    );

    private ModDisplaySources() {
    }

    public static void register(IEventBus eventBus) {
        DISPLAY_SOURCES.register(eventBus);
    }
}

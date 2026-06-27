package net.sprocketgames.create_aeronautics_automated_logistics.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;

public final class ModCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateAeronauticsAutomatedLogistics.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LOGISTICS_TAB =
            CREATIVE_MODE_TABS.register("logistics", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_aeronautics_automated_logistics"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.SHIP_TRANSPONDER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.AIRSHIP_STATION.get());
                        output.accept(ModItems.SHIP_TRANSPONDER.get());
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    private ModCreativeTabs() {
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.registry;

import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;

public final class ModSimulatedSections {
    public static final ResourceLocation AUTOMATED_LOGISTICS_SECTION =
            ResourceLocation.fromNamespaceAndPath(CreateAeronauticsAutomatedLogistics.MOD_ID, "automated_logistics");

    private static boolean bootstrapped;

    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;

        register(ModItems.AIRSHIP_STATION);
        register(ModItems.TRAIN_STATION);
        register(ModItems.SHIP_TRANSPONDER);
        register(ModItems.LOGISTICS_TERMINAL);
        register(ModItems.DRIVE_MODULE);
    }

    private static void register(DeferredItem<? extends Item> item) {
        SimulatedRegistrate.TAB_ITEMS.add(item::get);
        SimulatedRegistrate.ITEM_TO_SECTION.put(item.getId(), AUTOMATED_LOGISTICS_SECTION);
    }

    private ModSimulatedSections() {
    }
}

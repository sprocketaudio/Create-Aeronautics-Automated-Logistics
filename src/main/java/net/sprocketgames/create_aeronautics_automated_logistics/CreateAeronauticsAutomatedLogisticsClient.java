package net.sprocketgames.create_aeronautics_automated_logistics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.createmod.ponder.foundation.PonderIndex;
import net.sprocketgames.create_aeronautics_automated_logistics.client.ponder.AutomatedLogisticsPonderPlugin;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.AirshipScheduleScreen;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.AirshipStationScreen;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.LogisticsTerminalClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.LogisticsTerminalScreen;
import net.sprocketgames.create_aeronautics_automated_logistics.client.screen.ShipTransponderScreen;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AdvancedTransponderOverlayRenderer;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AdvancedTransponderOverlayClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.AutomatedShipVisualClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.DockLinkPromptClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.CargoLinkPromptClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsClientOverlays;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsTerminalPreviewClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.LogisticsTerminalTopPreviewRenderer;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.MenuActionBarClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.visual.ShipTransponderHighlightClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.client.map.FtbChunksShipMapCompat;
import net.sprocketgames.create_aeronautics_automated_logistics.client.map.ShipMapClientState;
import net.sprocketgames.create_aeronautics_automated_logistics.registry.ModMenus;

@Mod(value = CreateAeronauticsAutomatedLogistics.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = CreateAeronauticsAutomatedLogistics.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CreateAeronauticsAutomatedLogisticsClient {
    public CreateAeronauticsAutomatedLogisticsClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        container.registerConfig(ModConfig.Type.CLIENT, AutomatedLogisticsClientConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(CreateAeronauticsAutomatedLogisticsClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(CreateAeronauticsAutomatedLogisticsClient::onRenderGuiPost);
        NeoForge.EVENT_BUS.addListener(CreateAeronauticsAutomatedLogisticsClient::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(CreateAeronauticsAutomatedLogisticsClient::onScreenRenderPost);
        PonderIndex.addPlugin(new AutomatedLogisticsPonderPlugin());
        FtbChunksShipMapCompat.register();
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.AIRSHIP_STATION.get(), AirshipStationScreen::new);
        event.register(ModMenus.AIRSHIP_SCHEDULE.get(), AirshipScheduleScreen::new);
        event.register(ModMenus.SHIP_TRANSPONDER.get(), ShipTransponderScreen::new);
        event.register(ModMenus.LOGISTICS_TERMINAL.get(), LogisticsTerminalScreen::new);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        AdvancedTransponderOverlayClientState.clearIfWorldMissing();
        AutomatedShipVisualClientState.clearIfWorldMissing();
        DockLinkPromptClientState.tick();
        CargoLinkPromptClientState.tick();
        ShipTransponderHighlightClientState.tick();
        MenuActionBarClientState.tick();
        LogisticsClientOverlays.refresh();
        ShipMapClientState.clearIfWorldMissing();
        LogisticsTerminalClientState.clearIfWorldMissing();
        LogisticsTerminalPreviewClientState.clearIfWorldMissing();
    }

    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        renderOverlayMessages(event.getGuiGraphics(), false);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        AdvancedTransponderOverlayRenderer.render(event);
        LogisticsTerminalTopPreviewRenderer.render(event);
    }

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        FtbChunksShipMapCompat.renderLargeMapOverlay(event);
        renderOverlayMessages(event.getGuiGraphics(), true);
    }

    private static void renderOverlayMessages(net.minecraft.client.gui.GuiGraphics guiGraphics, boolean screenOpen) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if ((minecraft.screen != null) != screenOpen) {
            return;
        }
        DockLinkPromptClientState.render(guiGraphics);
        CargoLinkPromptClientState.render(guiGraphics);
        MenuActionBarClientState.render(guiGraphics);
    }
}

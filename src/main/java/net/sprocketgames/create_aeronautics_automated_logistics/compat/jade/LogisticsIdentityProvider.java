package net.sprocketgames.create_aeronautics_automated_logistics.compat.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.create_aeronautics_automated_logistics.CreateAeronauticsAutomatedLogistics;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.AirshipStationBlockEntity;
import net.sprocketgames.create_aeronautics_automated_logistics.block.entity.ShipTransponderBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

enum LogisticsIdentityProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            CreateAeronauticsAutomatedLogistics.MOD_ID,
            "logistics_identity"
    );

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlockEntity() instanceof AirshipStationBlockEntity station) {
            tooltip.add(labelledName(
                    "jade.create_aeronautics_automated_logistics.station",
                    station.stationName()
            ));
        } else if (accessor.getBlockEntity() instanceof ShipTransponderBlockEntity transponder) {
            tooltip.add(labelledName(
                    "jade.create_aeronautics_automated_logistics.ship",
                    transponder.shipName()
            ));
        }
    }

    private static Component labelledName(String translationKey, String name) {
        return Component.translatable(
                translationKey,
                Component.literal(name).withStyle(ChatFormatting.WHITE)
        ).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}

package net.sprocketgames.create_aeronautics_automated_logistics.compat.jade;

import net.sprocketgames.create_aeronautics_automated_logistics.block.AirshipStationBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.block.ShipTransponderBlock;
import net.sprocketgames.create_aeronautics_automated_logistics.block.TrainStationBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public final class AutomatedLogisticsJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(LogisticsIdentityProvider.INSTANCE, AirshipStationBlock.class);
        registration.registerBlockComponent(LogisticsIdentityProvider.INSTANCE, TrainStationBlock.class);
        registration.registerBlockComponent(LogisticsIdentityProvider.INSTANCE, ShipTransponderBlock.class);
    }
}

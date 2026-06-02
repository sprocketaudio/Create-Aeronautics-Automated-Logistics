package net.sprocketgames.create_aeronautics_automated_logistics.service;

import net.sprocketgames.create_aeronautics_automated_logistics.route.CargoWaitTarget;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;

public record CargoFailureContext(
        CargoWaitTarget target,
        WaitConditionType waitType
) {
}

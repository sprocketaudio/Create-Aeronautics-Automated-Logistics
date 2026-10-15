package net.sprocketgames.create_aeronautics_automated_logistics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class VehicleRoutePlaybackServiceTest {
    @Test
    void dockDistanceUsesNearestPointOnVehicleBounds() {
        AABB shipBounds = new AABB(10.0D, 20.0D, 30.0D, 20.0D, 25.0D, 40.0D);

        assertEquals(0.0D, VehicleRoutePlaybackService.distanceToSqr(
                shipBounds,
                new Vec3(15.0D, 22.0D, 35.0D)
        ));
        assertEquals(25.0D, VehicleRoutePlaybackService.distanceToSqr(
                shipBounds,
                new Vec3(25.0D, 22.0D, 35.0D)
        ));
        assertEquals(50.0D, VehicleRoutePlaybackService.distanceToSqr(
                shipBounds,
                new Vec3(25.0D, 30.0D, 35.0D)
        ));
    }
}

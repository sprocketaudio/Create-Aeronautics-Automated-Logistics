package net.sprocketgames.create_aeronautics_automated_logistics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.sprocketgames.create_aeronautics_automated_logistics.route.WaitConditionType;
import org.junit.jupiter.api.Test;

class StationWaitRuntimeTest {
    @Test
    void cargoWaitClassificationIncludesAllCargoConditionTypesOnly() {
        assertTrue(StationWaitRuntime.isCargoWaitType(WaitConditionType.UNTIL_ITEM_THRESHOLD));
        assertTrue(StationWaitRuntime.isCargoWaitType(WaitConditionType.UNTIL_FLUID_THRESHOLD));
        assertTrue(StationWaitRuntime.isCargoWaitType(WaitConditionType.UNTIL_ITEM_EMPTY));
        assertTrue(StationWaitRuntime.isCargoWaitType(WaitConditionType.UNTIL_ITEM_FULL));
        assertTrue(StationWaitRuntime.isCargoWaitType(WaitConditionType.UNTIL_FLUID_EMPTY));
        assertTrue(StationWaitRuntime.isCargoWaitType(WaitConditionType.UNTIL_FLUID_FULL));
        assertTrue(StationWaitRuntime.isCargoWaitType(WaitConditionType.UNTIL_EMPTY));
        assertTrue(StationWaitRuntime.isCargoWaitType(WaitConditionType.UNTIL_FULL));

        assertFalse(StationWaitRuntime.isCargoWaitType(WaitConditionType.TIMED));
        assertFalse(StationWaitRuntime.isCargoWaitType(WaitConditionType.UNTIL_DOCKED));
        assertFalse(StationWaitRuntime.isCargoWaitType(WaitConditionType.UNTIL_IDLE));
        assertFalse(StationWaitRuntime.isCargoWaitType(WaitConditionType.REDSTONE_LINK));
        assertFalse(StationWaitRuntime.isCargoWaitType(WaitConditionType.TIME_OF_DAY));
    }

    @Test
    void conditionRuntimeStatePreservesSavedCountersAndTicksDown() {
        ConditionRuntimeState state = new ConditionRuntimeState(
                3,
                3,
                5,
                7,
                false,
                Optional.empty()
        );

        assertFalse(state.tickWait());
        assertEquals(2, state.waitTicksRemaining);
        assertFalse(state.tickIdleTimeout());
        assertEquals(4, state.idleTimeoutTicksRemaining);
        assertFalse(state.tickCargoTimeout());
        assertEquals(6, state.cargoTimeoutTicksRemaining);
    }

    @Test
    void idleResetRestartsStabilityWindowWithoutDroppingBelowOneTick() {
        ConditionRuntimeState state = new ConditionRuntimeState(
                0,
                0,
                0,
                0,
                false,
                Optional.empty()
        );

        state.resetIdleWait();

        assertEquals(1, state.waitTicksRemaining);
    }
}

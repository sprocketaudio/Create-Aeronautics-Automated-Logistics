package net.sprocketgames.create_aeronautics_automated_logistics.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WaitConditionTest {
    @Test
    void cargoFactoriesDefaultToTwoSecondsStable() {
        int expected = WaitCondition.DEFAULT_CARGO_STABILITY_TICKS;

        assertEquals(expected, WaitCondition.defaultCargoStabilityTicks(WaitConditionType.UNTIL_ITEM_THRESHOLD));
        assertEquals(expected, WaitCondition.defaultCargoStabilityTicks(WaitConditionType.UNTIL_FLUID_THRESHOLD));
        assertEquals(expected, WaitCondition.defaultCargoStabilityTicks(WaitConditionType.UNTIL_ITEM_EMPTY));
        assertEquals(expected, WaitCondition.defaultCargoStabilityTicks(WaitConditionType.UNTIL_ITEM_FULL));
        assertEquals(expected, WaitCondition.defaultCargoStabilityTicks(WaitConditionType.UNTIL_FLUID_EMPTY));
        assertEquals(expected, WaitCondition.defaultCargoStabilityTicks(WaitConditionType.UNTIL_FLUID_FULL));
    }

    @Test
    void explicitZeroStabilityIsPreserved() {
        assertEquals(0, WaitCondition.sanitizeCargoStabilityTicks(0));
        assertEquals(0, WaitCondition.sanitizeCargoStabilityTicks(-1));
    }
}

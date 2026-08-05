package net.sprocketgames.create_aeronautics_automated_logistics.dock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DockHandshakeServiceTest {
    @Test
    void lockClockUsesElapsedServerTimeWithoutConsumingMoreThanOneTickPerCall() {
        DockHandshakeService.LockClock clock = new DockHandshakeService.LockClock(3);

        assertEquals(3, clock.tick(1_000_000_000L));
        assertEquals(3, clock.tick(1_025_000_000L));
        assertEquals(2, clock.tick(1_050_000_000L));
        assertEquals(1, clock.tick(2_000_000_000L));
    }

    @Test
    void restoredHigherTimeoutRestartsClockSampling() {
        DockHandshakeService.LockClock clock = new DockHandshakeService.LockClock(2);
        clock.tick(1_000_000_000L);
        assertEquals(1, clock.tick(1_050_000_000L));

        clock.restoreIfGreater(5);

        assertEquals(5, clock.remainingTicks());
        assertEquals(5, clock.tick(2_000_000_000L));
    }

    @Test
    void poseProofRequiresFiniteNonNegativeErrors() {
        DockPoseProof proof = new DockPoseProof(true, true, 0.2D, 2.5D);

        assertTrue(proof.confirmed());
        assertThrows(
                IllegalArgumentException.class,
                () -> new DockPoseProof(false, true, Double.NaN, 0.0D)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new DockPoseProof(true, false, 0.0D, -1.0D)
        );
    }
}

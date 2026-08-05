package net.sprocketgames.create_aeronautics_automated_logistics.dock;

public record DockPoseProof(
        boolean positionConfirmed,
        boolean rotationConfirmed,
        double positionError,
        double rotationErrorDegrees
) {
    public DockPoseProof {
        if (!Double.isFinite(positionError) || positionError < 0.0D) {
            throw new IllegalArgumentException("positionError must be finite and non-negative");
        }
        if (!Double.isFinite(rotationErrorDegrees) || rotationErrorDegrees < 0.0D) {
            throw new IllegalArgumentException("rotationErrorDegrees must be finite and non-negative");
        }
    }

    public boolean confirmed() {
        return positionConfirmed && rotationConfirmed;
    }
}

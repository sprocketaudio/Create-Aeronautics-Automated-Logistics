package net.sprocketgames.create_aeronautics_automated_logistics.service;

record DriveDemand(
        double lift,
        double forwardBack,
        double leftRight,
        double turn,
        double forwardVelocity,
        double rightVelocity,
        double yawVelocity,
        double uprightDot
) {
}

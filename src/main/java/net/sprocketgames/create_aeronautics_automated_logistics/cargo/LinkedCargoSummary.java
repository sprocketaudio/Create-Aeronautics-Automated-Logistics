package net.sprocketgames.create_aeronautics_automated_logistics.cargo;

public record LinkedCargoSummary(
        int totalLinks,
        int validLinks,
        int staleLinks,
        int itemLinks,
        int fluidLinks
) {
    public boolean hasLinks() {
        return totalLinks > 0;
    }
}

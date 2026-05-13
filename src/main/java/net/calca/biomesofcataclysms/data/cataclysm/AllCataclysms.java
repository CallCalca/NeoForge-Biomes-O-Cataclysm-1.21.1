package net.calca.biomesofcataclysms.data.cataclysm;

import net.calca.biomesofcataclysms.data.ModVariables;
import net.minecraft.server.level.ServerLevel;

public enum AllCataclysms {
    DESTROYED,
    FLOODED,
    SUN_BURNT;

    public boolean usesChunkDestruction() {
        return this == DESTROYED;
    }

    public boolean startsInDynamicOnly() {
        return this == FLOODED || this == SUN_BURNT;
    }
}

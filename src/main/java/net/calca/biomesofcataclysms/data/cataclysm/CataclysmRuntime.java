package net.calca.biomesofcataclysms.data.cataclysm;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class CataclysmRuntime extends SavedData {
    public int floodLevel = 0;
    public int floodTick = 0;
    public int burnTick = 0;

    public final Map<String, Integer> biomeFloodLevels = new HashMap<>();
    public final Map<String, Integer> biomeHeatLevels = new HashMap<>();

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return null;
    }

    // load/save + syncData + setDirty
}
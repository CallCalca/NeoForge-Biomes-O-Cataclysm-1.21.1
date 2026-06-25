package net.calca.biomesofcataclysms.data.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;

public class ChunkInstance {
    public final ResourceKey<Level> dimension;
    public final ChunkPos pos;
    public Set<String> biomeIds = new HashSet<>();
    public Set<String> clearedBiomes = new HashSet<>();
    public String activeBiome = null;
    public int activeBiomeStep = 0;

    public int lastFloodHeight = -64;

    public boolean initialWave;   // is it queued in the INITIAL QUEUE?
    public boolean dynamic;       // is it queued in the DYNAMIC QUEUE?

    public long firstSeenTick;
    public ChunkState state = ChunkState.QUEUED;

    public long lastSeenTick;
    public double priorityScore;

    public ChunkInstance(ResourceKey<Level> dimension, ChunkPos pos, ServerLevel serverLevel) {
        this.dimension = dimension;
        this.pos = pos;

        this.biomeIds = collectBiomesInChunk(serverLevel, pos);
        this.firstSeenTick = serverLevel.getGameTime();
    }

    private ChunkInstance(ResourceKey<Level> dimension, ChunkPos pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    //This method is used to collect all biomes inside a specific chunk (a chunk can contain various biomes).
    private Set<String> collectBiomesInChunk(ServerLevel level, ChunkPos pos) {
        Set<String> biomes = new HashSet<>();

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();

        for (int xOff = 0; xOff < 16; xOff += 2) {
            for (int zOff = 0; zOff < 16; zOff += 2) {
                for (int y = minY; y < maxY; y += 4) {
                    BlockPos samplePos = new BlockPos(minX + xOff, y, minZ + zOff);

                    level.getBiome(samplePos).unwrapKey().ifPresent(key -> {
                        biomes.add(key.location().toString());
                    });
                }
            }
        }

        return biomes;
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();

        Set<String> biomeIdsCopy;
        Set<String> clearedBiomesCopy;
        String activeBiomeCopy;
        int activeBiomeStepCopy;
        int lastFloodHeightCopy;
        boolean initialWaveCopy;
        boolean dynamicCopy;
        long firstSeenTickCopy;
        ChunkState stateCopy;
        long lastSeenTickCopy;
        double priorityScoreCopy;

        synchronized (this) {
            biomeIdsCopy = new HashSet<>(biomeIds);
            clearedBiomesCopy = new HashSet<>(clearedBiomes);
            activeBiomeCopy = activeBiome;
            activeBiomeStepCopy = activeBiomeStep;
            lastFloodHeightCopy = lastFloodHeight;
            initialWaveCopy = initialWave;
            dynamicCopy = dynamic;
            firstSeenTickCopy = firstSeenTick;
            stateCopy = state;
            lastSeenTickCopy = lastSeenTick;
            priorityScoreCopy = priorityScore;
        }

        tag.putString("dimension", dimension.location().toString());
        tag.putLong("chunkPos", pos.toLong());

        ListTag biomeList = new ListTag();
        for (String biome : biomeIdsCopy) {
            biomeList.add(StringTag.valueOf(biome));
        }
        tag.put("biomeIds", biomeList);

        ListTag clearedList = new ListTag();
        for (String biome : clearedBiomesCopy) {
            clearedList.add(StringTag.valueOf(biome));
        }
        tag.put("clearedBiomes", clearedList);

        tag.putBoolean("hasActiveBiome", activeBiomeCopy != null);
        if (activeBiomeCopy != null) {
            tag.putString("activeBiome", activeBiomeCopy);
        }

        tag.putInt("activeBiomeStep", activeBiomeStepCopy);
        tag.putInt("lastFloodHeight", lastFloodHeightCopy);
        tag.putBoolean("initialWave", initialWaveCopy);
        tag.putBoolean("dynamic", dynamicCopy);

        tag.putLong("firstSeenTick", firstSeenTickCopy);
        tag.putString("state", stateCopy.name());
        tag.putLong("lastSeenTick", lastSeenTickCopy);
        tag.putDouble("priorityScore", priorityScoreCopy);

        return tag;
    }

    public static ChunkInstance load(CompoundTag tag, HolderLookup.Provider provider) {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("dimension"))
        );

        ChunkPos pos = new ChunkPos(tag.getLong("chunkPos"));

        ChunkInstance mod = new ChunkInstance(dimension, pos);

        mod.biomeIds.clear();
        ListTag biomeIdsList = tag.getList("biomeIds", Tag.TAG_STRING);
        for (int i = 0; i < biomeIdsList.size(); i++) {
            mod.biomeIds.add(biomeIdsList.getString(i));
        }

        mod.clearedBiomes.clear();
        ListTag clearedBiomesList = tag.getList("clearedBiomes", Tag.TAG_STRING);
        for (int i = 0; i < clearedBiomesList.size(); i++) {
            mod.clearedBiomes.add(clearedBiomesList.getString(i));
        }

        mod.activeBiome = tag.getBoolean("hasActiveBiome") ? tag.getString("activeBiome") : null;

        mod.activeBiomeStep = tag.getInt("activeBiomeStep");
        mod.lastFloodHeight = tag.getInt("lastFloodHeight");
        mod.initialWave = tag.getBoolean("initialWave");
        mod.dynamic = tag.getBoolean("dynamic");

        mod.firstSeenTick = tag.getLong("firstSeenTick");

        String stateName = tag.getString("state");
        mod.state = stateName.isEmpty() ? ChunkState.QUEUED : ChunkState.valueOf(stateName);

        mod.lastSeenTick = tag.getLong("lastSeenTick");
        mod.priorityScore = tag.getDouble("priorityScore");

        return mod;
    }

}

/*
Ho provato, nel momento in cui vengono usate le liste mi da questo errore:
Caused by: java.lang.RuntimeException: Failed encoding custom payload biomesofcataclysms:saved_data_sync:
java.util.ConcurrentModificationException Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException:
Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException Caused by:
ava.util.ConcurrentModificationException Caused by: java.lang.RuntimeException:
Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException
Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException:
Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException
Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException:
Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException
Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException:
Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException
Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException:
Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException
Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException:
Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException
Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException:
Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException
Caused by: java.util.ConcurrentModificationException
Perche cosa hai sbagliato?
 */
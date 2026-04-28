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

public class ChunkMod {
    public final ResourceKey<Level> dimension;
    public final ChunkPos pos;
    public Set<String> biomeIds = new HashSet<>();

    public boolean initialWave;   // fa parte dell'onda iniziale
    public boolean dynamic;       // scoperto dal radar
    public boolean instant;       // distruzione istantanea o no

    public long firstSeenTick;
    public long readyAtTick;
    public int speedTier;         // opzionale: velocità di distruzione
    public ChunkState state = ChunkState.QUEUED;
    public int step = 0;          // 0..3 per i quarti del chunk

    public long lastSeenTick;
    public double priorityScore;

    public ChunkMod(ResourceKey<Level> dimension, ChunkPos pos, ServerLevel serverLevel) {
        this.dimension = dimension;
        this.pos = pos;

        this.biomeIds = collectBiomesInChunk(serverLevel, pos);
        this.firstSeenTick = serverLevel.getGameTime();
        this.readyAtTick = this.firstSeenTick + 60; // 1 secondo circa
    }

    private ChunkMod(ResourceKey<Level> dimension, ChunkPos pos) {
        this.dimension = dimension;
        this.pos = pos;
    }

    private Set<String> collectBiomesInChunk(ServerLevel level, ChunkPos pos) {
        Set<String> biomes = new HashSet<>();

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int stepY = 8; // puoi mettere 4 se vuoi più precisione, ma costa di più

        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();

        int[] sampleX = {4, 8, 12};
        int[] sampleZ = {4, 8, 12};

        for (int xOff : sampleX) {
            for (int zOff : sampleZ) {
                for (int y = minY; y < maxY; y += stepY) {
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

        tag.putString("dimension", dimension.location().toString());
        tag.putLong("chunkPos", pos.toLong());

        ListTag biomeList = new ListTag();
        for (String biome : new HashSet<>(biomeIds)) {
            biomeList.add(StringTag.valueOf(biome));
        }
        tag.put("biomeIds", biomeList);

        tag.putBoolean("initialWave", initialWave);
        tag.putBoolean("dynamic", dynamic);
        tag.putBoolean("instant", instant);

        tag.putLong("firstSeenTick", firstSeenTick);
        tag.putLong("readyAtTick", readyAtTick);
        tag.putInt("speedTier", speedTier);
        tag.putString("state", state.name());
        tag.putInt("step", step);
        tag.putLong("lastSeenTick", lastSeenTick);
        tag.putDouble("priorityScore", priorityScore);

        return tag;
    }

    public static ChunkMod load(CompoundTag tag, HolderLookup.Provider provider) {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.parse(tag.getString("dimension"))
        );

        ChunkPos pos = new ChunkPos(tag.getLong("chunkPos"));

        ChunkMod mod = new ChunkMod(dimension, pos);

        mod.biomeIds.clear();
        ListTag biomes = tag.getList("biomeIds", Tag.TAG_STRING);
        for (int i = 0; i < biomes.size(); i++) {
            mod.biomeIds.add(biomes.getString(i));
        }

        mod.initialWave = tag.getBoolean("initialWave");
        mod.dynamic = tag.getBoolean("dynamic");
        mod.instant = tag.getBoolean("instant");

        mod.firstSeenTick = tag.getLong("firstSeenTick");
        mod.readyAtTick = tag.getLong("readyAtTick");
        mod.speedTier = tag.getInt("speedTier");
        mod.state = ChunkState.valueOf(tag.getString("state"));
        mod.step = tag.getInt("step");
        mod.lastSeenTick = tag.getLong("lastSeenTick");
        mod.priorityScore = tag.getDouble("priorityScore");

        return mod;
    }

}

/*
Ho provato, nel momento in cui venogno usate le liste mi da questo errore: Caused by: java.lang.RuntimeException: Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException: Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException: Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException: Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException: Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException: Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException: Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException: Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException Caused by: java.util.ConcurrentModificationException Caused by: java.lang.RuntimeException: Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException Caused by: java.util.ConcurrentModificationException Perche cosa hai sbagliato?
 */
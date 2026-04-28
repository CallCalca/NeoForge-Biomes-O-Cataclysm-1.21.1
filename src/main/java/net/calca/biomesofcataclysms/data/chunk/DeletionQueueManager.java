package net.calca.biomesofcataclysms.data.chunk;

import net.calca.biomesofcataclysms.data.ModVariables;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class DeletionQueueManager {
    public static class DataSavingHelper{
        private static String dimToString(ResourceKey<Level> dim) {
        return dim.location().toString();
    }

        public static ResourceKey<Level> stringToDim(String s) {
            return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(s));
        }

        public static void writeLongDequeMap(CompoundTag root, String name,
                                              Map<ResourceKey<Level>, ArrayDeque<Long>> map) {
            CompoundTag section = new CompoundTag();

            Map<ResourceKey<Level>, ArrayDeque<Long>> copy = new HashMap<>();
            for (var entry : map.entrySet()) {
                copy.put(entry.getKey(), new ArrayDeque<>(entry.getValue()));
            }

            for (var entry : copy.entrySet()) {
                long[] values = entry.getValue().stream().mapToLong(Long::longValue).toArray();
                section.putLongArray(dimToString(entry.getKey()), values);
            }

            root.put(name, section);
        }

        public static void readLongDequeMap(CompoundTag root, String name,
                                             Map<ResourceKey<Level>, ArrayDeque<Long>> map) {
            map.clear();
            if (!root.contains(name, Tag.TAG_COMPOUND)) return;

            CompoundTag section = root.getCompound(name);
            for (String dimId : section.getAllKeys()) {
                long[] values = section.getLongArray(dimId);
                ArrayDeque<Long> deque = new ArrayDeque<>(values.length);
                for (long value : values) {
                    deque.addLast(value);
                }
                map.put(stringToDim(dimId), deque);
            }
        }

        public static void writeStateMap(CompoundTag root, String name,
                                          Map<ResourceKey<Level>, DimensionState> map) {
            CompoundTag section = new CompoundTag();

            Map<ResourceKey<Level>, DimensionState> copy = new HashMap<>();
            for (var entry : map.entrySet()) {
                DimensionState original = entry.getValue();
                DimensionState state = new DimensionState();
                state.currentKey = original.currentKey;
                state.step = original.step;
                copy.put(entry.getKey(), state);
            }

            for (var entry : copy.entrySet()) {
                CompoundTag stateTag = new CompoundTag();
                DimensionState state = entry.getValue();

                stateTag.putBoolean("hasCurrentKey", state.currentKey != null);
                if (state.currentKey != null) {
                    stateTag.putLong("currentKey", state.currentKey);
                }
                stateTag.putInt("step", state.step);

                section.put(dimToString(entry.getKey()), stateTag);
            }

            root.put(name, section);
        }

        public static void readStateMap(CompoundTag root, String name,
                                         Map<ResourceKey<Level>, DimensionState> map) {
            map.clear();
            if (!root.contains(name, Tag.TAG_COMPOUND)) return;

            CompoundTag section = root.getCompound(name);
            for (String dimId : section.getAllKeys()) {
                CompoundTag stateTag = section.getCompound(dimId);
                DimensionState state = new DimensionState();

                if (stateTag.getBoolean("hasCurrentKey")) {
                    state.currentKey = stateTag.getLong("currentKey");
                }
                state.step = stateTag.getInt("step");

                map.put(stringToDim(dimId), state);
            }
        }

    }
    public static class RuntimeBuffers {
        public static final Map<ResourceKey<Level>, ArrayDeque<Long>> INITIAL_ORDER = new HashMap<>();
        public static final Map<ResourceKey<Level>, ArrayDeque<Long>> DYNAMIC_ORDER = new HashMap<>();
        public static final Map<ResourceKey<Level>, DimensionState> INITIAL_STATES = new HashMap<>();
        public static final Map<ResourceKey<Level>, DimensionState> DYNAMIC_STATES = new HashMap<>();
        public static final Map<ResourceKey<Level>, Map<Long, ChunkMod>> CHUNKS = new HashMap<>();
    }

    public static class DimensionState {
        public Long currentKey = null;
        public int step = 0;
    }


    private static Map<Long, ChunkMod> getOrCreateDimChunkMap(ResourceKey<Level> dim) {
        return RuntimeBuffers.CHUNKS.computeIfAbsent(dim, k -> new HashMap<>());
    }

    private static ArrayDeque<Long> getOrCreateQueue(Map<ResourceKey<Level>, ArrayDeque<Long>> store, ResourceKey<Level> dim) {
        ArrayDeque<Long> queue = store.get(dim);
        if (queue == null) {
            queue = new ArrayDeque<>();
            store.put(dim, queue);
        }
        return queue;
    }


    public static void registerInitialChunk(ServerLevel level, ChunkPos pos) {
        ResourceKey<Level> dim = level.dimension();
        long packed = pos.toLong();

        Map<Long, ChunkMod> dimMap = getOrCreateDimChunkMap(dim);
        ChunkMod mod = dimMap.get(packed);

        if (mod == null) {
            mod = new ChunkMod(dim, pos, level);
            dimMap.put(packed, mod);
        }

        mod.initialWave = true;

        ArrayDeque<Long> queue = getOrCreateQueue(RuntimeBuffers.INITIAL_ORDER, dim);
        if (!queue.contains(packed)) {
            queue.addLast(packed);
        }
    }

    public static void registerDynamicChunk(ServerLevel level, ChunkPos pos) {
        ResourceKey<Level> dim = level.dimension();
        long packed = pos.toLong();

        Map<Long, ChunkMod> dimMap = getOrCreateDimChunkMap(dim);
        ChunkMod mod = dimMap.get(packed);

        if (mod == null) {
            mod = new ChunkMod(dim, pos, level);
            dimMap.put(packed, mod);
        }

        mod.dynamic = true;

        ArrayDeque<Long> queue = getOrCreateQueue(RuntimeBuffers.DYNAMIC_ORDER, dim);
        if (!queue.contains(packed)) {
            queue.addLast(packed);
        }
    }

    public static void refreshDynamicQueue(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkMod> registry = RuntimeBuffers.CHUNKS.get(dim);
        if (registry == null || registry.isEmpty()) return;

        List<ChunkMod> valid = new ArrayList<>();

        for (ChunkMod mod : registry.values()) {
            if (mod.state == ChunkState.DONE) continue;
            if (mod.initialWave) continue;
            if (!mod.dynamic) continue;

            // chunk non più caricato -> fuori
            if (!level.hasChunk(mod.pos.x, mod.pos.z)) continue;

            if (!isNearAnyPlayer(level, mod.pos, 32)) continue;

            mod.priorityScore = nearestPlayerDistanceSq(level, mod.pos);
            valid.add(mod);
        }

        if (valid.isEmpty()) return;

        valid.sort(Comparator.comparingDouble(m -> m.priorityScore));

        ArrayDeque<Long> newOrder = new ArrayDeque<>();
        for (ChunkMod mod : valid) {
            newOrder.addLast(ChunkPos.asLong(mod.pos.x, mod.pos.z));
        }

        RuntimeBuffers.DYNAMIC_ORDER.put(dim, newOrder);
    }
    public static void pruneDynamicQueue(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        ArrayDeque<Long> queue = RuntimeBuffers.DYNAMIC_ORDER.get(dim);
        Map<Long, ChunkMod> registry = RuntimeBuffers.CHUNKS.get(dim);

        if (queue == null || queue.isEmpty() || registry == null) return;

        queue.removeIf(key -> {
            ChunkMod mod = registry.get(key);
            if (mod == null) return true;

            // se il chunk non è più caricato, esce dalla coda
            if (!level.hasChunk(mod.pos.x, mod.pos.z)) {
                mod.initialWave = false;
                mod.dynamic = true;
                mod.state = ChunkState.QUEUED;
                return true;
            }

            // se è già finito, esce dalla coda
            return mod.state == ChunkState.DONE;
        });
    }

    public static void rescueMissedChunks(ServerLevel level, int scanRadiusChunks) {
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkMod> registry = RuntimeBuffers.CHUNKS.get(dim);
        if (registry == null || registry.isEmpty() || level.players().isEmpty()) return;

        long now = level.getGameTime();
        Set<Long> visited = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            ChunkPos center = new ChunkPos(player.blockPosition());

            for (int dx = -scanRadiusChunks; dx <= scanRadiusChunks; dx++) {
                for (int dz = -scanRadiusChunks; dz <= scanRadiusChunks; dz++) {
                    ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                    long key = ChunkPos.asLong(pos.x, pos.z);

                    if (!visited.add(key)) continue;
                    if (!level.hasChunk(pos.x, pos.z)) continue;

                    ChunkMod mod = registry.get(key);

                    // Se è già gestito, non fare nulla
                    if (mod != null) {
                        if (mod.state == ChunkState.DONE) continue;
                        if (mod.state == ChunkState.PROCESSING) continue;
                    }

                    if (mod == null) {
                        mod = new ChunkMod(dim, pos, level);
                        registry.put(key, mod);
                    }

                    mod.initialWave = false;
                    mod.dynamic = true;
                    mod.state = ChunkState.QUEUED;
                    mod.lastSeenTick = now;

                    registerDynamicChunk(level, pos);
                }
            }
        }
    }

    private static final int DELETE_FLAGS =
            Block.UPDATE_CLIENTS |
                    Block.UPDATE_SUPPRESS_DROPS |
                    Block.UPDATE_KNOWN_SHAPE;

    private static void clearChunkQuarter(ServerLevel level, ChunkPos pos, int quarter) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        int startX = quarter * 4;
        int endX = startX + 4;

        for (int x = startX; x < endX; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    mutablePos.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);

                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isAir()) continue;

                    if (state.hasBlockEntity()) {
                        level.removeBlockEntity(mutablePos);
                    }

                    level.setBlock(mutablePos, Blocks.AIR.defaultBlockState(), DELETE_FLAGS);
                }
            }
        }
    }

    private static final int VERTICAL_BANDS = 4; // passes will be devided by VERTICAL_BANDS (so 1 passe will clear 1/4 of a chunk).
    private static void process(ServerLevel level,
                                Map<ResourceKey<Level>, ArrayDeque<Long>> orders,
                                Map<ResourceKey<Level>, DimensionState> states,
                                int passes,
                                boolean dynamic) {

        ResourceKey<Level> dim = level.dimension();
        ArrayDeque<Long> order = orders.get(dim);
        DimensionState state = states.computeIfAbsent(dim, d -> new DimensionState());
        Map<Long, ChunkMod> registry = RuntimeBuffers.CHUNKS.get(dim);

        if (order == null || order.isEmpty() || registry == null) return;

        for (int i = 0; i < passes; i++) {
            if (state.currentKey == null) {
                while (!order.isEmpty()) {
                    long next = order.pollFirst();
                    ChunkMod mod = registry.get(next);

                    if (mod == null || mod.state == ChunkState.DONE) continue;

                    state.currentKey = next;
                    state.step = 0;
                    mod.state = ChunkState.PROCESSING;
                    break;
                }

                if (state.currentKey == null) break;
            }

            ChunkMod current = registry.get(state.currentKey);
            if (current == null) {
                state.currentKey = null;
                state.step = 0;
                continue;
            }

            Set<String> biomesToDestroy = new HashSet<>(current.biomeIds);
            ModVariables.MapVariables variables = ModVariables.MapVariables.get(level);
            biomesToDestroy.retainAll(variables.deletedBiomes);

            if (biomesToDestroy.isEmpty()) {
                current.state = ChunkState.DONE;
                current.initialWave = false;
                if (dynamic) current.dynamic = false;
                state.currentKey = null;
                state.step = 0;
                continue;
            }

            clearChunkVerticalBand(level, current.pos, biomesToDestroy, state.step);
            state.step++;

            if (state.step >= VERTICAL_BANDS) {
                current.state = ChunkState.DONE;
                current.initialWave = false;
                if (dynamic) current.dynamic = false;

                state.currentKey = null;
                state.step = 0;
            } else {
                current.state = ChunkState.PARTIAL;
            }
        }
    }

    private static void clearChunkVerticalBand(ServerLevel level, ChunkPos pos, Set<String> targetBiomes, int band) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int totalHeight = maxY - minY;
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(level);

        int bandStart = minY + (totalHeight * band) / VERTICAL_BANDS;
        int bandEnd = minY + (totalHeight * (band + 1)) / VERTICAL_BANDS;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bandStart; y < bandEnd; y++) {
                    mutablePos.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);
                    BlockState state = level.getBlockState(mutablePos);


                    Block transformingTo = Blocks.ACACIA_LEAVES;
                    if (variables.mode == 0 && variables.difficulty == 4){
                        transformingTo = Blocks.BEDROCK;
                    }else{
                        transformingTo = Blocks.AIR;
                    }

                    if (state.is(transformingTo)) continue;

                    String biomeIdAtPos = level.getBiome(mutablePos)
                            .unwrapKey()
                            .map(k -> k.location().toString())
                            .orElse("unknown");

                    if (!targetBiomes.contains(biomeIdAtPos)) continue;

                    if (level.getBlockState(mutablePos).hasBlockEntity()) {
                        level.removeBlockEntity(mutablePos);
                    }

                    level.setBlock(mutablePos, transformingTo.defaultBlockState(), flags);
                }
            }
        }
    }

    public static void processInitialQueue(ServerLevel level, int passes) {
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(level);
        process(level, RuntimeBuffers.INITIAL_ORDER, RuntimeBuffers.INITIAL_STATES, passes, false);
        variables.syncData(level);
    }
    public static void processDynamicQueue(ServerLevel level, int passes) {
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(level);
        process(level, RuntimeBuffers.DYNAMIC_ORDER, RuntimeBuffers.DYNAMIC_STATES, passes, true);
        variables.syncData(level);
    }


    public static boolean hasChunksInRadius(ServerLevel level, ChunkPos center, int radius) {
        Map<Long, ChunkMod> registry = RuntimeBuffers.CHUNKS.get(level.dimension());
        if (registry == null || registry.isEmpty()) return false;

        for (ChunkMod mod : registry.values()) {
            if (mod.state == ChunkState.DONE) continue;

            if (Math.abs(mod.pos.x - center.x) <= radius &&
                    Math.abs(mod.pos.z - center.z) <= radius) {
                return true;
            }
        }

        return false;
    }
    private static boolean isNearAnyPlayer(ServerLevel level, ChunkPos pos, int radius) {
        for (ServerPlayer player : level.players()) {
            ChunkPos pc = new ChunkPos(player.blockPosition());

            int dx = Math.abs(pos.x - pc.x);
            int dz = Math.abs(pos.z - pc.z);

            if (Math.max(dx, dz) <= radius) {
                return true;
            }
        }
        return false;
    }
    private static double nearestPlayerDistanceSq(ServerLevel level, ChunkPos pos) {
        int centerX = (pos.x << 4) + 8;
        int centerZ = (pos.z << 4) + 8;

        double best = Double.MAX_VALUE;

        for (ServerPlayer player : level.players()) {
            double d = player.distanceToSqr(centerX, player.getY(), centerZ);
            if (d < best) best = d;
        }

        return best;
    }

    public static ChunkMod getOrCreateChunkMod(ServerLevel level, ChunkPos pos) {
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkMod> dimMap = getOrCreateDimChunkMap(dim);

        long packed = pos.toLong();
        ChunkMod mod = dimMap.get(packed);

        if (mod == null) {
            mod = new ChunkMod(dim, pos, level);
            dimMap.put(packed, mod);
        }

        return mod;
    }
}
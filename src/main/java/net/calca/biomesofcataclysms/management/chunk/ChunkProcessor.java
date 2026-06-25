package net.calca.biomesofcataclysms.management.chunk;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.RuntimeData;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.cataclysm.sunburn.SunBurnStage;
import net.calca.biomesofcataclysms.data.chunk.ChunkInstance;
import net.calca.biomesofcataclysms.data.chunk.ChunkState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import java.util.*;

import static net.calca.biomesofcataclysms.management.chunk.flood.FloodProcessorHelper.floodChunkSingleLayer;
import static net.calca.biomesofcataclysms.management.chunk.sunburn.SunBurnProcessorHelper.*;

public class ChunkProcessor {

    public static class DimensionState {
        public Long currentKey = null;
        public int step = 0;
    }

    private static Map<Long, ChunkInstance> getOrCreateDimChunkMap(ResourceKey<Level> dim) {
        return RuntimeData.CHUNKS.computeIfAbsent(dim, k -> new HashMap<>());
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

        Map<Long, ChunkInstance> dimMap = getOrCreateDimChunkMap(dim);
        ChunkInstance mod = dimMap.get(packed);

        if (mod == null) {
            mod = new ChunkInstance(dim, pos, level);
            dimMap.put(packed, mod);
        }

        mod.initialWave = true;

        ArrayDeque<Long> queue = getOrCreateQueue(RuntimeData.INITIAL_ORDER, dim);
        if (!queue.contains(packed)) {
            queue.addLast(packed);
        }
    }
    public static void registerDynamicChunk(ServerLevel level, ChunkPos pos) {
        ResourceKey<Level> dim = level.dimension();
        long packed = pos.toLong();

        Map<Long, ChunkInstance> dimMap = getOrCreateDimChunkMap(dim);
        ChunkInstance mod = dimMap.get(packed);

        if (mod == null) {
            mod = new ChunkInstance(dim, pos, level);
            dimMap.put(packed, mod);
        }

        // Se era stato chiuso prima, lo riapriamo
        if (mod.state == ChunkState.DONE) {
            mod.state = ChunkState.QUEUED;
            mod.activeBiome = null;
            mod.activeBiomeStep = 0;
        }

        mod.dynamic = true;

        ArrayDeque<Long> queue = getOrCreateQueue(RuntimeData.DYNAMIC_ORDER, dim);
        if (!queue.contains(packed)) {
            queue.addLast(packed);
        }
    }

    public static void refreshDynamicQueue(ServerLevel level) {
        PersistentData.MapVariables mapVariables = PersistentData.MapVariables.get(level);
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(dim);
        if (registry == null || registry.isEmpty()) return;

        List<ChunkInstance> valid = new ArrayList<>();

        for (ChunkInstance mod : registry.values()) {
            AllCataclysms type = ModUtils.decodeCataclysmFromString(mapVariables.cataclysm);
            if (type == null){
                type = AllCataclysms.DESTROYED;
                ModUtils.sendChatMessage(level, ModUtils.buildErrorMessage(true, 11, Component.literal("refreshDynamicQueue"),
                        Component.translatable("error.biomesofcataclysms.error11")));
                return;
            }

            // --- FIX CRASH: Sostituito Set.of con un controllo null semplice ---
            SunBurnStage stage = SunBurnStage.FIRE;
            if (mod.activeBiome != null) {
                long elapsed = mapVariables.getSunBurnElapsedTicks(mod.activeBiome, level);
                stage = getSunBurnStage(elapsed);
            }

            // LOGICA ORIGINALE (Invariata, inclusa la tua condizione su INSTANT_TRANSFORM)
            if (mod.state == ChunkState.DONE
                    || ((type == AllCataclysms.SUN_BURNT && mod.state == ChunkState.PARTIAL) && stage != SunBurnStage.INSTANT_TRANSFORM)
                    || (type == AllCataclysms.FLOODED && mod.state == ChunkState.PARTIAL)) continue;
            if (mod.initialWave) continue;
            if (!mod.dynamic) continue;
            if (!level.hasChunk(mod.pos.x, mod.pos.z)) continue;
            if (!isNearAnyPlayer(level, mod.pos, 32)) continue;

            mod.priorityScore = nearestPlayerDistanceSq(level, mod.pos);
            valid.add(mod);
        }

        if (valid.isEmpty()) return;

        valid.sort(Comparator.comparingDouble(m -> m.priorityScore));

        ArrayDeque<Long> newOrder = new ArrayDeque<>();
        for (ChunkInstance mod : valid) {
            newOrder.addLast(ChunkPos.asLong(mod.pos.x, mod.pos.z));
        }

        RuntimeData.DYNAMIC_ORDER.put(dim, newOrder);
    }

    //Questo metodo elimina dalla coda:
    // 1. Chunk inesistenti;
    // 2. Chunk attualmente non caricati;
    // 3. Chunk già elaborati (DONE)
    public static void cleanDynamicQueue(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        ArrayDeque<Long> queue = RuntimeData.DYNAMIC_ORDER.get(dim);
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(dim);

        if (queue == null || queue.isEmpty() || registry == null) return;

        queue.removeIf(key -> {
            ChunkInstance mod = registry.get(key);
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
    public static boolean isQueueEmpty(ServerLevel level) {
        ArrayDeque<Long> order = RuntimeData.DYNAMIC_ORDER.get(level.dimension());
        return order == null || order.isEmpty();
    }

    //Questo metodo recupera chunk che sono andati, per qualche motivo, persi nella coda. Reinserisce questi chunk all'interno della coda, per essere
    //rielaborati.
    public static void rescueMissedChunks(ServerLevel level, int scanRadiusChunks) {
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(dim);
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

                    ChunkInstance mod = registry.get(key);

                    // Se è già gestito, non fare nulla
                    if (mod != null) {
                        if (mod.state == ChunkState.DONE) continue;
                        if (mod.state == ChunkState.PROCESSING) continue;
                    }

                    if (mod == null) {
                        mod = new ChunkInstance(dim, pos, level);
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

    private static final int VERTICAL_BANDS = 4; // passes will be devided by VERTICAL_BANDS (so 1 pass will clear 1/4 of a chunk).
    private static void process(ServerLevel level,
                                Map<ResourceKey<Level>, ArrayDeque<Long>> orders,
                                Map<ResourceKey<Level>, DimensionState> states,
                                int passes,
                                boolean dynamic) {

        ResourceKey<Level> dim = level.dimension();
        ArrayDeque<Long> order = orders.get(dim);
        DimensionState state = states.computeIfAbsent(dim, d -> new DimensionState());
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(dim);

        if (order == null || order.isEmpty() || registry == null) return;

        PersistentData.MapVariables variables = PersistentData.MapVariables.get(level);
        AllCataclysms type = ModUtils.decodeCataclysmFromString(variables.cataclysm);
        if (type == null){
            type = AllCataclysms.DESTROYED;
            ModUtils.sendChatMessage(level, ModUtils.buildErrorMessage(true, 11, Component.literal("process"),
                    Component.translatable("error.biomesofcataclysms.error11")));
            return;
        }

        for (int i = 0; i < passes; i++) {
            if (state.currentKey == null) {
                if (order.isEmpty()) break;

                long next = order.pollFirst();
                ChunkInstance mod = registry.get(next);

                if (mod == null || mod.state == ChunkState.DONE
                        || (type == AllCataclysms.SUN_BURNT && mod.state == ChunkState.PARTIAL)
                        || (type == AllCataclysms.FLOODED && mod.state == ChunkState.PARTIAL)) {
                    continue;
                }

                state.currentKey = next;
                mod.state = ChunkState.PROCESSING;
            }

            ChunkInstance current = registry.get(state.currentKey);
            if (current == null) {
                state.currentKey = null;
                continue;
            }

            Set<String> targetBiomes = new HashSet<>(current.biomeIds);
            targetBiomes.retainAll(variables.deletedBiomes);
            targetBiomes.removeAll(current.clearedBiomes);

            if (targetBiomes.isEmpty()) {
                current.state = ChunkState.DONE;
                state.currentKey = null;
                continue;
            }

            if (current.activeBiome == null || !targetBiomes.contains(current.activeBiome)) {
                current.activeBiome = targetBiomes.iterator().next();
                current.activeBiomeStep = 0;
            }

            // --- LOGICA SUN_BURNT ---
            if (type == AllCataclysms.SUN_BURNT) {
                if (!hasExposedBiome(level, current.pos, targetBiomes)) {
                    current.state = ChunkState.DONE;
                    state.currentKey = null;
                    continue;
                }

                applySunBurntChunk(level, current.pos, Set.of(current.activeBiome), variables);

                long elapsed = variables.getSunBurnElapsedTicks(current.activeBiome, level);
                SunBurnStage stage = getSunBurnStage(elapsed);

                if (stage == SunBurnStage.INSTANT_TRANSFORM) {
                    current.state = ChunkState.DONE;
                } else {
                    current.state = ChunkState.PARTIAL;
                }
                state.currentKey = null;
                continue; // SALTA IL RESTO (Evita il crash nelle bande verticali)
            }

            // --- LOGICA FLOODED ---
            // --- LOGICA FLOODED (Versione Uniforme / Round Robin) ---
            if (type == AllCataclysms.FLOODED) {
                int skyLimit = level.getMaxBuildHeight();
                int globalFloodY = Math.min(variables.getFloodHeight(current.activeBiome, level), skyLimit - 1);

                if (current.lastFloodHeight < level.getMinBuildHeight())
                    current.lastFloodHeight = level.getMinBuildHeight() - 1;

                if (current.lastFloodHeight < globalFloodY) {
                    // 1. Facciamo lo scatto di 5 blocchi (o meno se vicino al target)
                    int maxHeightPerTick = (int) ((passes*0.25)*30);
                    int steps = Math.min(maxHeightPerTick, globalFloodY - current.lastFloodHeight);

                    for (int step = 0; step < steps; step++) {
                        int nextY = current.lastFloodHeight + 1;
                        if (nextY < skyLimit) {
                            floodChunkSingleLayer(level, current.pos, Set.of(current.activeBiome), nextY);
                            current.lastFloodHeight = nextY;
                        }
                    }

                    // 2. LA CHIAVE PER L'UNIFORMITÀ:
                    // Invece di rimetterlo in cima (Priority), lo rimettiamo in FONDO alla coda.
                    // Questo costringe il sistema a lavorare su TUTTI gli altri chunk prima di tornare qui.
                    registerDynamicChunk(level, current.pos); // Questo usa addLast() internamente

                    if (current.lastFloodHeight < globalFloodY) current.state = ChunkState.QUEUED;
                    else current.state = ChunkState.PARTIAL;

                    // 3. Rilasciamo il controllo immediatamente per questo "pass"
                    // Settando currentKey a null e facendo continue, il ciclo 'passes' passerà al prossimo chunk nella coda
                    state.currentKey = null;
                    continue;
                }

                if (current.lastFloodHeight >= skyLimit - 1) {
                    current.state = ChunkState.DONE;
                }

                state.currentKey = null;
                continue;
            }

            // --- LOGICA STANDARD (SOLO PER DESTROYED) ---
            clearChunkVerticalBand(type, level, current.pos, Set.of(current.activeBiome), current.activeBiomeStep);
            current.activeBiomeStep++;

            if (current.initialWave) {
                current.initialWave = false;
                current.dynamic = true;
                long packed = current.pos.toLong();
                ArrayDeque<Long> dyn = getOrCreateQueue(RuntimeData.DYNAMIC_ORDER, dim);
                if (!dyn.contains(packed)) dyn.addLast(packed);
            }

            if (current.activeBiomeStep >= VERTICAL_BANDS) {
                current.clearedBiomes.add(current.activeBiome);
                current.activeBiome = null;
                current.activeBiomeStep = 0;

                Set<String> remaining = new HashSet<>(current.biomeIds);
                remaining.retainAll(variables.deletedBiomes);
                remaining.removeAll(current.clearedBiomes);

                if (remaining.isEmpty()) {
                    current.state = ChunkState.DONE;
                    current.initialWave = false;
                    if (dynamic) current.dynamic = false;
                    state.currentKey = null;
                } else {
                    current.state = ChunkState.PARTIAL;
                }
            } else {
                current.state = ChunkState.PARTIAL;
            }
        }
    }


    private static void clearChunkVerticalBand(AllCataclysms cataclysms,
                                               ServerLevel level,
                                               ChunkPos pos,
                                               Set<String> targetBiomes,
                                               int band) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(level);

        if (cataclysms == AllCataclysms.SUN_BURNT) {
            applySunBurntChunk(level, pos, targetBiomes, variables);
            return;
        }

        int bandStart = minY + (level.getMaxBuildHeight() - minY) * band / VERTICAL_BANDS;
        int bandEnd = minY + (maxY - minY) * (band + 1) / VERTICAL_BANDS;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE;

        List<BlockPos> portalHits = new ArrayList<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bandStart; y < bandEnd; y++) {
                    mutablePos.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);

                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isAir() && cataclysms != AllCataclysms.FLOODED && variables.difficulty < 4) continue;

                    String biomeIdAtPos = level.getBiome(mutablePos)
                            .unwrapKey()
                            .map(k -> k.location().toString())
                            .orElse("unknown");

                    if (!targetBiomes.contains(biomeIdAtPos)) continue;

                    if (cataclysms == AllCataclysms.DESTROYED) {
                        if (state.is(Blocks.NETHER_PORTAL)) {
                            portalHits.add(new BlockPos(mutablePos));
                            continue;
                        }

                        // Se è un portale o un frame, saltiamo completamente il blocco
                        if (state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME)) {
                            continue;
                        }

                        if (shouldProtectBlock(level, mutablePos, state)) continue;

                        Block transformingTo = Blocks.AIR;
                        if (variables.mode == 0 && variables.difficulty == 4) {
                            transformingTo = Blocks.BEDROCK;
                        }

                        if (state.hasBlockEntity()) {
                            level.removeBlockEntity(mutablePos);
                        }

                        level.setBlock(mutablePos, transformingTo.defaultBlockState(), flags);
                    } else if (cataclysms == AllCataclysms.FLOODED) {
                        int floodY = variables.getFloodHeight(biomeIdAtPos, level);
                        if (y > floodY) continue;

                        BlockState fluidBlock = level.dimension() == Level.NETHER
                                ? Blocks.LAVA.defaultBlockState()
                                : Blocks.WATER.defaultBlockState();

                            // Se il blocco può essere sostituito dall'acqua (erba, fiori, aria)
                            if (state.isAir() || state.canBeReplaced(Fluids.WATER)) {
                                level.setBlock(mutablePos, fluidBlock, flags);
                            }

                            // Altrimenti proviamo il waterlog
                            else if (state.hasProperty(BlockStateProperties.WATERLOGGED) && !state.getValue(BlockStateProperties.WATERLOGGED)) {
                                level.setBlock(mutablePos, state.setValue(BlockStateProperties.WATERLOGGED, true), flags);
                        }
                    }
                }
            }
        }
        level.getChunkSource().blockChanged(mutablePos);

        extinguishNetherPortals(level, portalHits);
    }

    public static void wakeUpPartialChunks(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();

        // Accediamo ai buffer globali che abbiamo definito all'inizio
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(dim);
        ArrayDeque<Long> dynamicQueue = RuntimeData.DYNAMIC_ORDER.get(dim);

        // Se il registro o la coda non esistono per questa dimensione, usciamo
        if (registry == null || dynamicQueue == null) return;

        for (Map.Entry<Long, ChunkInstance> entry : registry.entrySet()) {
            ChunkInstance mod = entry.getValue();

            // Risvegliamo solo i chunk che sono in pausa (PARTIAL)
            if (mod.state == ChunkState.PARTIAL) {
                long posHash = entry.getKey();

                // Lo aggiungiamo alla coda dinamica solo se non è già dentro
                // Nota: se la coda diventa enorme, questo .contains() potrebbe rallentare.
                if (!dynamicQueue.contains(posHash)) {
                    dynamicQueue.addLast(posHash);
                }
            }
        }
    }

    private static boolean shouldProtectBlock(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        // END: proteggi struttura base
        if (level.dimension() == Level.END) {
            return block == Blocks.BEDROCK || block == Blocks.OBSIDIAN || block == Blocks.IRON_BARS;
        }

        // END PORTAL FRAME (OVERWORLD)
        if (block == Blocks.END_PORTAL_FRAME) {
            return true;
        }

        // NETHER + OVERWORLD PORTAL FRAME (OBSIDIAN)
        if (block == Blocks.OBSIDIAN) {
            return isPortalFrameObsidian(level, pos);
        }

        return false;
    }
    private static boolean isPortalFrameObsidian(ServerLevel level, BlockPos start) {
        if (!level.getBlockState(start).is(Blocks.OBSIDIAN)) return false;

        // Cerca portal blocks vicini
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -24; dy <= 24; dy++) {
                for (int dz = -8; dz <= 8; dz++) {

                    BlockPos check = start.offset(dx, dy, dz);

                    if (level.getBlockState(check).is(Blocks.NETHER_PORTAL)) {

                        // Se trovi portal block, controlla se l'ossidiana
                        // è collegata al portale tramite altro frame
                        if (isConnectedToPortalFrame(level, start, check)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
    private static boolean isConnectedToPortalFrame(ServerLevel level, BlockPos start, BlockPos portal) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        queue.add(start);

        int max = 1024;

        while (!queue.isEmpty() && visited.size() < max) {
            BlockPos pos = queue.pollFirst();

            if (!visited.add(pos.asLong())) continue;

            if (pos.closerThan(portal, 1.6)) {
                return true;
            }

            BlockState state = level.getBlockState(pos);

            if (!state.is(Blocks.OBSIDIAN)) continue;

            for (Direction dir : Direction.values()) {
                queue.add(pos.relative(dir));
            }
        }

        return false;
    }
    private static void extinguishNetherPortals(ServerLevel level, List<BlockPos> portalHits) {
        if (portalHits.isEmpty()) return;

        Set<Long> visited = new HashSet<>();
        List<BlockPos> toClear = new ArrayList<>();

        for (BlockPos start : portalHits) {
            collectConnectedPortalBlocks(level, start, visited, toClear);
        }

        BlockPos soundPos = portalHits.getFirst();
        level.playSound(
                null,
                soundPos,
                SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS,
                1.0F,
                1.0F
        );

        for (BlockPos p : toClear) {
            if (level.getBlockState(p).hasBlockEntity()) {
                level.removeBlockEntity(p);
            }
            level.destroyBlock(p, false);
        }
    }
    private static void collectConnectedPortalBlocks(ServerLevel level,
                                                     BlockPos start,
                                                     Set<Long> visited,
                                                     List<BlockPos> out) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(new BlockPos(start));

        while (!queue.isEmpty()) {
            BlockPos current = queue.pollFirst();
            long key = current.asLong();

            if (!visited.add(key)) continue;
            if (!level.getBlockState(current).is(Blocks.NETHER_PORTAL)) continue;

            out.add(new BlockPos(current));

            for (Direction dir : Direction.values()) {
                queue.add(current.relative(dir));
            }
        }
    }

    public static void processInitialQueue(ServerLevel level, int passes) {
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(level);
        process(level, RuntimeData.INITIAL_ORDER, RuntimeData.INITIAL_STATES, passes, false);
        variables.syncData(level, true, false);
    }
    public static void processDynamicQueue(ServerLevel level, int passes) {
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(level);
        process(level, RuntimeData.DYNAMIC_ORDER, RuntimeData.DYNAMIC_STATES, passes, true);
        variables.syncData(level, true, false);
    }

    public static boolean hasChunksInRadius(ServerLevel level, ChunkPos center, int radius) {
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(level.dimension());
        if (registry == null || registry.isEmpty()) return false;

        int radiusSq = radius * radius;

        for (ChunkInstance mod : registry.values()) {
            if (mod.state == ChunkState.DONE) continue;

            int dx = mod.pos.x - center.x;
            int dz = mod.pos.z - center.z;

            if (dx * dx + dz * dz <= radiusSq) {
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

    public static ChunkInstance getOrCreateChunkMod(ServerLevel level, ChunkPos pos) {
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkInstance> dimMap = getOrCreateDimChunkMap(dim);

        long packed = pos.toLong();
        ChunkInstance mod = dimMap.get(packed);

        if (mod == null) {
            mod = new ChunkInstance(dim, pos, level);
            dimMap.put(packed, mod);
        }

        return mod;
    }
}
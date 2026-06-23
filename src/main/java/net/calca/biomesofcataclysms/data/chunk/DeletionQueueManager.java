package net.calca.biomesofcataclysms.data.chunk;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.ModVariables;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.cataclysm.sunburn.SunBurnStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
        public static final Map<ResourceKey<Level>, DeletionQueueManager.DimensionState> INITIAL_STATES = new HashMap<>();
        public static final Map<ResourceKey<Level>, DeletionQueueManager.DimensionState> DYNAMIC_STATES = new HashMap<>();
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

        // Se era stato chiuso prima, lo riapriamo
        if (mod.state == ChunkState.DONE) {
            mod.state = ChunkState.QUEUED;
            mod.activeBiome = null;
            mod.activeBiomeStep = 0;
        }

        mod.dynamic = true;

        ArrayDeque<Long> queue = getOrCreateQueue(RuntimeBuffers.DYNAMIC_ORDER, dim);
        if (!queue.contains(packed)) {
            queue.addLast(packed);
        }
    }

    public static void refreshDynamicQueue(ServerLevel level) {
        ModVariables.MapVariables mapVariables = ModVariables.MapVariables.get(level);
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkMod> registry = RuntimeBuffers.CHUNKS.get(dim);
        if (registry == null || registry.isEmpty()) return;

        List<ChunkMod> valid = new ArrayList<>();

        for (ChunkMod mod : registry.values()) {
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
    public static boolean isQueueEmpty(ServerLevel level) {
        ArrayDeque<Long> order = RuntimeBuffers.DYNAMIC_ORDER.get(level.dimension());
        return order == null || order.isEmpty();
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

    private static final int VERTICAL_BANDS = 4; // passes will be devided by VERTICAL_BANDS (so 1 pass will clear 1/4 of a chunk).
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

        ModVariables.MapVariables variables = ModVariables.MapVariables.get(level);
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
                ChunkMod mod = registry.get(next);

                if (mod == null || mod.state == ChunkState.DONE
                        || (type == AllCataclysms.SUN_BURNT && mod.state == ChunkState.PARTIAL)
                        || (type == AllCataclysms.FLOODED && mod.state == ChunkState.PARTIAL)) {
                    continue;
                }

                state.currentKey = next;
                mod.state = ChunkState.PROCESSING;
            }

            ChunkMod current = registry.get(state.currentKey);
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
                ArrayDeque<Long> dyn = getOrCreateQueue(RuntimeBuffers.DYNAMIC_ORDER, dim);
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
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(level);

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
    private static void applySunBurntChunk(ServerLevel level,
                                           ChunkPos pos,
                                           Set<String> targetBiomes,
                                           ModVariables.MapVariables vars) {

        String activeBiomeId = targetBiomes.iterator().next();
        long elapsed = vars.getSunBurnElapsedTicks(activeBiomeId, level);
        SunBurnStage stage = getSunBurnStage(elapsed);

        // --- 1. STADIO INSTANT_TRANSFORM: OTTIMIZZATO PER LAG E LAVA ---
        if (stage == SunBurnStage.INSTANT_TRANSFORM) {
            // FASE 1: VAPORIZZAZIONE (Piazza Pulita)
            // Rimuoviamo TUTTI i blocchi speciali dal chunk prima di toccare il terreno.
            // FASE 1: VAPORIZZAZIONE (Piazza Pulita)
            // --- Dentro applySunBurntChunk -> stage == SunBurnStage.INSTANT_TRANSFORM ---
            // FASE 1: VAPORIZZAZIONE
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos scanPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos.getBlockAt(x, 0, z));
                    int limit = 0;
                    while (limit < 64) {
                        BlockState state = level.getBlockState(scanPos);

                        if (state.isAir() || state.is(Blocks.FIRE) || state.is(Blocks.LAVA)) {
                            scanPos = scanPos.below();
                            limit++;
                            continue;
                        }

                        // --- AGGIUNTA PROTEZIONE QUI ---
                        // Se troviamo questi blocchi, fermiamo la scansione della colonna: sono "suolo"
                        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME)) {
                            break;
                        }

                        if (isExpiringBlock(state) || isWoodLog(state) || state.is(Blocks.COAL_BLOCK) || isGlass(state)) {
                            // ... (codice vaporizzazione esistente)
                            level.setBlock(scanPos, Blocks.AIR.defaultBlockState(), 2);
                            scanPos = scanPos.below();
                            limit++;
                        } else {
                            break;
                        }
                    }
                }
            }

            // FASE 2: LIQUEFAZIONE (Piazzamento Lava)
            // Ora che gli alberi e il vetro sono spariti, la Heightmap è stabile.
            // FASE 2: LIQUEFAZIONE
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos groundPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos.getBlockAt(x, 0, z));

                    for (int d = 0; d < 10; d++) {
                        BlockPos current = groundPos.below(d);
                        if (current.getY() < level.getMinBuildHeight()) break;

                        BlockState currentState = level.getBlockState(current);

                        // --- PROTEZIONE LAVA ---
                        // Se il blocco attuale è uno di questi, non trasformarlo in lava e passa al prossimo (o ferma la colonna)
                        if (currentState.is(Blocks.OBSIDIAN) || currentState.is(Blocks.END_PORTAL) || currentState.is(Blocks.END_PORTAL_FRAME)) {
                            continue; // Salta questo blocco e non mettere lava
                        }

                        placeSmartLava(level, current);
                    }
                }
            }
            return;
        }

        // --- 2. LOGICA STANDARD (EARLY, MID, HOT, FINAL) ---
        int attempts = getSunBurnAttempts(stage);
        int safety = attempts * 16;
        RandomSource random = level.getRandom();

        while (attempts > 0 && safety-- > 0) {
            BlockPos topPos = findRandomSurfacePos(level, pos, random);
            if (topPos == null) break;

            int depthOffset = random.nextInt(11);
            BlockPos surfacePos = topPos.below(depthOffset);
            if (surfacePos.getY() < level.getMinBuildHeight()) continue;

            String biomeIdAtPos = level.getBiome(surfacePos).unwrapKey().map(k -> k.location().toString()).orElse("unknown");
            if (!targetBiomes.contains(biomeIdAtPos)) continue;

            BlockState state = level.getBlockState(surfacePos);
            if (state.isAir() || state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.OBSIDIAN) || state.is(Blocks.END_PORTAL)  || state.is(Blocks.END_PORTAL_FRAME)) continue;

            boolean specialActionDone = false;

            // Gestione PRIORITARIA blocchi speciali
            if (isGrassLike(state)) {
                level.setBlock(surfacePos, Blocks.COARSE_DIRT.defaultBlockState(), 3);
                specialActionDone = true;
            } else if (stage != SunBurnStage.FIRE) {
                if (isGlass(state)) {
                    explodeSingleBlock(level, surfacePos, state);
                    specialActionDone = true;
                } else if (isExpiringBlock(state)) {
                    level.setBlock(surfacePos, Blocks.AIR.defaultBlockState(), 3);
                    specialActionDone = true;
                } else if (isWoodLog(state) || (state.is(Blocks.COAL_BLOCK) && isWoodLog(level.getBlockState(surfacePos.below())))) {
                    level.setBlock(surfacePos, Blocks.AIR.defaultBlockState(), 3);
                    handleWoodSpecialEffects(level, surfacePos, random);
                    specialActionDone = true;
                }
            }

            if (specialActionDone) {
                attempts--;
                continue;
            }

            // --- TRASFORMAZIONE DIRETTA DEL TERRENO ---
            if (!hasAdjacentFire(level, surfacePos)) {
                tryPlaceAdjacentFire(level, surfacePos);
            }

            if (stage == SunBurnStage.FIRE) {
                attempts--;
            } else if (stage == SunBurnStage.BURNING || stage == SunBurnStage.HOT) {
                if (state.is(Blocks.NETHERRACK)) {
                    level.setBlock(surfacePos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                } else if (!state.is(Blocks.COAL_BLOCK) &&  !isGlass(state) && !isExpiringBlock(state) && !isWoodLog(state) && !isGrassLike(state)){
                    level.setBlock(surfacePos, Blocks.NETHERRACK.defaultBlockState(), 3);
                }
                attempts--;
            } else { // FINAL STAGE
                if (state.is(Blocks.MAGMA_BLOCK)) {
                    placeSmartLava(level, surfacePos);
                } else if (!state.is(Blocks.COAL_BLOCK) && !isGlass(state) && !isExpiringBlock(state) && !isWoodLog(state) && !isGrassLike(state)){
                    level.setBlock(surfacePos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                }
                attempts--;
            }
        }
    }

    private static void placeSmartLava(ServerLevel level, BlockPos pos) {
        // Controllo critico: se sotto c'è aria o un fluido non solido, DEVE essere fluida
        if (level.isEmptyBlock(pos.below()) || !level.getBlockState(pos.below()).isSolid()) {
            // Piazziamo lava fluida (Level 1).
            // USIAMO FLAG 3 per forzare l'aggiornamento dei fluidi circostanti
            level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);

            // Reazione a catena verso l'alto per correggere eventuali sorgenti rimaste "appese"
            BlockPos above = pos.above();
            BlockState stateAbove = level.getBlockState(above);
            int chainLimit = 0;
            while (stateAbove.is(Blocks.LAVA) && chainLimit < 20) {
                // Se sopra è una sorgente (0), la facciamo diventare fluida (1)
                if (stateAbove.getValue(BlockStateProperties.LEVEL) == 0) {
                    level.setBlock(above, Blocks.LAVA.defaultBlockState().setValue(BlockStateProperties.LEVEL, 1), 3);
                }
                above = above.above();
                stateAbove = level.getBlockState(above);
                chainLimit++;
            }
        } else {
            // Appoggiata su solido: Sorgente pura
            level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);
        }
    }

    /**
     * Gestione carbone/charcoal per il legno rimosso
     */
    private static void handleWoodSpecialEffects(ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < 0.70f) {
            if (random.nextFloat() < 0.40f) {
                Block.popResource(level, pos, new ItemStack(Items.CHARCOAL));
            }
        } else {
            // Controlliamo che non ci sia già un FallingBlock in quel punto per evitare sovrapposizioni visive
            AABB searchBox = new AABB(pos).inflate(0.1);
            if (level.getEntitiesOfClass(FallingBlockEntity.class, searchBox).isEmpty()) {

                // IL FIX È QUI: FallingBlockEntity.fall() aggiunge già l'entità al mondo!
                FallingBlockEntity fallingBlock = FallingBlockEntity.fall(level, pos, Blocks.COAL_BLOCK.defaultBlockState());
                fallingBlock.dropItem = false;
                // NON chiamare level.addFreshEntity(fallingBlock);
            }
        }
    }
    private static boolean hasExposedBiome(ServerLevel level, ChunkPos pos, Set<String> targetBiomes) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Prendiamo il blocco più in alto (esposto al cielo)
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getMinBlockX() + x, pos.getMinBlockZ() + z);
                mutablePos.set(pos.getMinBlockX() + x, y - 1, pos.getMinBlockZ() + z);

                String biomeId = level.getBiome(mutablePos).unwrapKey()
                        .map(k -> k.location().toString()).orElse("unknown");

                if (targetBiomes.contains(biomeId)) {
                    return true; // Trovato almeno un punto di contatto con il sole!
                }
            }
        }
        return false; // Il bioma è interamente coperto da altri biomi o roccia
    }
    public static void resetSunBurntWaves(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkMod> registry = RuntimeBuffers.CHUNKS.get(dim);
        if (registry == null) return;

        for (ChunkMod mod : registry.values()) {
            if (mod.state == ChunkState.PARTIAL) {
                mod.state = ChunkState.QUEUED; // Torna disponibile
                // Opzionale: lo riaggiungiamo alla coda se non c'è già
                registerDynamicChunk(level, mod.pos);
            }
        }
    }
    public static void resetFloodWaves(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkMod> registry = RuntimeBuffers.CHUNKS.get(dim);
        if (registry == null) return;

        for (ChunkMod mod : registry.values()) {
            if (mod.state == ChunkState.PARTIAL) {
                mod.state = ChunkState.QUEUED; // Torna disponibile
                // Opzionale: lo riaggiungiamo alla coda se non c'è già
                registerDynamicChunk(level, mod.pos);
            }
        }
    }
    public static SunBurnStage getSunBurnStage(long elapsedTicks) {
        if (elapsedTicks < 20L * 60L) return SunBurnStage.FIRE; //Dura 60 secondi
        if (elapsedTicks < 20L * 105L) return SunBurnStage.BURNING; //Dura altri 45
        if (elapsedTicks < 20L * 180L) return SunBurnStage.HOT; //Dura 75
        if (elapsedTicks < 20L * 270L) return SunBurnStage.MELTING; // Final dura 90 secondi
        return SunBurnStage.INSTANT_TRANSFORM; // Dopo 165 secondi totali
    }
    private static int getSunBurnAttempts(SunBurnStage stage) {
        return switch (stage) {
            case FIRE -> 15;
            case BURNING -> 30;
            case HOT -> 100;
            case MELTING -> 150;
            case INSTANT_TRANSFORM -> 0;
        };
    }
    private static BlockPos findRandomSurfacePos(ServerLevel level, ChunkPos chunk, RandomSource random) {
        int x = chunk.getMinBlockX() + random.nextInt(16);
        int z = chunk.getMinBlockZ() + random.nextInt(16);

        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return null;
    }
    private static void tryPlaceAdjacentFire(ServerLevel level, BlockPos pos) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_KNOWN_SHAPE;

        for (Direction dir : Direction.values()) {
            BlockPos firePos = pos.relative(dir);
            BlockState current = level.getBlockState(firePos);

            if (!current.isAir()) continue;

            BlockState fire = Blocks.FIRE.defaultBlockState();
            if (fire.canSurvive(level, firePos)) {
                level.setBlock(firePos, fire, flags);
                return;
            }
        }
    }
    private static boolean hasAdjacentFire(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).is(Blocks.FIRE)) {
                return true;
            }
        }
        return false;
    }
    private static boolean isGlass(BlockState state) {
        return state.is(Blocks.GLASS)
                || state.is(Blocks.TINTED_GLASS)
                || state.is(Blocks.WHITE_STAINED_GLASS)
                || state.is(Blocks.ORANGE_STAINED_GLASS)
                || state.is(Blocks.MAGENTA_STAINED_GLASS)
                || state.is(Blocks.LIGHT_BLUE_STAINED_GLASS)
                || state.is(Blocks.YELLOW_STAINED_GLASS)
                || state.is(Blocks.LIME_STAINED_GLASS)
                || state.is(Blocks.PINK_STAINED_GLASS)
                || state.is(Blocks.GRAY_STAINED_GLASS)
                || state.is(Blocks.LIGHT_GRAY_STAINED_GLASS)
                || state.is(Blocks.CYAN_STAINED_GLASS)
                || state.is(Blocks.PURPLE_STAINED_GLASS)
                || state.is(Blocks.BLUE_STAINED_GLASS)
                || state.is(Blocks.BROWN_STAINED_GLASS)
                || state.is(Blocks.GREEN_STAINED_GLASS)
                || state.is(Blocks.RED_STAINED_GLASS)
                || state.is(Blocks.BLACK_STAINED_GLASS)
                || state.is(Blocks.AMETHYST_BLOCK)
                || state.is(Blocks.BUDDING_AMETHYST)
                || state.is(Blocks.SMALL_AMETHYST_BUD)
                || state.is(Blocks.MEDIUM_AMETHYST_BUD)
                || state.is(Blocks.LARGE_AMETHYST_BUD)
                || state.is(Blocks.AMETHYST_CLUSTER)
                || state.is(Blocks.GLOWSTONE);
    }
    private static boolean isExpiringBlock(BlockState state) {
        return state.is(Blocks.ACACIA_LEAVES)
                || state.is(Blocks.BIRCH_LEAVES)
                || state.is(Blocks.AZALEA_LEAVES)
                || state.is(Blocks.CHERRY_LEAVES)
                || state.is(Blocks.JUNGLE_LEAVES)
                || state.is(Blocks.OAK_LEAVES)
                || state.is(Blocks.DARK_OAK_LEAVES)
                || state.is(Blocks.MANGROVE_LEAVES)
                || state.is(Blocks.SPRUCE_LEAVES)
                || state.is(Blocks.FLOWERING_AZALEA_LEAVES)
                || state.is(Blocks.NETHER_WART_BLOCK)
                || state.is(Blocks.WARPED_WART_BLOCK)
                || state.is(Blocks.SHROOMLIGHT)
                || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.DANDELION)
                || state.is(Blocks.POPPY)
                || state.is(Blocks.BLUE_ORCHID)
                || state.is(Blocks.ALLIUM)
                || state.is(Blocks.AZURE_BLUET)
                || state.is(Blocks.RED_TULIP)
                || state.is(Blocks.ORANGE_TULIP)
                || state.is(Blocks.WHITE_TULIP)
                || state.is(Blocks.PINK_TULIP)
                || state.is(Blocks.OXEYE_DAISY)
                || state.is(Blocks.CORNFLOWER)
                || state.is(Blocks.LILY_OF_THE_VALLEY)
                || state.is(Blocks.TORCHFLOWER)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.PINK_PETALS)
                || state.is(Blocks.SPORE_BLOSSOM)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.CRIMSON_ROOTS)
                || state.is(Blocks.WARPED_ROOTS)
                || state.is(Blocks.NETHER_SPROUTS)
                || state.is(Blocks.WEEPING_VINES)
                || state.is(Blocks.TWISTING_VINES)
                || state.is(Blocks.VINE)
                || state.is(Blocks.SUNFLOWER)
                || state.is(Blocks.LILAC)
                || state.is(Blocks.ROSE_BUSH)
                || state.is(Blocks.PEONY)
                || state.is(Blocks.PITCHER_PLANT)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.CRIMSON_FUNGUS)
                || state.is(Blocks.WARPED_FUNGUS)
                || state.is(Blocks.BIG_DRIPLEAF)
                || state.is(Blocks.SMALL_DRIPLEAF)
                || state.is(Blocks.CHORUS_FLOWER)
                || state.is(Blocks.CHORUS_PLANT)
                || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.HANGING_ROOTS)
                || state.is(Blocks.NETHER_WART)
                || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.LILY_PAD)
                || state.is(Blocks.SEA_PICKLE)
                || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.CARVED_PUMPKIN)
                || state.is(Blocks.PUMPKIN_STEM)
                || state.is(Blocks.MELON_STEM)
                || state.is(Blocks.FLOWERING_AZALEA)
                || state.is(Blocks.AZALEA)
                || state.is(Blocks.ACACIA_SAPLING)
                || state.is(Blocks.MANGROVE_PROPAGULE)
                || state.is(Blocks.SPRUCE_SAPLING)
                || state.is(Blocks.BIRCH_SAPLING)
                || state.is(Blocks.BAMBOO_SAPLING)
                || state.is(Blocks.DARK_OAK_SAPLING)
                || state.is(Blocks.CHERRY_SAPLING)
                || state.is(Blocks.JUNGLE_SAPLING)
                || state.is(Blocks.OAK_SAPLING)
                || state.is(Blocks.MELON)
                || state.is(Blocks.HAY_BLOCK)
                || state.is(Blocks.BEE_NEST)
                || state.is(Blocks.SCULK_SENSOR)
                || state.is(Blocks.SCULK_SHRIEKER)
                || state.is(Blocks.SCULK_CATALYST)
                || state.is(Blocks.SCULK_VEIN)
                || state.is(Blocks.COBWEB)
                || state.is(Blocks.DEAD_TUBE_CORAL_BLOCK)
                || state.is(Blocks.DEAD_BRAIN_CORAL_BLOCK)
                || state.is(Blocks.DEAD_BUBBLE_CORAL_BLOCK)
                || state.is(Blocks.DEAD_FIRE_CORAL_BLOCK)
                || state.is(Blocks.DEAD_HORN_CORAL_BLOCK)
                || state.is(Blocks.DEAD_TUBE_CORAL)
                || state.is(Blocks.DEAD_BRAIN_CORAL)
                || state.is(Blocks.DEAD_BUBBLE_CORAL)
                || state.is(Blocks.DEAD_FIRE_CORAL)
                || state.is(Blocks.DEAD_HORN_CORAL)
                || state.is(Blocks.DEAD_TUBE_CORAL_FAN)
                || state.is(Blocks.DEAD_BRAIN_CORAL_FAN)
                || state.is(Blocks.DEAD_BUBBLE_CORAL_FAN)
                || state.is(Blocks.DEAD_FIRE_CORAL_FAN)
                || state.is(Blocks.DEAD_HORN_CORAL_FAN)
                || state.is(Blocks.SPONGE)
                || state.is(Blocks.WET_SPONGE)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.WATER)
                || state.is(Blocks.BUBBLE_COLUMN)
                || state.is(Blocks.BLUE_ICE);
    }
    private static boolean isWoodLog(BlockState state) {
        return state.is(Blocks.ACACIA_LOG)
                || state.is(Blocks.BIRCH_LOG)
                || state.is(Blocks.CHERRY_LOG)
                || state.is(Blocks.JUNGLE_LOG)
                || state.is(Blocks.OAK_LOG)
                || state.is(Blocks.DARK_OAK_LOG)
                || state.is(Blocks.MANGROVE_LOG)
                || state.is(Blocks.SPRUCE_LOG)
                || state.is(Blocks.STRIPPED_ACACIA_LOG)
                || state.is(Blocks.STRIPPED_BIRCH_LOG)
                || state.is(Blocks.STRIPPED_CHERRY_LOG)
                || state.is(Blocks.STRIPPED_JUNGLE_LOG)
                || state.is(Blocks.STRIPPED_OAK_LOG)
                || state.is(Blocks.STRIPPED_DARK_OAK_LOG)
                || state.is(Blocks.STRIPPED_MANGROVE_LOG)
                || state.is(Blocks.STRIPPED_SPRUCE_LOG)
                || state.is(Blocks.MANGROVE_ROOTS);
    }
    private static boolean isGrassLike(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.MUD)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS);
    }
    private static void explodeSingleBlock(ServerLevel level, BlockPos pos, BlockState state) {
        level.levelEvent(2001, pos, Block.getId(state));
        level.destroyBlock(pos, false);
    }

    private static void floodChunkSlice(ServerLevel level, ChunkPos pos, Set<String> targetBiomes, int startY, int endY) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState fluidBlock = level.dimension() == Level.NETHER ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = startY; y <= endY; y++) {
                    mutablePos.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);

                    // Controllo Bioma (solo se necessario per ogni blocco, o puoi ottimizzare scansionando colonne)
                    String biomeId = level.getBiome(mutablePos).unwrapKey().map(k -> k.location().toString()).orElse("unknown");
                    if (!targetBiomes.contains(biomeId)) continue;

                    BlockState state = level.getBlockState(mutablePos);

                    // Piazzamento intelligente
                    if (state.isAir() || state.canBeReplaced(Fluids.WATER)) {
                        level.setBlock(mutablePos, fluidBlock, flags);
                    } else if (state.hasProperty(BlockStateProperties.WATERLOGGED) && !state.getValue(BlockStateProperties.WATERLOGGED)) {
                        level.setBlock(mutablePos, state.setValue(BlockStateProperties.WATERLOGGED, true), flags);
                    }
                }
            }
        }
    }
    private static void floodChunkSingleLayer(ServerLevel level, ChunkPos pos, Set<String> targetBiomes, int y) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE; // Flag leggero
        BlockState fluid = level.dimension() == Level.NETHER ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                mutablePos.set(pos.getMinBlockX() + x, y, pos.getMinBlockZ() + z);

                // Controllo rapido bioma
                if (!targetBiomes.contains(level.getBiome(mutablePos).unwrapKey().map(k -> k.location().toString()).orElse("")))
                    continue;

                BlockState state = level.getBlockState(mutablePos);
                if (state.isAir() || state.canBeReplaced(Fluids.WATER)) {
                    level.setBlock(mutablePos, fluid, flags);
                } else if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    level.setBlock(mutablePos, state.setValue(BlockStateProperties.WATERLOGGED, true), flags);
                }
            }
        }
    }
    public static void wakeUpPartialChunks(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();

        // Accediamo ai buffer globali che abbiamo definito all'inizio
        Map<Long, ChunkMod> registry = RuntimeBuffers.CHUNKS.get(dim);
        ArrayDeque<Long> dynamicQueue = RuntimeBuffers.DYNAMIC_ORDER.get(dim);

        // Se il registro o la coda non esistono per questa dimensione, usciamo
        if (registry == null || dynamicQueue == null) return;

        for (Map.Entry<Long, ChunkMod> entry : registry.entrySet()) {
            ChunkMod mod = entry.getValue();

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
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(level);
        process(level, RuntimeBuffers.INITIAL_ORDER, RuntimeBuffers.INITIAL_STATES, passes, false);
        variables.syncData(level, true, false);
    }
    public static void processDynamicQueue(ServerLevel level, int passes) {
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(level);
        process(level, RuntimeBuffers.DYNAMIC_ORDER, RuntimeBuffers.DYNAMIC_STATES, passes, true);
        variables.syncData(level, true, false);
    }

    public static boolean hasChunksInRadius(ServerLevel level, ChunkPos center, int radius) {
        Map<Long, ChunkMod> registry = RuntimeBuffers.CHUNKS.get(level.dimension());
        if (registry == null || registry.isEmpty()) return false;

        int radiusSq = radius * radius;

        for (ChunkMod mod : registry.values()) {
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
package net.calca.biomesofcataclysms.management.server.chunk;

import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.cataclysm.sunburn.SunBurnStage;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.calca.biomesofcataclysms.data.server.RuntimeData;
import net.calca.biomesofcataclysms.data.server.chunk.ChunkInstance;
import net.calca.biomesofcataclysms.data.server.chunk.ChunkState;
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

import static net.calca.biomesofcataclysms.cataclysm.sunburn.SunBurnStage.getSunBurnStage;
import static net.calca.biomesofcataclysms.management.server.chunk.ChunkProcessorHelper.extinguishNetherPortals;
import static net.calca.biomesofcataclysms.management.server.chunk.ChunkProcessorHelper.shouldProtectBlock;
import static net.calca.biomesofcataclysms.management.server.chunk.ChunkQueueManager.*;
import static net.calca.biomesofcataclysms.management.server.chunk.flood.FloodProcessorHelper.floodChunkSingleLayer;
import static net.calca.biomesofcataclysms.management.server.chunk.sunburn.SunBurnProcessorHelper.applySunBurntChunk;
import static net.calca.biomesofcataclysms.management.server.chunk.sunburn.SunBurnProcessorHelper.hasExposedBiome;

public class ChunkProcessor {

    public static class DimensionState {
        public Long currentKey = null;
        public int step = 0;
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

        if (type == AllCataclysms.ETERNAL_ECLIPSE) return;

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

                        // Blocchi proibiti
                        if (state.is(Blocks.END_PORTAL) || state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.NETHER_BRICKS)) {
                            continue;
                        }
                        //Gli spawner si saltano solo se posto sopra a del nether bricks (quindi spawner di blase)
                        if (state.is(Blocks.SPAWNER) && level.getBlockState(mutablePos.below()).is(Blocks.NETHER_BRICKS)){
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
}
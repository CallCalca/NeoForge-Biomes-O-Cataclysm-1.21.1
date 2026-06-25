package net.calca.biomesofcataclysms.management.chunk.flood;

import net.calca.biomesofcataclysms.data.RuntimeData;
import net.calca.biomesofcataclysms.data.chunk.ChunkInstance;
import net.calca.biomesofcataclysms.data.chunk.ChunkState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

import java.util.Map;
import java.util.Set;

import static net.calca.biomesofcataclysms.management.chunk.ChunkProcessor.registerDynamicChunk;

public class FloodProcessorHelper {
    public static void resetFloodWaves(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(dim);
        if (registry == null) return;

        for (ChunkInstance mod : registry.values()) {
            if (mod.state == ChunkState.PARTIAL) {
                mod.state = ChunkState.QUEUED; // Torna disponibile
                // Opzionale: lo riaggiungiamo alla coda se non c'è già
                registerDynamicChunk(level, mod.pos);
            }
        }
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
    public static void floodChunkSingleLayer(ServerLevel level, ChunkPos pos, Set<String> targetBiomes, int y) {
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
}

package net.calca.biomesofcataclysms.management.server.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class ChunkProcessorHelper {

    //This method tells the processor which blocks not to destroy
    protected static boolean shouldProtectBlock(ServerLevel level, BlockPos pos, BlockState state) {
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

    //The following methods try to understand if a cluster of obsidian is part of a Nether portal or not.
    //If it part of an active Nether portal then the portal will be disabled, but the obsidian will stay.
    protected static boolean isPortalFrameObsidian(ServerLevel level, BlockPos start) {
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
    protected static boolean isConnectedToPortalFrame(ServerLevel level, BlockPos start, BlockPos portal) {
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
    protected static void extinguishNetherPortals(ServerLevel level, List<BlockPos> portalHits) {
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
    protected static void collectConnectedPortalBlocks(ServerLevel level,
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

}

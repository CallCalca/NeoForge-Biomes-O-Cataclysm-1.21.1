package net.calca.biomesofcataclysms.mixin.eternalEclipse;

import com.mojang.datafixers.kinds.IdF;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.checkerframework.common.aliasing.qual.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Mixin(BlockBehaviour.class)
public abstract class MushroomStemRandomTickBehaviorMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void boc$onMushroomStemRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (state.is(Blocks.MUSHROOM_STEM)) {
            PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);
            if (!ModUtils.isGameRunningWithCataclysm(level, AllCataclysms.ETERNAL_ECLIPSE)) return;
            String biomeID = ModUtils.getBiomeID(level, pos);
            if (!globalVars.deletedBiomes.contains(biomeID)) return;
            // Verifichiamo se le condizioni geometriche richieste sono soddisfatte
            if (boc$shouldMushroomStemTick(level, pos)) {
                Random random1 = new Random(); //Ha il 100% di probabilità to random tick
                if (random1.nextInt(100) + 1 <= 100) {
                    BlockState belowState = level.getBlockState(pos.below());
                    BlockState aboveState = level.getBlockState(pos.above());
                    if (aboveState.is(Blocks.BROWN_MUSHROOM_BLOCK)){
                        int pattern = ModUtils.putBlockPosThroughSeed(pos, 5, 0);
                        BlockPos[] possesFromPattern = boc$getBrownMushroomBerryPoses(pattern, pos);
                        for (BlockPos blockPos : possesFromPattern) {
                            if (level.getBlockState(blockPos).is(Blocks.AIR) && level.getBlockState(blockPos.above()).is(Blocks.BROWN_MUSHROOM_BLOCK)){
                                if (random1.nextInt(100)+1 <= 25){
                                    level.setBlockAndUpdate(blockPos, Blocks.CAVE_VINES_PLANT.defaultBlockState());
                                    return;
                                }else{
                                    level.setBlockAndUpdate(blockPos, Blocks.CAVE_VINES.defaultBlockState());
                                    return;
                                }
                            }
                        }
                    } else if (aboveState.is(Blocks.RED_MUSHROOM_BLOCK)) {
                        int pattern = ModUtils.putBlockPosThroughSeed(pos, 5, 0);
                        BlockPos[] possesFromPattern = boc$getRedMushroomBerryPoses(pattern, pos);
                        for (BlockPos blockPos : possesFromPattern) {
                            if (level.getBlockState(blockPos).is(Blocks.AIR) && level.getBlockState(blockPos.above()).is(Blocks.RED_MUSHROOM_BLOCK)){
                                if (random1.nextInt(100)+1 <= 25){
                                    level.setBlockAndUpdate(blockPos, Blocks.CAVE_VINES_PLANT.defaultBlockState());
                                    return;
                                }else{
                                    level.setBlockAndUpdate(blockPos, Blocks.CAVE_VINES.defaultBlockState());
                                    return;
                                }
                            }
                        }
                    }
                    if (belowState.is(Blocks.AIR) || belowState.is(Blocks.MUSHROOM_STEM)) {// VERIFICA FONDAMENTALE: È ancora collegato al CAP in alto?
                        if (level.getBlockState(pos.above()).is(Blocks.AIR)) {
                            if (random1.nextInt(100) + 1 <= 25) {
                                level.setBlockAndUpdate(pos, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState());
                            } else {
                                level.setBlockAndUpdate(pos, Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState());
                            }
                            level.setBlockAndUpdate(pos.below(), Blocks.MUSHROOM_STEM.defaultBlockState());
                            return;
                        } else if (isConnectedToCapAbove(level, pos)) {
                            level.setBlockAndUpdate(pos.below(), Blocks.MUSHROOM_STEM.defaultBlockState());
                        }
                    }
                    if (belowState.is(net.minecraft.tags.BlockTags.DIRT)
                            || belowState.is(Blocks.MYCELIUM)
                            || belowState.is(Blocks.PODZOL)
                            || belowState.is(Blocks.GRASS_BLOCK)) {
                        level.destroyBlock(pos.below(), false);
                        level.setBlockAndUpdate(pos.below(), Blocks.MUSHROOM_STEM.defaultBlockState());
                    } else {
                        List<Direction> direzioni = new ArrayList<>(List.of(Direction.values()));
                        Collections.shuffle(direzioni, new java.util.Random(random1.nextLong()));

                        for (Direction dir : direzioni) {
                            BlockPos targetPos = pos.relative(dir);
                            BlockState targetState = level.getBlockState(targetPos);
                            if (boc$isStoneOrOre(targetState)) {
                                level.setBlockAndUpdate(targetPos, Blocks.COBBLESTONE.defaultBlockState());
                                break;
                            } else if (boc$isDeepslateOrOre(targetState)) {
                                level.setBlockAndUpdate(targetPos, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
                                break;
                            } else if (boc$isNetherOre(targetState)) {
                                level.setBlockAndUpdate(targetPos, Blocks.NETHERRACK.defaultBlockState());
                                break;
                            } else if (boc$isCorrodible(targetState)) {
                                level.setBlockAndUpdate(targetPos, Blocks.MUSHROOM_STEM.defaultBlockState());
                                break;
                            }
                        }
                    }
                }

            }
            ci.cancel();
        }
    }

    @Unique
    private boolean isConnectedToCapAbove(Level level, BlockPos startPos) {
        BlockPos.MutableBlockPos currentPos = startPos.mutable();
        int maxAltezza = 30; // Limite di sicurezza per evitare loop infiniti se la struttura è gigante

        for (int i = 0; i < maxAltezza; i++) {
            currentPos.move(Direction.UP); // Sali di un blocco
            BlockState state = level.getBlockState(currentPos);

            // 1. Se incontriamo un cappello, la catena è VIVA!
            if (boc$isMushroomCap(state)) {
                return true;
            }

            // 2. Se non è né un cappello né un gambo (es. Aria, Cobblestone, Foglie), la catena è SPEZZATA
            if (!state.is(Blocks.MUSHROOM_STEM)) {
                return false;
            }
        }

        return false; // Se supera l'altezza massima senza trovare il cappello
    }

    // Metodo di supporto per identificare i blocco Cappello (sia vanilla che custom)
    private boolean boc$isMushroomCap(BlockState state) {
        return state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK);
    }

    @Unique
    private static boolean boc$shouldMushroomStemTick(ServerLevel level, BlockPos pos) {
        // 1. Controllo SOTTO (Deve essere della DIRT)
        // Usiamo il Tag DIRT che include terra classica, coarse dirt, podzol e blocchi d'erba
        BlockState belowState = level.getBlockState(pos.below());
        BlockState above = level.getBlockState(pos.above());
        boolean isBlockBelowOK = belowState.is(BlockTags.DIRT) || belowState.is(Blocks.AIR) || above.is(Blocks.AIR);

        // 2. Controllo SOPRA (Deve essere un blocco CAP rosso o marrone)
        BlockState aboveState = level.getBlockState(pos.above());
        boolean isCapAbove = aboveState.is(Blocks.RED_MUSHROOM_BLOCK) || aboveState.is(Blocks.BROWN_MUSHROOM_BLOCK);

        boolean isStoneAround = false;
        List<Direction> direzioni = new ArrayList<>(List.of(Direction.values()));
        for (Direction dir : direzioni) {
            BlockState adjacentState = level.getBlockState(pos.relative(dir));
            if (boc$isStoneOrOre(adjacentState) || boc$isDeepslateOrOre(adjacentState) || boc$isNetherOre(adjacentState) || boc$isCorrodible(adjacentState)) {
                isStoneAround = true;
                break; // Ne basta uno per attivare il tick, quindi interrompiamo il ciclo
            }
        }

        // Il blocco deve ticcare se almeno una di queste tre condizioni è vera
        return isBlockBelowOK || isCapAbove || isStoneAround;
    }

    @Unique
    private static boolean boc$isStoneOrOre(BlockState state) {
        // Solo Stone generica, Granite, Diorite, Andesite, Tuff e minerali incastonati nella Stone
        if (state.is(Blocks.STONE) || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE))
            return true;
        if (state.is(net.minecraft.tags.BlockTags.STONE_ORE_REPLACEABLES)) return true;

        // Controlla se è un minerale ma NON della deepslate e NON del nether
        return (state.is(net.minecraft.tags.BlockTags.COAL_ORES) ||
                state.is(net.minecraft.tags.BlockTags.IRON_ORES) ||
                state.is(net.minecraft.tags.BlockTags.COPPER_ORES) ||
                state.is(net.minecraft.tags.BlockTags.GOLD_ORES) ||
                state.is(net.minecraft.tags.BlockTags.REDSTONE_ORES) ||
                state.is(net.minecraft.tags.BlockTags.LAPIS_ORES) ||
                state.is(net.minecraft.tags.BlockTags.DIAMOND_ORES) ||
                state.is(net.minecraft.tags.BlockTags.EMERALD_ORES))
                && !state.is(net.minecraft.tags.BlockTags.DEEPSLATE_ORE_REPLACEABLES);
    }

    @Unique
    private static boolean boc$isDeepslateOrOre(BlockState state) {
        // Solo Deepslate (sia normale che cobblated) e minerali incastonati nella Deepslate
        if (state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.TUFF)) return true;
        if (state.is(net.minecraft.tags.BlockTags.DEEPSLATE_ORE_REPLACEABLES)) return true;

        // Controlla se è espressamente una variante "deepslate ore"
        return state.is(Blocks.DEEPSLATE_COAL_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE) ||
                state.is(Blocks.DEEPSLATE_COPPER_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE) ||
                state.is(Blocks.DEEPSLATE_REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE) ||
                state.is(Blocks.DEEPSLATE_DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE);
    }

    @Unique
    private static boolean boc$isNetherOre(BlockState state) {
        // Solo i minerali nativi del Nether
        return state.is(Blocks.NETHER_QUARTZ_ORE) || state.is(Blocks.NETHER_GOLD_ORE);
    }

    @Unique
    private static boolean boc$isCorrodible(BlockState state) {
        // Un blocco è "corrodibile" se appartiene a una QUALSIASI delle tre categorie sopra!
        return state.is(Blocks.COBBLESTONE) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.NETHERRACK);
    }

    /**
     * Pattern per BROWN MUSHROOM (Cappello piatto).
     * Tutte le posizioni mantengono la stessa Y della posizione passata (offset Y = 0).
     */
    @Unique
    private static BlockPos[] boc$getBrownMushroomBerryPoses(int pattern, BlockPos underCapPos) {
        List<BlockPos> positions = new ArrayList<>();

        // pattern % 6 per sicurezza sugli indici
        switch (Math.abs(pattern) % 6) {
            case 0: // 2 liane agli angoli opposti
                positions.add(underCapPos.offset(-3, 0, -2));
                positions.add(underCapPos.offset(1, 0, 3));
                positions.add(underCapPos.offset(2, 0, 1));
                positions.add(underCapPos.offset(3, 0, 2));
                positions.add(underCapPos.offset(-1, 0, -2));
                positions.add(underCapPos.offset(0, 0, 3));
                break;

            case 1: // Croce rada (4 liane attorno al centro)
                positions.add(underCapPos.offset(2, 0, 2));
                positions.add(underCapPos.offset(-2, 0, 1));
                positions.add(underCapPos.offset(-3, 0, 1));
                positions.add(underCapPos.offset(0, 0, -2));
                break;

            case 2: // Diagonale da 3 liane (incluso il centro)
                positions.add(underCapPos.offset(-1, 0, -1));
                positions.add(underCapPos.offset(-3, 0, -2));
                positions.add(underCapPos.offset(2, 0, 3));
                positions.add(underCapPos.offset(-3, 0, -1));
                positions.add(underCapPos.offset(-3, 0, 2));
                positions.add(underCapPos.offset(1, 0, -3));
                break;

            case 3: // Forma a "L" lungo 2 lati del cappello
                positions.add(underCapPos.offset(-1, 0, 1));
                positions.add(underCapPos.offset(0, 0, 1));
                positions.add(underCapPos.offset(3, 0, -1));
                positions.add(underCapPos.offset(2, 0, 3));
                positions.add(underCapPos.offset(2, 0, -3));
                positions.add(underCapPos.offset(0, 0, 2));
                break;

            case 4: // Anello esterno da 5 liane
                positions.add(underCapPos.offset(-2, 0, 0));
                positions.add(underCapPos.offset(1, 0, 0));
                positions.add(underCapPos.offset(0, 0, -1));
                positions.add(underCapPos.offset(2, 0, 3));
                positions.add(underCapPos.offset(1, 0, 2));
                positions.add(underCapPos.offset(-1, 0, 3));
                positions.add(underCapPos.offset(-2, 0, -2));
                break;

            case 5: // Asimmetrico (3 liane sparse)
                positions.add(underCapPos.offset(3, 0, -2));
                positions.add(underCapPos.offset(2, 0, 0));
                positions.add(underCapPos.offset(1, 0, 3));
                positions.add(underCapPos.offset(1, 0, -2));
                positions.add(underCapPos.offset(-1, 0, 0));
                positions.add(underCapPos.offset(0, 0, 1));
                positions.add(underCapPos.offset(-3, 0, -2));
                break;
        }

        return positions.toArray(new BlockPos[0]);
    }

    /**
     * Pattern per RED MUSHROOM (Cappello a cupola).
     * Mantiene la stessa Y per tutte le posizioni generate nel singolo pattern.
     */
    @Unique
    private static BlockPos[] boc$getRedMushroomBerryPoses(int pattern, BlockPos underCapPos) {
        List<BlockPos> positions = new ArrayList<>();

        switch (Math.abs(pattern) % 6) {
            case 0: // 2 liane sui lati opposti della cupola
                positions.add(underCapPos.offset(-1, 0, 0));
                positions.add(underCapPos.offset(1, 0, 0));
                break;

            case 1: // Centro + 2 liane adiacenti
                positions.add(underCapPos.offset(1, 0, 1));
                positions.add(underCapPos.offset(0, 0, 1));
                positions.add(underCapPos.offset(1, 0, -1));
                positions.add(underCapPos.offset(0, 0, -1));
                break;

            case 2: // Cerchio/Anello a 4 punti attorno allo stelo
                positions.add(underCapPos.offset(1, 0, 0));
                positions.add(underCapPos.offset(-1, 0, 0));
                positions.add(underCapPos.offset(0, 0, 1));
                positions.add(underCapPos.offset(0, 0, -1));
                break;

            case 3: // Tre angoli a triangolo
                positions.add(underCapPos.offset(-1, 0, -1));
                positions.add(underCapPos.offset(1, 0, 1));
                positions.add(underCapPos.offset(1, 0, -1));
                positions.add(underCapPos.offset(-1, 0, -1));
                positions.add(underCapPos.offset(1, 0, -1));
                break;

            case 4: // Densità massima: Centro + 4 angoli
                positions.add(underCapPos.offset(-1, 0, 0));
                positions.add(underCapPos.offset(1, 0, 1));
                positions.add(underCapPos.offset(-1, 0, -1));
                positions.add(underCapPos.offset(-1, 0, 1));
                positions.add(underCapPos.offset(1, 0, -1));
                break;

            case 5: // Linea su un solo lato del cappello
                positions.add(underCapPos.offset(1, 0, 0));
                positions.add(underCapPos.offset(1, 0, 1));
                positions.add(underCapPos.offset(-1, 0, 0));
                positions.add(underCapPos.offset(1, 0, -1));
                break;
        }

        return positions.toArray(new BlockPos[0]);
    }
}
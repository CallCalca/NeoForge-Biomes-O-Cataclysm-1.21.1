package net.calca.biomesofcataclysms.mixin;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.cataclysm.eternaleclipse.EternalEclipseStage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static net.calca.biomesofcataclysms.ModUtils.getHumidityOnPos;

@Mixin(SpreadingSnowyDirtBlock.class)
public class SpreadingDirtRandomTickMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void onGrassAndMyceliumTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        // 1. Controllo dello stato del cataclisma
        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);
        if (!ModUtils.isGameRunningWithCataclysm(level, AllCataclysms.ETERNAL_ECLIPSE)) return;
        if (!state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.MYCELIUM)) return;

        String biomeId = ModUtils.getBiomeID(level, pos);

        // 3. Se questo bioma è colpito dal cataclisma
        if (globalVars.deletedBiomes.contains(biomeId)) {
            Random random1 = new Random();
            long elapsedTicks = globalVars.getEternalEclipseElapsedTicks(biomeId, level);
            EternalEclipseStage stage = EternalEclipseStage.getEternalEclipseStage(elapsedTicks);
            if (state.is(Blocks.GRASS_BLOCK)) {
                int chance = ThreadLocalRandom.current().nextInt(1, 101);

                long totalDurationTicks = EternalEclipseStage.START_DECAY_TICKS; // 4 minuti
                //La percentuale di decay aumenta progressivamente per 4 minuti, quando raggiunge il 100%
                int decayPercentage = (int) ((100 * elapsedTicks) / totalDurationTicks);
                decayPercentage = Math.max(0, Math.min(100, decayPercentage));

                if (stage == EternalEclipseStage.START_DECAY){
                    if (chance <= decayPercentage) {
                        boc$transformGrass(random1, level, pos, stage);
                    }
                }else{
                    boc$transformGrass(random1, level, pos, stage);
                }
            }else if (state.is(Blocks.MYCELIUM)) {
                //Se non è abbastanza umido, trasforma in dirt e ritorna, non è piu necessario fare altra logica.
                if (!getHumidityOnPos(level, pos, EternalEclipseStage.HUMID_ENOUGH_TO_SPAWN_FUNGUS)){
                    level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
                    return;
                }
                if (stage == EternalEclipseStage.START_SPREAD){
                    //Lascio la logica vanilla solo a una certa probabilità crescente
                    long totalDurationTicks = EternalEclipseStage.START_SPREAD_TCKS; // fino a 8 minuti
                    //La percentuale di spread aumenta progressivamente per 4 minuti, quando raggiunge il 100%
                    int spreadPercentage = (int) ((100 * elapsedTicks) / totalDurationTicks);
                    spreadPercentage = Math.max(0, Math.min(100, spreadPercentage));
                    if (random1.nextInt(100) <= spreadPercentage) {
                        boc$spreadMycelium(random1, level,pos);
                    }
                }else if (stage == EternalEclipseStage.MYCELIUM_SPREAD){
                    boc$spreadMycelium(random1, level,pos);
                    long totalDurationTicks = EternalEclipseStage.MYCELIUM_SPREAD_TICKS; // fino a 10 minuti
                    //La percentuale di sprout dei funghi aumenta progressivamente per 2 minuti, quando raggiunge il 100%
                    int sproutPercentage = (int) ((100 * elapsedTicks) / totalDurationTicks);
                    sproutPercentage = Math.max(0, Math.min(100, sproutPercentage));
                    if (random1.nextInt(100) <= sproutPercentage){
                        boc$placeMushrooms(random, random1, level, pos, stage);
                    }

                }else if (stage == EternalEclipseStage.FUNGUS_SPROUT){
                    //Spawnano funghi, secondo una logica precisa, sopra al micelio con 100%
                    boc$spreadMycelium(random1, level,pos);
                        boc$placeMushrooms(random, random1, level, pos, stage);
                }else if (stage == EternalEclipseStage.CAVE_GERMINATION){
                    //Spawnano i funghi ad una probabilità fissa del 100% e crescono normalmente
                    boc$spreadMycelium(random1, level,pos);
                    boc$placeMushrooms(random, random1, level, pos, stage);
                }

            }
            ci.cancel();
        }

    }

    @Unique
    private static void boc$transformGrass(Random random1, ServerLevel level, BlockPos pos, EternalEclipseStage stage){
        int randomChance = random1.nextInt(100);
        if (randomChance <= 15){
            if (getHumidityOnPos(level, pos, EternalEclipseStage.HUMID_ENOUGH_TO_SPAWN_FUNGUS)){
                level.setBlockAndUpdate(pos, Blocks.MYCELIUM.defaultBlockState());
            }else{
                level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            }
        }else if (randomChance <= 25){
            level.setBlockAndUpdate(pos, Blocks.COARSE_DIRT.defaultBlockState());
        }else if (randomChance <= 35){
            level.setBlockAndUpdate(pos, Blocks.ROOTED_DIRT.defaultBlockState());
        }else if (stage == EternalEclipseStage.START_DECAY || stage == EternalEclipseStage.DEATH_OF_VEGETATION || stage == EternalEclipseStage.START_SPREAD){
            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        }else{
            if (getHumidityOnPos(level, pos, EternalEclipseStage.HUMID_ENOUGH_TO_SPAWN_FUNGUS)){
                level.setBlockAndUpdate(pos, Blocks.MYCELIUM.defaultBlockState());
            }else{
                level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            }
        }
        //Elimino la vegetazione che può trovarsi sopra la terra
        BlockPos rightAbovePos = pos.above();
        BlockState rightAboveBlockState = level.getBlockState(rightAbovePos);
        Block rightAboveBlock = rightAboveBlockState.getBlock();
        if (rightAboveBlockState.is(net.minecraft.tags.BlockTags.FLOWERS)
                || rightAboveBlockState.is(net.minecraft.tags.BlockTags.CROPS)
                || rightAboveBlockState.is(net.minecraft.tags.BlockTags.SAPLINGS)
                || rightAboveBlock instanceof net.minecraft.world.level.block.BushBlock
                || rightAboveBlock instanceof net.minecraft.world.level.block.StemBlock
                || rightAboveBlock instanceof net.minecraft.world.level.block.BambooSaplingBlock
                || rightAboveBlock instanceof net.minecraft.world.level.block.BambooStalkBlock
        ){
            level.setBlockAndUpdate(rightAbovePos, Blocks.AIR.defaultBlockState());
        }
    }
    @Unique
    private static void boc$spreadMycelium(Random random1, ServerLevel level, BlockPos pos){
        int maxAttempt = 3;

        for (int i = 0; i < maxAttempt; i++) {
            // Calcoliamo una posizione casuale nell'area 3x3x3 attorno a 'pos'
            // random1.nextInt(-1, 2) restituisce -1, 0, oppure 1 (perché il 2 è escluso!)
            BlockPos randomSidePos = new BlockPos(
                    pos.getX() + random1.nextInt(-1, 2), // Genera -1, 0, 1 (Larghezza: 3)
                    pos.getY() + random1.nextInt(0, 2),  // MODIFICATO: Genera solo 0 e 1 (Altezza: 2)
                    pos.getZ() + random1.nextInt(-1, 2)  // Genera -1, 0, 1 (Profondità: 3)
            );

            net.minecraft.world.level.block.state.BlockState statoBersaglio = level.getBlockState(randomSidePos);

            // Controlliamo se il bersaglio è DIRT o GRASS_BLOCK
            if ((statoBersaglio.is(Blocks.DIRT) || statoBersaglio.is(Blocks.GRASS_BLOCK))
                    && level.getBlockState(randomSidePos.above()).propagatesSkylightDown(level, randomSidePos.above())) {
                // Piazza il micelio ed esce immediatamente dal ciclo dei tentativi
                level.setBlockAndUpdate(randomSidePos, Blocks.MYCELIUM.defaultBlockState());
                break;
            }
        }
    }

    @Unique
    private static void boc$placeMushrooms(RandomSource random, Random random1, ServerLevel level, BlockPos pos, EternalEclipseStage stage){
        BlockPos abovePos = pos.above();
        boolean fungusAround = false;
        int raggio = 2;
        int patternToApply = ModUtils.putBlockPosThroughSeed(pos, 12, 1);
        BlockPos[] possesFromPattern = boc$getBlockPosesFromPattern(patternToApply, abovePos);

        // FIX: Corretto il ciclo di scansione attorno per evitare i bug di puntatori e tipi di fungo
        for (BlockPos areaPos : BlockPos.betweenClosed(
                abovePos.offset(-raggio, 0, -raggio),
                abovePos.offset(raggio, 0, raggio)
        )) {
            BlockState targetState = level.getBlockState(areaPos);
            // Controlla sia rossi che marroni correttamente
            if (targetState.is(Blocks.BROWN_MUSHROOM) || targetState.is(Blocks.RED_MUSHROOM)) {
                boolean trovatoNelPattern = false;
                for (BlockPos blockPos : possesFromPattern) {
                    if (blockPos == null) continue;
                    // FIX: .equals() confronta le coordinate reali, non i puntatori di memoria!
                    if (areaPos.equals(blockPos)) {
                        trovatoNelPattern = true;
                        break;
                    }
                }
                if (!trovatoNelPattern) {
                    fungusAround = true;
                    break;
                }
            }
        }
        if (fungusAround) {
            if (stage == EternalEclipseStage.CAVE_GERMINATION) level.setBlockAndUpdate(pos, Blocks.PODZOL.defaultBlockState());
            return;
        }

        BlockState mushroomToPlace;
        if (random1.nextInt(100) <= 25){
            mushroomToPlace = Blocks.RED_MUSHROOM.defaultBlockState();
        } else {
            mushroomToPlace = Blocks.BROWN_MUSHROOM.defaultBlockState();
        }

        BlockPos GrownMushroom = possesFromPattern[0];

        // Shuffle dell'array
        for (int i = possesFromPattern.length - 1; i > 0; i--) {
            if (possesFromPattern[i] == null) continue;
            int j = random1.nextInt(i + 1);
            BlockPos temp = possesFromPattern[i];
            possesFromPattern[i] = possesFromPattern[j];
            possesFromPattern[j] = temp;
        }

        for (int i = 0; i < possesFromPattern.length; i++) {
            if (possesFromPattern[i] == null) continue;

            if (level.getBlockState(possesFromPattern[i]).isAir()
                    && (level.getBlockState(possesFromPattern[i].below()).is(Blocks.MYCELIUM)
                    || level.getBlockState(possesFromPattern[i].below()).is(Blocks.PODZOL))){
                level.setBlockAndUpdate(possesFromPattern[i], mushroomToPlace);
                break;
            }

            // Fallback di crescita forzata
            if (i == possesFromPattern.length - 1 && stage == EternalEclipseStage.CAVE_GERMINATION) {
                if (GrownMushroom != null) {
                    BlockState mushroomToGrow = level.getBlockState(GrownMushroom);

                    if (mushroomToGrow.is(Blocks.BROWN_MUSHROOM)) {
                        ((MushroomBlock) Blocks.BROWN_MUSHROOM).growMushroom(level, GrownMushroom, mushroomToGrow, random);
                    } else if (mushroomToGrow.is(Blocks.RED_MUSHROOM)) {
                        ((MushroomBlock) Blocks.RED_MUSHROOM).growMushroom(level, GrownMushroom, mushroomToGrow, random);
                    }
                }
                level.setBlockAndUpdate(pos, Blocks.PODZOL.defaultBlockState());
                return;
            }
        }
    }

    //Questo metodo server per ottenere i block pos su cui poi il sistema tenterà di piazzare i funghi, in modo da generare pattern specifici per ogni valore inserito
    //Nel commento successivo sono mostrati i pattern esistenti attraverso uno schema. Sono visti dall'alto.
    /*
    PATTERN 0:
    0 0 0
    0 1 0
    0 0 0
    PATTERN 1:
    1 0 0
    1 1 0
    0 1 0
    PATTERN 2:
    1 1 0
    1 1 1
    0 1 0
    PATTERN 3:
    0 0 1
    1 1 0
    0 1 0
    PATTERN 4:
    1 0 1
    1 1 1
    0 1 0
    PATTERN 5:
    1 0 0
    0 0 1
    1 0 0
    PATTERN 6:
    0 1 0
    1 1 1
    1 0 0
    PATTERN 7:
    0 1 1
    1 1 1
    1 1 0
    PATTERN 8:
    0 1 0
    1 1 1
    0 1 0
    PATTERN 9:
    0 1 0
    1 1 1
    0 0 0
    PATTERN 10:
    0 1 0
    0 1 1
    0 0 0
    PATTERN 11:
    0 0 0
    1 1 0
    0 1 0
     */
    @Unique
    private static BlockPos[] boc$getBlockPosesFromPattern(int pattern, BlockPos abovePos){
        BlockPos[] returnValues = new BlockPos[7];
        switch (pattern) {
            case 0:
                returnValues[0] = abovePos;
                break;
            case 1:
                returnValues[0] = abovePos;
                returnValues[1] = abovePos.offset(0,0,-1);
                returnValues[2] = abovePos.offset(-1,0,0);
                returnValues[3] = abovePos.offset(-1,0,1);
                break;
            case 2:
                returnValues[0] = abovePos;
                returnValues[1] = abovePos.offset(0,0,-1);
                returnValues[2] = abovePos.offset(1,0,0);
                returnValues[3] = abovePos.offset(-1,0,0);
                returnValues[4] = abovePos.offset(-1,0,1);
                returnValues[5] = abovePos.offset(0,0,1);
                break;
            case 3:
                returnValues[0] = abovePos;
                returnValues[1] = abovePos.offset(1,0,1);
                returnValues[2] = abovePos.offset(-1,0,0);
                returnValues[3] = abovePos.offset(0,0,-1);
                break;
            case 4:
                returnValues[0] = abovePos;
                returnValues[1] = abovePos.offset(1,0,1);
                returnValues[2] = abovePos.offset(-1,0,1);
                returnValues[3] = abovePos.offset(-1,0,0);
                returnValues[4] = abovePos.offset(1,0,0);
                returnValues[5] = abovePos.offset(0,0,-1);
                break;
            case 5:
                returnValues[0] = abovePos.offset(-1,0,1);
                returnValues[1] = abovePos.offset(1,0,0);
                returnValues[2] = abovePos.offset(-1,0,-1);
                break;
            case 6:
                returnValues[0] = abovePos;
                returnValues[1] = abovePos.offset(0,0,1);
                returnValues[2] = abovePos.offset(1,0,0);
                returnValues[3] = abovePos.offset(-1,0,0);
                returnValues[4] = abovePos.offset(-1,0,-1);
                break;
            case 7:
                returnValues[0] = abovePos;
                returnValues[1] = abovePos.offset(1,0,1);
                returnValues[2] = abovePos.offset(1,0,0);
                returnValues[3] = abovePos.offset(0,0,1);
                returnValues[4] = abovePos.offset(-1,0,-1);
                returnValues[5] = abovePos.offset(0,0,-1);
                returnValues[6] = abovePos.offset(-1,0,0);
                break;
            case 8:
                returnValues[0] = abovePos;
                returnValues[1] = abovePos.offset(1,0,0);
                returnValues[2] = abovePos.offset(-1,0,0);
                returnValues[3] = abovePos.offset(0,0,1);
                returnValues[4] = abovePos.offset(0,0,-1);
                break;
            case 9:
                returnValues[0] = abovePos;
                returnValues[1] = abovePos.offset(1,0,0);
                returnValues[2] = abovePos.offset(-1,0,0);
                returnValues[3] = abovePos.offset(0,0,1);
                break;
            case 10:
                returnValues[0] = abovePos;
                returnValues[1] = abovePos.offset(0,0,1);
                returnValues[2] = abovePos.offset(1,0,0);
                break;
            case 11:
                returnValues[0] = abovePos;
                returnValues[1] = abovePos.offset(-1,0,0);
                returnValues[2] = abovePos.offset(0,0,-1);
                break;
        }
        return returnValues;
    }

}
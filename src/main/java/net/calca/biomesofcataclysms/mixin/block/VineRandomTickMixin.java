package net.calca.biomesofcataclysms.mixin.block;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.cataclysm.eternaleclipse.EternalEclipseStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(VineBlock.class)
public abstract class VineRandomTickMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void boc$onVineRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!ModUtils.isGameRunningWithCataclysm(level, AllCataclysms.ETERNAL_ECLIPSE)) return;
        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);
        String biomeID = ModUtils.getBiomeID(level, pos);
        if (!globalVars.deletedBiomes.contains(biomeID)) return;
        long elapsedTicks = globalVars.getEternalEclipseElapsedTicks(biomeID, level);
        EternalEclipseStage stage = EternalEclipseStage.getEternalEclipseStage(elapsedTicks);
        Random random1 = new Random();
        if (stage == EternalEclipseStage.CAVE_GERMINATION) {
            int randomInt = random1.nextInt(100) + 1;

            if (randomInt <= 50) {
                // 50% di probabilità: distruggi e basta
                level.destroyBlock(pos, false);
            } else if (randomInt <= 70) {
                // 20% di probabilità: tenta la trasformazione in Glow Lichen
                BlockState licheneState = Blocks.GLOW_LICHEN.defaultBlockState();
                boolean haAlmenoUnaFacciaValida = false;

                for (Direction direzione : Direction.values()) {
                    // 1. Controlliamo se la liana originale era attaccata a questa faccia
                    if (state.hasProperty(MultifaceBlock.getFaceProperty(direzione)) &&
                            state.getValue(MultifaceBlock.getFaceProperty(direzione))) {

                        // 2. VERIFICA D'APPOGGIO: Controlliamo se il blocco adiacente in quella direzione è solido/pieno
                        BlockPos bloccoAdiacentePos = pos.relative(direzione);
                        BlockState bloccoAdiacente = level.getBlockState(bloccoAdiacentePos);

                        // Se il blocco adiacente ha una superficie solida su quella faccia, possiamo attaccare il lichen
                        if (bloccoAdiacente.isFaceSturdy(level, bloccoAdiacentePos, direzione.getOpposite())) {
                            licheneState = licheneState.setValue(MultifaceBlock.getFaceProperty(direzione), true);
                            haAlmenoUnaFacciaValida = true;
                        }
                    }
                }

                // Rimuoviamo la liana
                level.destroyBlock(pos, false);

                // Piazziamo il lichen SOLO SE ha trovato almeno una superficie solida d'appoggio
                if (haAlmenoUnaFacciaValida) {
                    level.setBlockAndUpdate(pos, licheneState);
                }
            }
        }


    }
}
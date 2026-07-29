package net.calca.biomesofcataclysms.mixin.block;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.cataclysm.eternaleclipse.EternalEclipseStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(LeavesBlock.class)
public class LeavesRandomTickMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void onLeavesRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);
        if (!ModUtils.isGameRunningWithCataclysm(level, AllCataclysms.ETERNAL_ECLIPSE)) return;

        String biomeId = ModUtils.getBiomeID(level, pos);

        if (globalVars.deletedBiomes.contains(biomeId)) {
            long elapsedTicks = globalVars.getEternalEclipseElapsedTicks(biomeId, level);
            EternalEclipseStage stage = EternalEclipseStage.getEternalEclipseStage(elapsedTicks);
            boolean deveSparire = false;

            if (stage == EternalEclipseStage.START_DECAY) {
                long totalDurationTicks = EternalEclipseStage.START_DECAY_TICKS; // 4 minuti
                //La percentuale di decay aumenta progressivamente per 4 minuti, quando raggiunge il 100%
                int decayPercentage = (int) ((100 * elapsedTicks) / totalDurationTicks);
                decayPercentage = Math.max(0, Math.min(100, decayPercentage));

                if (random.nextInt(100) < decayPercentage) {
                    deveSparire = true;
                }
            } else {
                deveSparire = true;
            }

            if (deveSparire) {
                // Rimuoviamo la foglia colpita
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            // 2. IMPORTANTE: Questo blocca la logica vanilla!
            // Avendo gestito la foglia (o lasciata lì, o distrutta),
            // diciamo a Minecraft di non eseguire il resto del metodo originale.
            ci.cancel();
        }
    }
}
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
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(LiquidBlock.class)
public abstract class WaterRandomTickBehaviorMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void boc$onWaterRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        // Ci assicuriamo di colpire solo l'ACQUA (ed escludere la LAVA, che usa la stessa classe)
        if (state.is(Blocks.WATER)) {
            if (boc$shouldTick(state, level, pos)){
                PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);
                String biomeID = ModUtils.getBiomeID(level, pos);
                if (globalVars.deletedBiomes.contains(biomeID)){
                    long elapsedTicks = globalVars.getEternalEclipseElapsedTicks(biomeID, level);
                    EternalEclipseStage stage = EternalEclipseStage.getEternalEclipseStage(elapsedTicks);
                    Random random1 = new Random();
                    if (stage == EternalEclipseStage.CAVE_GERMINATION){
                        boolean hasFreezingPoint = false;
                        for (Direction dir : Direction.values()){
                            // Ottieni la posizione del blocco adiacente in base alla direzione corrente
                            BlockPos targetPos = pos.relative(dir);
                            BlockState targetState = level.getBlockState(targetPos);
                            if (!targetState.is(Blocks.AIR) && !targetState.is(Blocks.WATER) && !targetState.is(Blocks.LAVA)){
                                hasFreezingPoint = true;
                                break;
                            }
                        }
                        if (level.getBlockState(pos.above()).is(Blocks.AIR) || hasFreezingPoint){
                            level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
                        }
                    }else if (stage == EternalEclipseStage.START_DECAY
                            || stage == EternalEclipseStage.DEATH_OF_VEGETATION
                            || stage == EternalEclipseStage.START_SPREAD
                            || stage == EternalEclipseStage.MYCELIUM_SPREAD){
                        if (level.getBlockState(pos.above()).is(Blocks.ICE) && random1.nextInt(100)+1 <= 20){
                            level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
                        }
                    }else{
                        if (random1.nextInt(100)+1 <= 80){
                            boolean hasFreezingPoint = false;
                            for (Direction dir : Direction.values()){
                                // Ottieni la posizione del blocco adiacente in base alla direzione corrente
                                BlockPos targetPos = pos.relative(dir);
                                BlockState targetState = level.getBlockState(targetPos);
                                if (!targetState.is(Blocks.AIR) && !targetState.is(Blocks.WATER) && !targetState.is(Blocks.LAVA)){
                                    hasFreezingPoint = true;
                                    break;
                                }
                            }
                            if (hasFreezingPoint){
                                level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
                            }
                        }
                    }
                }
            }
            ci.cancel();
        }
    }

    @Unique
    private static boolean boc$shouldTick(BlockState state, ServerLevel level, BlockPos pos){
        if (level.getBlockState(pos.above()).is(Blocks.WATER)
                || level.getBlockState(pos.above()).is(Blocks.ICE)
                || level.getBlockState(pos.above()).is(Blocks.AIR)
                || level.getBlockState(pos.above()).is(Blocks.PACKED_ICE)){
            return ModUtils.isGameRunningWithCataclysm(level, AllCataclysms.ETERNAL_ECLIPSE);
        }
        return false;
    }
}
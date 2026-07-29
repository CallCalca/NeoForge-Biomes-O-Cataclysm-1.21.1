package net.calca.biomesofcataclysms.mixin.block;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.cataclysm.eternaleclipse.EternalEclipseStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(IceBlock.class)
public abstract class IceRandomTickMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void boc$onIceRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (ModUtils.isGameRunningWithCataclysm(level, AllCataclysms.ETERNAL_ECLIPSE)){
            String biomeID = ModUtils.getBiomeID(level, pos);
            PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);
            if (globalVars.deletedBiomes.contains(biomeID)){
                long elapsedTicks = globalVars.getEternalEclipseElapsedTicks(biomeID, level);
                EternalEclipseStage stage = EternalEclipseStage.getEternalEclipseStage(elapsedTicks);
                if (stage == EternalEclipseStage.FUNGUS_SPROUT){
                    Random random1 = new Random();
                    if (random1.nextInt(100)+1 <= 50){
                        if (!level.getBlockState(pos.below()).is(Blocks.WATER)
                                && !level.getBlockState(pos.below().below()).is(Blocks.WATER)
                                && !level.getBlockState(pos.below()).is(Blocks.WATER)
                                && !level.getBlockState(pos.below()).is(Blocks.WATER)){
                            level.setBlockAndUpdate(pos, Blocks.PACKED_ICE.defaultBlockState());
                        }
                    }
                } else if (stage == EternalEclipseStage.CAVE_GERMINATION) {
                    if (!level.getBlockState(pos.below()).is(Blocks.WATER)
                            && !level.getBlockState(pos.below()).is(Blocks.WATER)){
                        level.setBlockAndUpdate(pos, Blocks.PACKED_ICE.defaultBlockState());
                    }
                }
            }
        }
        ci.cancel();
    }

}
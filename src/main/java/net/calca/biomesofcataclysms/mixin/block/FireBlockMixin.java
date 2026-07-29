package net.calca.biomesofcataclysms.mixin.block;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public class FireBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onFireRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (ModUtils.isGameRunningWithCataclysm(level, AllCataclysms.ETERNAL_ECLIPSE)){
            PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);
            String biomeId = ModUtils.getBiomeID(level, pos);
            if (globalVars.deletedBiomes.contains(biomeId)){
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                ci.cancel();
            }
        }
    }
}
package net.calca.biomesofcataclysms.mixin.block.entity;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.cataclysm.eternaleclipse.EternalEclipseStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(CampfireBlockEntity.class)
public class CampfireBlockEntityMixin {

    private static int tickToExstinguish = 0;
    @Inject(method = "cookTick", at = @At("HEAD"), cancellable = true)
    private static void onCampfireTick(Level level, BlockPos pos, BlockState state, CampfireBlockEntity blockEntity, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel){
                if (ModUtils.isGameRunningWithCataclysm(serverLevel, AllCataclysms.ETERNAL_ECLIPSE)) {
                    PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(serverLevel);
                    String biomeId = ModUtils.getBiomeID(serverLevel, blockEntity.getBlockPos());
                    if (globalVars.deletedBiomes.contains(biomeId)) {
                        long elapsedTicks = globalVars.getEternalEclipseElapsedTicks(biomeId, serverLevel);
                        EternalEclipseStage stage = EternalEclipseStage.getEternalEclipseStage(elapsedTicks);
                        if (stage == EternalEclipseStage.CAVE_GERMINATION
                                || stage == EternalEclipseStage.FUNGUS_SPROUT
                                || stage == EternalEclipseStage.MYCELIUM_SPREAD
                                || stage == EternalEclipseStage.START_SPREAD) {
                            if (tickToExstinguish == 0) {
                                Random random1 = new Random();
                                tickToExstinguish = random1.nextInt(6, 81);
                            } else if (tickToExstinguish == 1) { //Il falò si spegne
                                level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 3);
                                tickToExstinguish--;
                            } else if (tickToExstinguish > 0) {
                                tickToExstinguish--;
                            }
                        }
                    }
                }
        }

    }
}
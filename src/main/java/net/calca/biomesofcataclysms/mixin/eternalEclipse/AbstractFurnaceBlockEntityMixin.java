package net.calca.biomesofcataclysms.mixin.eternalEclipse;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.cataclysm.eternaleclipse.EternalEclipseStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.common.Mod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
public class AbstractFurnaceBlockEntityMixin {

    @Inject(method = "getBurnDuration", at = @At("RETURN"), cancellable = true)
    private void halveBurnDuration(ItemStack fuel, CallbackInfoReturnable<Integer> cir) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        Level level = blockEntity.getLevel();
        if (level instanceof ServerLevel serverLevel){
            if (ModUtils.isGameRunningWithCataclysm(serverLevel, AllCataclysms.ETERNAL_ECLIPSE)){
                PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(serverLevel);
                String biomeId = ModUtils.getBiomeID(serverLevel, blockEntity.getBlockPos());
                if (globalVars.deletedBiomes.contains(biomeId)) {
                    long elapsedTicks = globalVars.getEternalEclipseElapsedTicks(biomeId, serverLevel);
                    EternalEclipseStage stage = EternalEclipseStage.getEternalEclipseStage(elapsedTicks);

                    int originalBurnTime = cir.getReturnValue();
                    if (stage == EternalEclipseStage.CAVE_GERMINATION){
                        // Se il carbone normalmente restituisce 1600 (8 oggetti), ora restituirà 400 (2 oggetti).
                        cir.setReturnValue(originalBurnTime / 4);

                    }else{
                        // Se il carbone normalmente restituisce 1600 (8 oggetti), ora restituirà 800 (4 oggetti).
                        cir.setReturnValue(originalBurnTime / 2);
                    }
                }

            }

        }
    }
}
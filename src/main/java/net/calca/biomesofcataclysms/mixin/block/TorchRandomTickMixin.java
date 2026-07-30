package net.calca.biomesofcataclysms.mixin.block;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.cataclysm.eternaleclipse.EternalEclipseStage;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.class)
public class TorchRandomTickMixin {

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void onRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        // Eseguiamo la logica SOLO se il blocco che sta tickando è una torcia
        if (state.getBlock() instanceof TorchBlock) {
            if (ModUtils.isGameRunningWithCataclysm(level, AllCataclysms.ETERNAL_ECLIPSE)){

                PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);
                String biomeId = ModUtils.getBiomeID(level, pos);
                if (globalVars.deletedBiomes.contains(biomeId)){
                    if (EternalEclipseStage.isBloodMoonEventActiveOnBiome(level, biomeId)) {
                            level.destroyBlock(pos, false);

                            // 2. Probabilità indipendente del 25% di droppare uno Stick
                            if (random.nextFloat() < 0.25f) {
                                Block.popResource(level, pos, new ItemStack(Items.STICK));
                            }

                            // 3. Probabilità indipendente del 25% di droppare un Charcoal
                            if (random.nextFloat() < 0.25f) {
                                Block.popResource(level, pos, new ItemStack(Items.CHARCOAL));
                            }

                    }

                }

            }
        }

    }
}
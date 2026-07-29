package net.calca.biomesofcataclysms.mixin.enable;


import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.client.ClientDataAccessPoint;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class WaterRandomTickStateMixin {
    @Shadow public abstract Block getBlock();

    @Inject(method = "isRandomlyTicking", at = @At("HEAD"), cancellable = true)
    private void boc$forceWaterRandomTick(CallbackInfoReturnable<Boolean> cir) {
        // Abilitiamo il random tick sia per l'acqua statica che per quella in movimento
        if (this.getBlock() == Blocks.WATER
                && ModUtils.decodeCataclysmFromString(ClientDataAccessPoint.clientData.mapData.cataclysm) == AllCataclysms.ETERNAL_ECLIPSE) {
            cir.setReturnValue(true);
        }
    }
}
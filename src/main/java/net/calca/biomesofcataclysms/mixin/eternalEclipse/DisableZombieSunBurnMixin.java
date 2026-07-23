package net.calca.biomesofcataclysms.mixin.eternalEclipse;

import net.calca.biomesofcataclysms.data.PersistentData;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public class DisableZombieSunBurnMixin {

    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void onIsSunBurnTick(CallbackInfoReturnable<Boolean> cir) {
        Mob mob = (Mob) (Object) this;

        if (mob.level() instanceof ServerLevel level) {
            PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);

            if (globalVars != null) {
                // 1. Ottieni l'Holder del bioma
                Holder<Biome> biomeHolder = level.getBiome(mob.blockPosition());

                // 2. Estrai la ResourceLocation (l'ID sotto forma di stringa, es: "minecraft:desert")
                String biomeId = biomeHolder.unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("");

                // 3. Controlla se il bioma attuale fa parte di quelli cancellati
                if (globalVars.deletedBiomes.contains(biomeId)) {
                    // Se il bioma è stato cancellato dal cataclisma, il mob NON deve prendere fuoco dal sole
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
package net.calca.biomesofcataclysms.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValueInjector;
import net.calca.biomesofcataclysms.data.PersistentData; // Adatta al tuo progetto
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Mixin(Biome.class)
public abstract class ModifyChunkTemperatureMixin {

    @Inject(method = "getTemperature", at = @At("HEAD"), cancellable = true)
    private void onGetTemperature(BlockPos pos, CallbackInfoReturnable<Float> cir) {
        Biome currentBiome = (Biome) (Object) this;
        var server = ServerLifecycleHooks.getCurrentServer();

        // 1. GESTIONE LATO SERVER (Controlliamo se siamo sul thread effettivo del Server)
        if (server != null && server.isSameThread()) {
            var biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
            Holder<Biome> biomeHolder = biomeRegistry.wrapAsHolder(currentBiome);
            String biomeId = biomeHolder.unwrapKey().map(key -> key.location().toString()).orElse("");
            for (ServerLevel level : server.getAllLevels()) {
                PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(level);
                float originalTemp = cir.getReturnValueF();
                float returnValue = originalTemp - (float) ((int)(globalVars.getEternalEclipseElapsedTicks(biomeId, level)/20)*0.016);
                if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                    if (globalVars != null && globalVars.deletedBiomes.contains(biomeId)) {
                        cir.setReturnValue(returnValue);
                        return; // CRUCIALE: Ferma il server qui, impedendogli di scendere sotto!
                    }
                }
            }
        }
        // 2. GESTIONE LATO CLIENT (Se non è il server, è il thread grafico del Client)
        else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                // USIAMO IL REGISTRO DEL CLIENT!
                var clientRegistry = mc.level.registryAccess().registryOrThrow(Registries.BIOME);
                Holder<Biome> biomeHolder = clientRegistry.wrapAsHolder(currentBiome);
                String biomeId = biomeHolder.unwrapKey().map(key -> key.location().toString()).orElse("");

                PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(mc.level);
                if (globalVars != null && globalVars.deletedBiomes.contains(biomeId)) {
                    cir.setReturnValue(-0.5F);
                }
            }
        }
    }
}
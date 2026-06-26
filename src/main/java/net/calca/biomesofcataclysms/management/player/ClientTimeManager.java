package net.calca.biomesofcataclysms.management.player;

import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.kinds.IdF;
import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.mixin.ClientLevelDataAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;


public class ClientTimeManager {
    public static float transitionProgress = 0.0f; //Quando parte la transizione, il suo valore si accumula fino a 1.0f. Resta 1.0 fino a che non
                                                        //parte la transizione nuovamente, dove tornerà a 0.0f
    public static final float TRANSITION_SPEED = 0.02f; // Velocità della transizione. Valore 0.01f -> Durata transizione 1 secondo

    public static long startingTime = -1L; //Valore default
    public static long targetTime = 18000L; // Mezzanotte fissa
    public static float shortestDistance = 0.0f; //Sceglie se fara andare indietro il tempo o avanti

    public static int errorDelay = 60;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(mc.level);

        if (globalVars.state != 2){
            if (mc.level.getGameTime() % 20 == 0){
                System.out.println("ClientTimeManager.tick() | Sending client sync request of type 1");
                PacketDistributor.sendToServer(new PersistentData.RequestCataclysmSyncMessage());
            }
            return;
        }else{
            if (mc.level.getGameTime() % 200 == 0){
                System.out.println("ClientTimeManager.tick() | Sending client sync request of type 1");
                PacketDistributor.sendToServer(new PersistentData.RequestCataclysmSyncMessage());
            }
        }

        //No need to return if state != 2: i want the time to shift even if the game is paused
        AllCataclysms type = ModUtils.decodeCataclysmFromString(globalVars.cataclysm);
        if (type == null){
            if (errorDelay == 60){
                ModUtils.sendChatMessage(mc.level, ModUtils.buildWarningMessage(false, Component.nullToEmpty("ClientTimeManager.tick"),
                        Component.translatable("warning.biomesofcataclysms.errorAboutToBeThrown", 11, 60)));
            }
            errorDelay--;
            System.out.println("ClientTimeManager.tick() | Sending client sync request of type 1");
            PacketDistributor.sendToServer(new PersistentData.RequestCataclysmSyncMessage());
            if (errorDelay <= 0){
                ModUtils.sendChatMessage(mc.level, ModUtils.buildErrorMessage(true, 11, Component.literal("ClientTimeManager.tick"),
                        Component.translatable("error.biomesofcataclysms.error11")));
                return;
            }
        }else{
            if (errorDelay != 60 && errorDelay > 0){
                ModUtils.sendChatMessage(mc.level, ModUtils.buildSuccessMessage(Component.nullToEmpty("ClientTimeManager.tick"),
                        Component.translatable("success.biomesofcataclysms.errorSolved"), "Client data sync type 1",
                        60 - errorDelay));
            }
            errorDelay = 60;
        }
        if (type != AllCataclysms.ETERNAL_ECLIPSE) return;

        // 1. Controllo Bioma
        Holder<Biome> biomeHolder = mc.level.getBiome(mc.player.blockPosition());
        Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();
        if (biomeKey.isEmpty()) return;
        String biomeId = biomeKey.get().location().toString();
        boolean isInTargetBiome = false;
        if (globalVars.deletedBiomes.contains(biomeId)) isInTargetBiome = true;



        // 2. Incremento/Decremento lineare e pulito del progresso (0.0 -> 1.0)
        if (isInTargetBiome) {
            if (transitionProgress < 1.0f) {
                transitionProgress = Math.min(1.0f, transitionProgress + TRANSITION_SPEED);
            }
        } else {
            if (transitionProgress > 0.0f) {
                transitionProgress = Math.max(0.0f, transitionProgress - TRANSITION_SPEED);
            }
        }

        // 3. Gestione degli stati di transizione
        if (transitionProgress > 0.0f) {
            long dist = targetTime - startingTime;
            // Se la transizione è appena iniziata, registriamo i punti di partenza fissi
            if (startingTime == -1L) {
                ClientLevelDataAccessor data =
                        (ClientLevelDataAccessor) mc.level.getLevelData();
                startingTime = data.boc$getRawDayTime() % 24000L;
                // Calcoliamo la distanza più breve sulla ruota delle 24000 ore UNA VOLTA SOLA
                if (dist > 12000L) dist -= 24000L;
                if (dist < -12000L) dist += 24000L;
            }else{
                startingTime++;
                if (dist > 12000L) dist -= 24000L;
                if (dist < -12000L) dist += 24000L;
            }
            shortestDistance = dist;
        } else {
            // Quando il progresso torna esattamente a 0.0, resettiamo tutto per il prossimo ciclo
            startingTime = -1L;
            shortestDistance = 0.0f;
        }
    }

    public static boolean isOverriding() {
        return transitionProgress > 0.0f;
    }

    public static long getRenderTime(long originalServerTime) {
        if (!isOverriding()) return originalServerTime;

        long days = originalServerTime / 24000L;

        // Applichiamo l'interpolazione lineare basata sulla distanza fissa calcolata all'inizio
        float currentLocalTime = startingTime + (shortestDistance * transitionProgress);

        // Correzione matematica per l'orario circolare
        if (currentLocalTime < 0.0f) currentLocalTime += 24000.0f;
        currentLocalTime %= 24000.0f;

        return (days * 24000L) + (long) currentLocalTime;
    }
}
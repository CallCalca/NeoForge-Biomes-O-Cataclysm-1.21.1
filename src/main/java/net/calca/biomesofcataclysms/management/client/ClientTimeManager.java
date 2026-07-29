package net.calca.biomesofcataclysms.management.client;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.cataclysm.eternaleclipse.MoonsManager;
import net.calca.biomesofcataclysms.mixin.client.ClientLevelDataAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

import static net.calca.biomesofcataclysms.data.client.ClientDataAccessPoint.clientData;


public class ClientTimeManager {
    public static final float TRANSITION_SPEED = 0.0120f; // Velocità della transizione. Valore 0.01f -> Durata transizione 1 secondo


    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        //No need to return if state != 2: i want the time to shift even if the game is paused
        AllCataclysms type = ModUtils.decodeCataclysmFromString(clientData.mapData.cataclysm);
        if (type != AllCataclysms.ETERNAL_ECLIPSE) return;

        if (clientData.mapData.state != 2){
            if (mc.level.getGameTime() % 20 == 0){
                System.out.println("ClientTimeManager.tick() | Sending client sync request of type 1");
                PacketDistributor.sendToServer(new PersistentData.RequestDedicatedSyncMessage());
            }
        }else{
            if (mc.level.getGameTime() % 200 == 0){
                System.out.println("ClientTimeManager.tick() | Sending client sync request of type 1");
                PacketDistributor.sendToServer(new PersistentData.RequestDedicatedSyncMessage());
            }
        }

        if (type == null){
            if (clientData.dayTimeData.errorDelay == 60){
                ModUtils.sendChatMessage(mc.level, ModUtils.buildWarningMessage(false, Component.nullToEmpty("ClientTimeManager.tick"),
                        Component.translatable("warning.biomesofcataclysms.errorAboutToBeThrown", 11, 60)));
            }
            clientData.dayTimeData.errorDelay--;
            System.out.println("ClientTimeManager.tick() | Sending client sync request of type 1");
            PacketDistributor.sendToServer(new PersistentData.RequestDedicatedSyncMessage());
            if (clientData.dayTimeData.errorDelay <= 0){
                ModUtils.sendChatMessage(mc.level, ModUtils.buildErrorMessage(true, 11, Component.literal("ClientTimeManager.tick"),
                        Component.translatable("error.biomesofcataclysms.error11")));
                return;
            }
        }else{
            if (clientData.dayTimeData.errorDelay != 60 && clientData.dayTimeData.errorDelay > 0){
                ModUtils.sendChatMessage(mc.level, ModUtils.buildSuccessMessage(Component.nullToEmpty("ClientTimeManager.tick"),
                        Component.translatable("success.biomesofcataclysms.errorSolved"), "Client data sync type 1",
                        60 - clientData.dayTimeData.errorDelay));
            }
            clientData.dayTimeData.errorDelay = 60;
        }

        // 1. Controllo Bioma
        Holder<Biome> biomeHolder = mc.level.getBiome(mc.player.blockPosition());
        Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();
        if (biomeKey.isEmpty()) return;
        String biomeId = biomeKey.get().location().toString();
        boolean isInTargetBiome = false;
        if (clientData.mapData.deletedBiomes.contains(biomeId)) isInTargetBiome = true;



        clientData.skyData.isInDeletedBiome = isInTargetBiome;
        // 2. Incremento/Decremento lineare e pulito del progresso (0.0 -> 1.0)
        if (isInTargetBiome) {
            // Aggiorna i colori basandosi sul valore corrente (sia che salga, sia che scenda)
            clientData.dayTimeData.transitionProgress = Math.min(1.0f, clientData.dayTimeData.transitionProgress + TRANSITION_SPEED);
        } else {
            // Quando cala a 0.0f esatto, pulisce tutto e rimette i valori vanilla fissi
            MoonsManager.reset();
            clientData.dayTimeData.transitionProgress = Math.max(0.0f, clientData.dayTimeData.transitionProgress - TRANSITION_SPEED);
        }

        // 3. Gestione degli stati di transizione
        if (clientData.dayTimeData.transitionProgress > 0.0f) {
            long dist = clientData.dayTimeData.targetTime - clientData.dayTimeData.startingTime;
            // Se la transizione è appena iniziata, registriamo i punti di partenza fissi
            if (clientData.dayTimeData.startingTime == -1L) {
                ClientLevelDataAccessor data =
                        (ClientLevelDataAccessor) mc.level.getLevelData();
                clientData.dayTimeData.startingTime = data.boc$getRawDayTime() % 24000L;
                // Calcoliamo la distanza più breve sulla ruota delle 24000 ore UNA VOLTA SOLA
                if (dist > 12000L) dist -= 24000L;
                if (dist < -12000L) dist += 24000L;
            }else{
                clientData.dayTimeData.startingTime++;
                if (dist > 12000L) dist -= 24000L;
                if (dist < -12000L) dist += 24000L;
            }
            clientData.dayTimeData.shortestDistance = dist;
        } else {
            // Quando il progresso torna esattamente a 0.0, resettiamo tutto per il prossimo ciclo
            clientData.dayTimeData.startingTime = -1L;
            clientData.dayTimeData.shortestDistance = 0.0f;
        }
    }

    public static boolean isOverriding() {
        return clientData.dayTimeData.transitionProgress > 0.0f;
    }

    public static long getRenderTime(long originalServerTime) {
        if (!isOverriding()) return originalServerTime;

        long days = originalServerTime / 24000L;

        // Applichiamo l'interpolazione lineare basata sulla distanza fissa calcolata all'inizio
        float currentLocalTime = clientData.dayTimeData.startingTime + (clientData.dayTimeData.shortestDistance * clientData.dayTimeData.transitionProgress);

        // Correzione matematica per l'orario circolare
        if (currentLocalTime < 0.0f) currentLocalTime += 24000.0f;
        currentLocalTime %= 24000.0f;

        return (days * 24000L) + (long) currentLocalTime;
    }
}
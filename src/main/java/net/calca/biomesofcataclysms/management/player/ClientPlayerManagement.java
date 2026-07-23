package net.calca.biomesofcataclysms.management.player;

import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.management.server.tmep.MoonController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;

@EventBusSubscriber(modid = BiomesOfCataclysms.MODID, value = Dist.CLIENT)
public final class ClientPlayerManagement {

    private ClientPlayerManagement() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientSound.tick(event);
        ClientTimeManager.tick(event);
        ClientMoonManager.tick(event);
        MoonController.tick();
        // Dentro ClientTimeManager.java -> metodo tick()
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            if (mc.level.getGameTime() % 200 == 0){
                PacketDistributor.sendToServer(new PersistentData.RequestRealServerTimeMessage());
            } else if (mc.level.getGameTime() % 20 == 0) {
                MoonController.isDirty = true;
            }
        }
        MoonController.realServerTime++;
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {

        // 1. Reset di MapVariables.clientSide
        if (PersistentData.MapVariables.clientSide != null) {
            PersistentData.MapVariables.clientSide.cataclysm = "NULL";
            PersistentData.MapVariables.clientSide.state = 0;
            PersistentData.MapVariables.clientSide.deletedBiomes.clear();
            PersistentData.MapVariables.clientSide.biomesAffected = 0;

            if (PersistentData.MapVariables.clientSide.eternalEclipseBloodMoonEvents != null) {
                PersistentData.MapVariables.clientSide.eternalEclipseBloodMoonEvents.clear();
            } else {
                PersistentData.MapVariables.clientSide.eternalEclipseBloodMoonEvents = new HashMap<>();
            }
        }
        ClientTimeManager.logOut();
        MoonController.logOut();
        ClientSound.logOut();
        ClientMoonManager.logOut();
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        PacketDistributor.sendToServer(new PersistentData.RequestDedicatedSyncMessage());
        MoonController.logIn();
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        float redness = 1.0F - MoonController.green;

        if (redness > 0.0F) {
            float originalRed = event.getRed();
            float originalGreen = event.getGreen();
            float originalBlue = event.getBlue();

            // Colore target per la nebbia: leggermente più luminoso del cielo per simulare la luce riflessa
            float targetRed = 0.25F;
            float targetGreen = 0.02F;
            float targetBlue = 0.02F;

            float newRed = Mth.lerp(redness, originalRed, targetRed);
            float newGreen = Mth.lerp(redness, originalGreen, targetGreen);
            float newBlue = Mth.lerp(redness, originalBlue, targetBlue);

            event.setRed(newRed);
            event.setGreen(newGreen);
            event.setBlue(newBlue);
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        float redness = 1.0F - MoonController.green;

        // Se la luna inizia a diventare rossa, accorciamo la nebbia per creare ansia
        if (redness > 0.1F) {
            float originalNear = event.getNearPlaneDistance();
            float originalFar = event.getFarPlaneDistance();

            // Riduciamo la distanza visiva fino al 50% in base a quanto è rossa la luna
            float fogReductionFactor = 1.0F - (redness * 0.5F);

            event.setNearPlaneDistance(originalNear * fogReductionFactor);
            event.setFarPlaneDistance(originalFar * fogReductionFactor);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        float redness = 1.0F - MoonController.green;

        if (redness > 0.0F) {
            GuiGraphics guiGraphics = event.getGuiGraphics();

            // Prendiamo le dimensioni esatte della finestra di gioco in quel momento
            int width = guiGraphics.guiWidth();
            int height = guiGraphics.guiHeight();

            // 1. Calcoliamo l'intensità della trasparenza (massimo 30% = 0.3F)
            float alphaFloat = redness * 0.085F;
            int alphaInt = (int) (alphaFloat * 255.0F);

            // 2. Costruiamo il colore in formato ARGB (Alpha, Rosso, Verde, Blu)
            // L'Alpha controlla quanto è trasparente. Il Rosso è al massimo (255), Verde e Blu a 0.
            int color = (alphaInt << 24) | (255 << 16) | (0 << 8) | 0;

            // 3. Disegniamo il rettangolo su tutto lo schermo
            // Essendo l'evento "Pre", colorerà il mondo 3D, ma la hotbar verrà disegnata DOPO, restando pulita.
            guiGraphics.fill(0, 0, width, height, color);
        }
    }
}
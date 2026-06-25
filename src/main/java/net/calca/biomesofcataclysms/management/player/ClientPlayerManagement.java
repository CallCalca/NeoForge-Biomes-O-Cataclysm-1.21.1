package net.calca.biomesofcataclysms.management.player;

import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = BiomesOfCataclysms.MODID, value = Dist.CLIENT)
public final class ClientPlayerManagement {

    private ClientPlayerManagement() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
            ClientTimeManager.tick();
    }

}
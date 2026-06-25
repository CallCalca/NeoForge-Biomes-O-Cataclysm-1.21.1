package net.calca.biomesofcataclysms.event;

import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.calca.biomesofcataclysms.manager.ClientTimeManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = BiomesOfCataclysms.MODID, value = Dist.CLIENT)
public final class ModClientEvent {

    private ModClientEvent() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
            ClientTimeManager.tick();
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
    }
}
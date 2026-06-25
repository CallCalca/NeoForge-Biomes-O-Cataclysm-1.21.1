package net.calca.biomesofcataclysms.data;

//Questa classe contiene gli eventi che permettono la sincronizzazione tra dati Persistent e Runtime

import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.bar.ProgressBarManager;
import net.calca.biomesofcataclysms.data.chunk.ChunkInstance;
import net.calca.biomesofcataclysms.management.chunk.ChunkProcessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = BiomesOfCataclysms.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DataSync {

    public static void loadPersistentToRuntime(ServerLevel server) {
        PersistentData.MapVariables saved = PersistentData.MapVariables.get(server);
        RuntimeData.INITIAL_ORDER.clear();
        RuntimeData.DYNAMIC_ORDER.clear();
        RuntimeData.INITIAL_STATES.clear();
        RuntimeData.DYNAMIC_STATES.clear();
        RuntimeData.CHUNKS.clear();

        for (var e : saved.initialOrder.entrySet())
            RuntimeData.INITIAL_ORDER.put(e.getKey(), new ArrayDeque<>(e.getValue()));
        for (var e : saved.dynamicOrder.entrySet())
            RuntimeData.DYNAMIC_ORDER.put(e.getKey(), new ArrayDeque<>(e.getValue()));
        for (var e : saved.initialStates.entrySet())
            RuntimeData.INITIAL_STATES.put(e.getKey(), copyState(e.getValue()));
        for (var e : saved.dynamicStates.entrySet())
            RuntimeData.DYNAMIC_STATES.put(e.getKey(), copyState(e.getValue()));
        for (var dimEntry : saved.chunks.entrySet()) {
            Map<Long, ChunkInstance> dimCopy = new HashMap<>(dimEntry.getValue());
            RuntimeData.CHUNKS.put(dimEntry.getKey(), dimCopy);
        }
        saved.syncData(server, true, false);
    }
    public static ChunkProcessor.DimensionState copyState(ChunkProcessor.DimensionState src) {
        ChunkProcessor.DimensionState dst = new ChunkProcessor.DimensionState();
        dst.currentKey = src.currentKey;
        dst.step = src.step;
        return dst;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(event.getServer().overworld());
        if (variables.state == 2) { //Game data might be corrupted: The server was not shut down correctly last time.
            variables.dataCondition = 2;
            variables.syncData(event.getServer().overworld(), true, false);

        }
        for (ServerLevel serverLevel : event.getServer().getAllLevels()) {
            ProgressBarManager.initializeOnServerStart(variables, serverLevel);
        }
        loadPersistentToRuntime(event.getServer().overworld());
    }


    @SubscribeEvent
    public static void onServerStop(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();

        for (ServerLevel serverLevel : server.getAllLevels()) {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack()
                            .withSuppressedOutput()
                            .withPermission(4),   // livello OP massimo
                    "biomesOfCataclysms pause"
            );

            ModUtils.sendChatMessage(serverLevel, Component.literal("Server Shutting Down: Game paused"));
        }

    }


}
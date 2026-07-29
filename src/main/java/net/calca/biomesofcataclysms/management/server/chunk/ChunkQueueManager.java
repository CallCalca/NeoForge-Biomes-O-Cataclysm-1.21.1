package net.calca.biomesofcataclysms.management.server.chunk;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.cataclysm.sunburn.SunBurnStage;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.calca.biomesofcataclysms.data.server.RuntimeData;
import net.calca.biomesofcataclysms.data.server.chunk.ChunkInstance;
import net.calca.biomesofcataclysms.data.server.chunk.ChunkState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.*;

import static net.calca.biomesofcataclysms.cataclysm.sunburn.SunBurnStage.getSunBurnStage;


public class ChunkQueueManager {
    public static Map<Long, ChunkInstance> getOrCreateDimChunkMap(ResourceKey<Level> dim) {
        return RuntimeData.CHUNKS.computeIfAbsent(dim, k -> new HashMap<>());
    }
    protected static ArrayDeque<Long> getOrCreateQueue(Map<ResourceKey<Level>, ArrayDeque<Long>> store, ResourceKey<Level> dim) {
        ArrayDeque<Long> queue = store.get(dim);
        if (queue == null) {
            queue = new ArrayDeque<>();
            store.put(dim, queue);
        }
        return queue;
    }

    public static void registerInitialChunk(ServerLevel level, ChunkPos pos) {
        ResourceKey<Level> dim = level.dimension();
        long packed = pos.toLong();

        Map<Long, ChunkInstance> dimMap = getOrCreateDimChunkMap(dim);
        ChunkInstance mod = dimMap.get(packed);

        if (mod == null) {
            mod = new ChunkInstance(dim, pos, level);
            dimMap.put(packed, mod);
        }

        mod.initialWave = true;

        ArrayDeque<Long> queue = getOrCreateQueue(RuntimeData.INITIAL_ORDER, dim);
        if (!queue.contains(packed)) {
            queue.addLast(packed);
        }
    }
    public static void registerDynamicChunk(ServerLevel level, ChunkPos pos) {
        ResourceKey<Level> dim = level.dimension();
        long packed = pos.toLong();

        Map<Long, ChunkInstance> dimMap = getOrCreateDimChunkMap(dim);
        ChunkInstance mod = dimMap.get(packed);

        if (mod == null) {
            mod = new ChunkInstance(dim, pos, level);
            dimMap.put(packed, mod);
        }

        // Se era stato chiuso prima, lo riapriamo
        if (mod.state == ChunkState.DONE) {
            mod.state = ChunkState.QUEUED;
            mod.activeBiome = null;
            mod.activeBiomeStep = 0;
        }

        mod.dynamic = true;

        ArrayDeque<Long> queue = getOrCreateQueue(RuntimeData.DYNAMIC_ORDER, dim);
        if (!queue.contains(packed)) {
            queue.addLast(packed);
        }
    }

    public static void refreshDynamicQueue(ServerLevel level) {
        PersistentData.MapVariables mapVariables = PersistentData.MapVariables.get(level);
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(dim);
        if (registry == null || registry.isEmpty()) return;

        List<ChunkInstance> valid = new ArrayList<>();

        for (ChunkInstance mod : registry.values()) {
            AllCataclysms type = ModUtils.decodeCataclysmFromString(mapVariables.cataclysm);
            if (type == null){
                type = AllCataclysms.DESTROYED;
                ModUtils.sendChatMessage(level, ModUtils.buildErrorMessage(true, 11, Component.literal("refreshDynamicQueue"),
                        Component.translatable("error.biomesofcataclysms.error11")));
                return;
            }

            // --- FIX CRASH: Sostituito Set.of con un controllo null semplice ---
            SunBurnStage stage = SunBurnStage.FIRE;
            if (mod.activeBiome != null) {
                long elapsed = mapVariables.getSunBurnElapsedTicks(mod.activeBiome, level);
                stage = getSunBurnStage(elapsed);
            }

            // LOGICA ORIGINALE (Invariata, inclusa la tua condizione su INSTANT_TRANSFORM)
            if (mod.state == ChunkState.DONE
                    || ((type == AllCataclysms.SUN_BURNT && mod.state == ChunkState.PARTIAL) && stage != SunBurnStage.INSTANT_TRANSFORM)
                    || (type == AllCataclysms.FLOODED && mod.state == ChunkState.PARTIAL)) continue;
            if (mod.initialWave) continue;
            if (!mod.dynamic) continue;
            if (!level.hasChunk(mod.pos.x, mod.pos.z)) continue;
            if (!isNearAnyPlayer(level, mod.pos, 32)) continue;

            mod.priorityScore = nearestPlayerDistanceSq(level, mod.pos);
            valid.add(mod);
        }

        if (valid.isEmpty()) return;

        valid.sort(Comparator.comparingDouble(m -> m.priorityScore));

        ArrayDeque<Long> newOrder = new ArrayDeque<>();
        for (ChunkInstance mod : valid) {
            newOrder.addLast(ChunkPos.asLong(mod.pos.x, mod.pos.z));
        }

        RuntimeData.DYNAMIC_ORDER.put(dim, newOrder);
    }

    //Questo metodo elimina dalla coda:
    // 1. Chunk inesistenti;
    // 2. Chunk attualmente non caricati;
    // 3. Chunk già elaborati (DONE)
    public static void cleanDynamicQueue(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        ArrayDeque<Long> queue = RuntimeData.DYNAMIC_ORDER.get(dim);
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(dim);

        if (queue == null || queue.isEmpty() || registry == null) return;

        queue.removeIf(key -> {
            ChunkInstance mod = registry.get(key);
            if (mod == null) return true;

            // se il chunk non è più caricato, esce dalla coda
            if (!level.hasChunk(mod.pos.x, mod.pos.z)) {
                mod.initialWave = false;
                mod.dynamic = true;
                mod.state = ChunkState.QUEUED;
                return true;
            }

            // se è già finito, esce dalla coda
            return mod.state == ChunkState.DONE;
        });
    }
    public static boolean isQueueEmpty(ServerLevel level) {
        ArrayDeque<Long> order = RuntimeData.DYNAMIC_ORDER.get(level.dimension());
        return order == null || order.isEmpty();
    }

    //Questo metodo recupera chunk che sono andati, per qualche motivo, persi nella coda. Reinserisce questi chunk all'interno della coda, per essere
    //rielaborati.
    public static void rescueMissedChunks(ServerLevel level, int scanRadiusChunks) {
        ResourceKey<Level> dim = level.dimension();
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(dim);
        if (registry == null || registry.isEmpty() || level.players().isEmpty()) return;

        long now = level.getGameTime();
        Set<Long> visited = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            ChunkPos center = new ChunkPos(player.blockPosition());

            for (int dx = -scanRadiusChunks; dx <= scanRadiusChunks; dx++) {
                for (int dz = -scanRadiusChunks; dz <= scanRadiusChunks; dz++) {
                    ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                    long key = ChunkPos.asLong(pos.x, pos.z);

                    if (!visited.add(key)) continue;
                    if (!level.hasChunk(pos.x, pos.z)) continue;

                    ChunkInstance mod = registry.get(key);

                    // Se è già gestito, non fare nulla
                    if (mod != null) {
                        if (mod.state == ChunkState.DONE) continue;
                        if (mod.state == ChunkState.PROCESSING) continue;
                    }

                    if (mod == null) {
                        mod = new ChunkInstance(dim, pos, level);
                        registry.put(key, mod);
                    }

                    mod.initialWave = false;
                    mod.dynamic = true;
                    mod.state = ChunkState.QUEUED;
                    mod.lastSeenTick = now;

                    registerDynamicChunk(level, pos);
                }
            }
        }
    }

    public static void wakeUpPartialChunks(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();

        // Accediamo ai buffer globali che abbiamo definito all'inizio
        Map<Long, ChunkInstance> registry = RuntimeData.CHUNKS.get(dim);
        ArrayDeque<Long> dynamicQueue = RuntimeData.DYNAMIC_ORDER.get(dim);

        // Se il registro o la coda non esistono per questa dimensione, usciamo
        if (registry == null || dynamicQueue == null) return;

        for (Map.Entry<Long, ChunkInstance> entry : registry.entrySet()) {
            ChunkInstance mod = entry.getValue();

            // Risvegliamo solo i chunk che sono in pausa (PARTIAL)
            if (mod.state == ChunkState.PARTIAL) {
                long posHash = entry.getKey();

                // Lo aggiungiamo alla coda dinamica solo se non è già dentro
                // Nota: se la coda diventa enorme, questo .contains() potrebbe rallentare.
                if (!dynamicQueue.contains(posHash)) {
                    dynamicQueue.addLast(posHash);
                }
            }
        }
    }


    public static boolean isNearAnyPlayer(ServerLevel level, ChunkPos pos, int radius) {
        for (ServerPlayer player : level.players()) {
            ChunkPos pc = new ChunkPos(player.blockPosition());

            int dx = Math.abs(pos.x - pc.x);
            int dz = Math.abs(pos.z - pc.z);

            if (Math.max(dx, dz) <= radius) {
                return true;
            }
        }
        return false;
    }
    public static double nearestPlayerDistanceSq(ServerLevel level, ChunkPos pos) {
        int centerX = (pos.x << 4) + 8;
        int centerZ = (pos.z << 4) + 8;

        double best = Double.MAX_VALUE;

        for (ServerPlayer player : level.players()) {
            double d = player.distanceToSqr(centerX, player.getY(), centerZ);
            if (d < best) best = d;
        }

        return best;
    }
}

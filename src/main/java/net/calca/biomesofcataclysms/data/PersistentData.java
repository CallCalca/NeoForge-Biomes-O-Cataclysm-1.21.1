package net.calca.biomesofcataclysms.data;


import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.calca.biomesofcataclysms.data.chunk.ChunkInstance;
import net.calca.biomesofcataclysms.management.chunk.ChunkProcessor;
import net.calca.biomesofcataclysms.data.chunk.ChunkState;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;

import java.util.*;
import java.util.stream.Collectors;

import static net.calca.biomesofcataclysms.data.DataSavingHelper.*;
import static net.calca.biomesofcataclysms.management.chunk.flood.FloodProcessorHelper.resetFloodWaves;


@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class PersistentData {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, BiomesOfCataclysms.MODID);

    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
        BiomesOfCataclysms.addNetworkMessage(SavedDataSyncMessage.TYPE, SavedDataSyncMessage.STREAM_CODEC, SavedDataSyncMessage::handleData);
    }

    @EventBusSubscriber
    public static class EventBusVariableHandlers {
        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                SavedData mapdata = MapVariables.get(event.getEntity().level());
                if (mapdata != null) { //No use out of this class
                    //PacketDistributor.sendToPlayer(player, new SavedDataSyncMessage(0, mapdata));
                }
            }
        }
    }

    //---------------------------------------------------

    public static class MapVariables extends SavedData {
        public static final String DATA_NAME = "biomesofcataclysms_mapvars";

        public final Map<ResourceKey<Level>, Map<Long, ChunkInstance>> chunks = new HashMap<>();
        public final Map<ResourceKey<Level>, ArrayDeque<Long>> initialOrder = new HashMap<>();
        public final Map<ResourceKey<Level>, ArrayDeque<Long>> dynamicOrder = new HashMap<>();

        public final Map<ResourceKey<Level>, ChunkProcessor.DimensionState> initialStates = new HashMap<>();
        public final Map<ResourceKey<Level>, ChunkProcessor.DimensionState> dynamicStates = new HashMap<>();
        public final Map<String, Integer> floodedHeights = new HashMap<>();
        public final Map<String, Long> sunBurnStartTicks = new HashMap<>();

        public boolean debugMode = false;
        public boolean graceCheckHappen = true;
        public boolean allNetherBiomesHitShouldNotify = true;
        public boolean allEndBiomesHitShouldNotify = true;
        public boolean allOverworldBiomesHitShouldNotify = true;
        public int totalBiomes = 0;
        public List<String> shuffledBiomes = new ArrayList<>(); // Coda dei prossimi biomi
        public List<String> overworldBiomeList = new ArrayList<>(); // lista dei biomi dell' over world
        public Set<String> deletedBiomes = new HashSet<>();    // Registro dei biomi già cancellati
        public Set<String> processedChunks = new HashSet<>(); //Chunk processati

        public int biomesToAffect = totalBiomes;
        public int biomesAffected = deletedBiomes.size();

        //Optimization
        public int pcPower = 1;
        public int radius = 16;
        public double destructionSpeed = 1;
        //---

        public int mode = 0; //0 = Biom Remover: chunks of a given biome will get completely deleted;
                            // 1 = Apocalypse: random disasters will affect all chunks of a given biome.

        public int difficulty = 1;
        //0 = Extremely easy: disaster will slowly affect chucks, grace period, The warning arrives 30 seconds before cataclysm, biome + cataclysm are revealed
        //1 = Easy:           disaster will slowly affect chucks, grace period,  The warning arrives 20 seconds before cataclysm, biome + cataclysm are revealed
        //2 = Hard:           disaster will normally affect chucks, grace period,  The warning arrives 10 seconds before cataclysm, biome + cataclysm are revealed
        //3 = Impossible:     disaster will "as fast as possible" affect chucks, The warning arrives 10 seconds before cataclysm, biome + cataclysm are hidden
        //4 = Hardcore:       disaster will "as fast as possible" affect chucks, No warning, biome + cataclysm are hidden

        public int tickDelayBetweenCataclysm = 5*60*20; // 6000 = 5 minutes default
        public int timer = -1;//When -1 -> timer is disabled
        public int state = 0; //0 = paused; 1 = paused (to confirm); 2 = playing;
        public int dataCondition = 0; //0 = data is fine; 1 = data is corrupted; -1 = data needs to be analyzed;

        public String nextBiomeToAffect = "None";
        public String cataclysm = "NULL";
        public int tickToNextCataclysm = tickDelayBetweenCataclysm;
        public int gracePeriod = 0;

        public static MapVariables load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
            MapVariables data = new MapVariables();
            data.read(tag, lookupProvider);
            return data;
        }

        public void read(CompoundTag nbt, HolderLookup.Provider lookupProvider) {

            readLongDequeMap(nbt, "initialOrder", initialOrder);
            readLongDequeMap(nbt, "dynamicOrder", dynamicOrder);

            readStateMap(nbt, "initialStates", initialStates);
            readStateMap(nbt, "dynamicStates", dynamicStates);

            chunks.clear();
            if (!nbt.contains("chunksRoot", Tag.TAG_COMPOUND)) return;
            CompoundTag chunksRoot = nbt.getCompound("chunksRoot");
            for (String dimId : chunksRoot.getAllKeys()) {
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimId));
                Map<Long, ChunkInstance> dimChunks = new HashMap<>();

                CompoundTag dimTag = chunksRoot.getCompound(dimId);
                ListTag chunksList = dimTag.getList("chunks", Tag.TAG_COMPOUND);

                for (int i = 0; i < chunksList.size(); i++) {
                    CompoundTag chunkTag = chunksList.getCompound(i);
                    long packedPos = chunkTag.getLong("packedPos");
                    ChunkInstance mod = ChunkInstance.load(chunkTag, lookupProvider);
                    dimChunks.put(packedPos, mod);
                }
                chunks.put(dim, dimChunks);
            }

            // --- Caricamento Flooded Heights ---
            floodedHeights.clear();
            if (nbt.contains("floodedHeights", Tag.TAG_COMPOUND)) {
                CompoundTag floodedTag = nbt.getCompound("floodedHeights");
                for (String biomeId : floodedTag.getAllKeys()) {
                    floodedHeights.put(biomeId, floodedTag.getInt(biomeId));
                }
            }

            // --- Caricamento Sun Burn Start Ticks ---
            sunBurnStartTicks.clear();
            if (nbt.contains("sunBurnStartTicks", Tag.TAG_COMPOUND)) {
                CompoundTag sunBurnTag = nbt.getCompound("sunBurnStartTicks");
                for (String biomeId : sunBurnTag.getAllKeys()) {
                    sunBurnStartTicks.put(biomeId, sunBurnTag.getLong(biomeId));
                }
            }

            // --- Caricamento Shuffled Biomes ---
            shuffledBiomes.clear();
            ListTag shuffledList = nbt.getList("shuffledBiomes", Tag.TAG_STRING);
            for (int i = 0; i < shuffledList.size(); i++) {
                shuffledBiomes.add(shuffledList.getString(i));
            }

            overworldBiomeList.clear();
            ListTag overworldBiomeListList = nbt.getList("overworldBiomeList", Tag.TAG_STRING);
            for (int i = 0; i < overworldBiomeListList.size(); i++) {
                overworldBiomeList.add(overworldBiomeListList.getString(i));
            }

            // --- Caricamento Deleted Biomes ---
            deletedBiomes.clear();
            ListTag deletedList = nbt.getList("deletedBiomes", Tag.TAG_STRING);
            for (int i = 0; i < deletedList.size(); i++) {
                deletedBiomes.add(deletedList.getString(i));
            }
            processedChunks.clear();
            ListTag processedList = nbt.getList("processedChunks", Tag.TAG_STRING);
            for (int i = 0; i < processedList.size(); i++) processedChunks.add(processedList.getString(i));

            totalBiomes = nbt.getInt("totalBiomes");
            graceCheckHappen = nbt.getBoolean("graceCheckHappen");
            debugMode = nbt.getBoolean("debugMode");
            allNetherBiomesHitShouldNotify = nbt.getBoolean("allNetherBiomesHitShouldNotify");
            allEndBiomesHitShouldNotify = nbt.getBoolean("allEndBiomesHitShouldNotify");
            allOverworldBiomesHitShouldNotify = nbt.getBoolean("allOverworldBiomesHitShouldNotify");
            biomesToAffect = nbt.getInt("biomesToAffect");
            biomesAffected = nbt.getInt("biomesAffected");
            pcPower = nbt.getInt("pcPower");
            radius = nbt.getInt("radius");
            destructionSpeed = nbt.getDouble("destructionSpeed");
            mode = nbt.getInt("mode");
            difficulty = nbt.getInt("difficulty");
            tickDelayBetweenCataclysm = nbt.getInt("tickDelayBetweenCataclysm");
            timer = nbt.getInt("timer");
            state = nbt.getInt("state");
            dataCondition = nbt.getInt("dataCondition");
            tickToNextCataclysm = nbt.getInt("tickToNextCataclysm");
            gracePeriod = nbt.getInt("gracePeriod");

            nextBiomeToAffect = nbt.getString("nextBiomeToAffect");
            cataclysm = nbt.getString("cataclysm");
        }

        @Override
        public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookupProvider) {

            Map<ResourceKey<Level>, Map<Long, ChunkInstance>> chunksCopy;
            Map<ResourceKey<Level>, ArrayDeque<Long>> initialOrderCopy;
            Map<ResourceKey<Level>, ArrayDeque<Long>> dynamicOrderCopy;
            Map<ResourceKey<Level>, ChunkProcessor.DimensionState> initialStatesCopy;
            Map<ResourceKey<Level>, ChunkProcessor.DimensionState> dynamicStatesCopy;
            Map<String, Integer> floodedHeightsCopy;
            Map<String, Long> sunBurnStartTicksCopy;

            List<String> shuffledBiomesCopy;
            List<String> overworldBiomeListCopy;
            Set<String> deletedBiomesCopy;
            Set<String> processedChunksCopy;

            // 🔒 SNAPSHOT SICURO (NO CONCURRENT MODIFICATION)
            synchronized (this) {

                chunksCopy = new HashMap<>();
                for (var e : chunks.entrySet()) {
                    chunksCopy.put(e.getKey(), new HashMap<>(e.getValue()));
                }

                initialOrderCopy = new HashMap<>();
                for (var e : initialOrder.entrySet()) {
                    initialOrderCopy.put(e.getKey(), new ArrayDeque<>(e.getValue()));
                }

                dynamicOrderCopy = new HashMap<>();
                for (var e : dynamicOrder.entrySet()) {
                    dynamicOrderCopy.put(e.getKey(), new ArrayDeque<>(e.getValue()));
                }

                initialStatesCopy = new HashMap<>(initialStates);
                dynamicStatesCopy = new HashMap<>(dynamicStates);
                floodedHeightsCopy = new HashMap<>(floodedHeights);
                sunBurnStartTicksCopy = new HashMap<>(sunBurnStartTicks);

                // 🔥 COPIA MANUALE SICURA (FIX DEFINITIVO)
                shuffledBiomesCopy = new ArrayList<>();
                for (String s : shuffledBiomes) {
                    shuffledBiomesCopy.add(s);
                }

                overworldBiomeListCopy = new ArrayList<>();
                for (String s : overworldBiomeList) {
                    overworldBiomeListCopy.add(s);
                }

                deletedBiomesCopy = new HashSet<>();
                for (String s : deletedBiomes) {
                    deletedBiomesCopy.add(s);
                }

                processedChunksCopy = new HashSet<>();
                for (String s : processedChunks) {
                    processedChunksCopy.add(s);
                }
            }

            // =========================
            // CHUNKS
            // =========================
            CompoundTag chunksRoot = new CompoundTag();

            for (var dimEntry : chunksCopy.entrySet()) {
                CompoundTag dimTag = new CompoundTag();
                ListTag chunksList = new ListTag();

                for (var chunkEntry : dimEntry.getValue().entrySet()) {
                    CompoundTag chunkTag = chunkEntry.getValue().save(lookupProvider);
                    chunkTag.putLong("packedPos", chunkEntry.getKey());
                    chunksList.add(chunkTag);
                }

                dimTag.put("chunks", chunksList);
                chunksRoot.put(dimEntry.getKey().location().toString(), dimTag);
            }
            nbt.put("chunksRoot", chunksRoot);


            CompoundTag floodedHeightsTag = new CompoundTag();
            for (var entry : floodedHeightsCopy.entrySet()) {
                floodedHeightsTag.putInt(entry.getKey(), entry.getValue());
            }
            nbt.put("floodedHeights", floodedHeightsTag);

            CompoundTag sunBurnStartTicksTag = new CompoundTag();
            for (var entry : sunBurnStartTicksCopy.entrySet()) {
                sunBurnStartTicksTag.putLong(entry.getKey(), entry.getValue());
            }
            nbt.put("sunBurnStartTicks", sunBurnStartTicksTag);


            // =========================
            // MAPPE ORDINI / STATES
            // =========================
            writeLongDequeMap(nbt, "initialOrder", initialOrderCopy);
            writeLongDequeMap(nbt, "dynamicOrder", dynamicOrderCopy);
            writeStateMap(nbt, "initialStates", initialStatesCopy);
            writeStateMap(nbt, "dynamicStates", dynamicStatesCopy);

            // =========================
            // LISTE
            // =========================
            ListTag shuffledTag = new ListTag();
            for (String b : shuffledBiomesCopy) {
                shuffledTag.add(StringTag.valueOf(b));
            }
            nbt.put("shuffledBiomes", shuffledTag);

            ListTag overworldBiomeListTag = new ListTag();
            for (String b : overworldBiomeListCopy) {
                overworldBiomeListTag.add(StringTag.valueOf(b));
            }
            nbt.put("overworldBiomeList", overworldBiomeListTag);

            ListTag deletedTag = new ListTag();
            for (String b : deletedBiomesCopy) {
                deletedTag.add(StringTag.valueOf(b));
            }
            nbt.put("deletedBiomes", deletedTag);

            ListTag processedTag = new ListTag();
            for (String s : processedChunksCopy) {
                processedTag.add(StringTag.valueOf(s));
            }
            nbt.put("processedChunks", processedTag);

            // =========================
            // PRIMITIVI
            // =========================
            nbt.putInt("totalBiomes", totalBiomes);
            nbt.putBoolean("graceCheckHappen", graceCheckHappen);
            nbt.putBoolean("debugMode", debugMode);
            nbt.putBoolean("allNetherBiomesHitShouldNotify", allNetherBiomesHitShouldNotify);
            nbt.putBoolean("allEndBiomesHitShouldNotify", allEndBiomesHitShouldNotify);
            nbt.putBoolean("allOverworldBiomesHitShouldNotify", allOverworldBiomesHitShouldNotify);

            nbt.putInt("biomesToAffect", biomesToAffect);
            nbt.putInt("biomesAffected", biomesAffected);
            nbt.putInt("pcPower", pcPower);
            nbt.putInt("radius", radius);
            nbt.putDouble("destructionSpeed", destructionSpeed);

            nbt.putInt("mode", mode);
            nbt.putInt("difficulty", difficulty);

            nbt.putInt("tickDelayBetweenCataclysm", tickDelayBetweenCataclysm);
            nbt.putInt("timer", timer);
            nbt.putInt("state", state);
            nbt.putInt("dataCondition", dataCondition);

            nbt.putInt("tickToNextCataclysm", tickToNextCataclysm);
            nbt.putInt("gracePeriod", gracePeriod);

            nbt.putString("nextBiomeToAffect", nextBiomeToAffect);
            nbt.putString("cataclysm", cataclysm);

            return nbt;
        }

        public void generateFullBiomeList(ServerLevel level) {
            Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
            this.shuffledBiomes = registry.keySet().stream()
                    .map(ResourceLocation::toString)
                    .filter(id -> !id.contains(Biomes.THE_VOID.location().toString())) // Evitiamo biomi nulli
                    .collect(Collectors.toList());

            Collections.shuffle(this.shuffledBiomes);
            this.totalBiomes = shuffledBiomes.size();
            this.biomesToAffect = totalBiomes;
            if (!shuffledBiomes.isEmpty()){
                this.nextBiomeToAffect = this.shuffledBiomes.get(0);
                buildOverworldBiomesList();
            }
            this.syncData(level, true, false);
        }
        private void buildOverworldBiomesList() {
            overworldBiomeList.clear();
            overworldBiomeList.addAll(shuffledBiomes);
            overworldBiomeList.removeIf(id ->
                    id.equals(Biomes.THE_VOID.location().toString())

                            || id.startsWith(Biomes.NETHER_WASTES.location().toString())
                            || id.startsWith(Biomes.SOUL_SAND_VALLEY.location().toString())
                            || id.startsWith(Biomes.BASALT_DELTAS.location().toString())
                            || id.startsWith(Biomes.CRIMSON_FOREST.location().toString())
                            || id.startsWith(Biomes.WARPED_FOREST.location().toString())

                            || id.startsWith(Biomes.END_HIGHLANDS.location().toString())
                            || id.startsWith(Biomes.THE_END.location().toString())
                            || id.startsWith(Biomes.END_MIDLANDS.location().toString())
                            || id.startsWith(Biomes.END_BARRENS.location().toString())
                            || id.startsWith(Biomes.SMALL_END_ISLANDS.location().toString())
            );
        }

        public void selectNextBiomeGlobal(MinecraftServer server) {
            if (this.shuffledBiomes.isEmpty()) return;

            String targetBiomeId = this.shuffledBiomes.remove(0);
            this.deletedBiomes.add(targetBiomeId);
            this.biomesAffected++;
            this.biomesAffected--;

            if (!this.shuffledBiomes.isEmpty()) {
                this.nextBiomeToAffect = this.shuffledBiomes.get(0);
            }

            // TRIGGER IMMEDIATO: Inizia subito a scansionare per evitare il lag di 30s
            if (difficulty < 4 && mode != 1){ //If chunk destruction DOESNT start on player pos
                for (ServerLevel level : server.getAllLevels()) {
                    scanAndQueueChunks(level, targetBiomeId, true); // true = priorità alta (onda)
                }
            }

            this.setDirty();
        }
        //Only used in debug mode
        public void forceNextBiome(String targetBiomeId, ServerLevel level) {
            int index = this.shuffledBiomes.indexOf(targetBiomeId);

            // LOG DI DEBUG: Controlliamo cosa sta succedendo in console
            System.out.println("Cerco di forzare: " + targetBiomeId + ". Trovato all'indice: " + index);

            if (index == -1) {
                // Il bioma non è nella lista (forse è già stato eliminato o il nome è sbagliato)
                if (this.deletedBiomes.contains(targetBiomeId)) {
                    BiomesOfCataclysms.LOGGER.warn("Impossibile forzare: " + targetBiomeId + " è già stato eliminato!");
                } else {
                    BiomesOfCataclysms.LOGGER.error("Errore: " + targetBiomeId + " non esiste nella lista dei biomi!");
                }
                return;
            }

            if (index > 0) {
                // Eseguiamo lo scambio solo se non è già al primo posto
                String currentFirst = this.shuffledBiomes.getFirst();
                this.shuffledBiomes.set(0, targetBiomeId);
                this.shuffledBiomes.set(index, currentFirst);
                BiomesOfCataclysms.LOGGER.info("Swap eseguito: " + targetBiomeId + " ora è il prossimo.");
            }

            // Aggiorniamo SEMPRE il puntatore del display e sincronizziamo
            this.nextBiomeToAffect = targetBiomeId;
            this.syncData(level, true, false);
        }

        //Scans around to check if there are any missed/corrupted chunk. This method generates an area around the player based on the radius
        public void scanContinuous(ServerLevel level) {
            if (this.deletedBiomes.isEmpty()) return;
            List<ServerPlayer> players = level.players().stream()
                    .filter(p -> !p.isSpectator())
                    .toList();
            if (players.isEmpty()) return;

            int radius = this.radius;
            String dimKey = level.dimension().location().toString();

            for (ServerPlayer player : players) {
                ChunkPos playerChunk = new ChunkPos(player.blockPosition());

                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        ChunkPos targetPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);

                        if (!level.hasChunk(targetPos.x, targetPos.z)) continue;

                        String chunkKey = targetPos.x + "," + targetPos.z + "," + dimKey;
                        ChunkInstance mod = ChunkProcessor.getOrCreateChunkMod(level, targetPos);

                        // Se il chunk è già in lavorazione o già in una coda, non fare nulla
                        if (mod.initialWave || mod.dynamic || mod.state == ChunkState.PROCESSING) {
                            continue;
                        }

                        boolean needsDynamic = false;

                        for (String biomeId : mod.biomeIds) {
                            if (!this.deletedBiomes.contains(biomeId)) continue;

                            String processedKey = chunkKey + "|" + biomeId;
                            if (this.processedChunks.contains(processedKey)) continue;

                            this.processedChunks.add(processedKey);
                            needsDynamic = true;
                        }

                        if (!needsDynamic) continue;

                        // -------------------------

                        this.setDirty();
                        ChunkProcessor.registerDynamicChunk(level, targetPos);
                    }
                }

            }
        }
        private void scanAndQueueChunks(ServerLevel level, String targetBiomeId, boolean priority) {
            List<ServerPlayer> players = level.players().stream()
                    .filter(p -> !p.isSpectator())
                    .toList();
            if (players.isEmpty()) return;

            int radius = this.radius;
            List<ChunkPos> foundChunks = new ArrayList<>();
            Set<Long> foundKeys = new HashSet<>();

            for (ServerPlayer player : players) {
                ChunkPos playerChunk = new ChunkPos(player.blockPosition());

                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        ChunkPos targetPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);

                        if (!level.hasChunk(targetPos.x, targetPos.z)) continue;

                        long packed = targetPos.toLong();
                        if (!foundKeys.add(packed)) continue;

                        ChunkInstance mod = ChunkProcessor.getOrCreateChunkMod(level, targetPos);

                        if (mod.biomeIds.contains(targetBiomeId)) {
                            foundChunks.add(targetPos);
                        }
                    }
                }
            }

            if (this.difficulty == 0 || this.difficulty == 1) {
                surroundingChunkDestruction(foundChunks, players, level);
            } else if (this.difficulty == 2) {
                randomChunkDestruction(foundChunks, level);
            } else if (this.difficulty == 3 || this.difficulty == 4) {
                instantDestruction(foundChunks, players, level);
            }

            for (ChunkPos pos : foundChunks) {
                if (priority) {
                    ChunkProcessor.registerDynamicChunk(level, pos);
                } else {
                    ChunkProcessor.registerInitialChunk(level, pos);
                }
            }
        }

        //The 3 different kind of destruction:
        // 1. Instant -> starts from the nearest chunk to the player
        // 2. Random -> picks random chunks
        // 3. Surrounding -> starts from the farthest chunk.
        //These method are called exclusively for the chunks queue in the INITIAL QUEUE, not the dynamic
        private void instantDestruction(List<ChunkPos> foundChunks, List<ServerPlayer> players, ServerLevel level){
            // --- EFFETTO ONDA CORRETTO (Dal più vicino al più lontano) ---
            foundChunks.sort(Comparator.comparingDouble(pos -> {
                double minDistance = Double.MAX_VALUE;
                for (ServerPlayer p : players) {
                    double dist = pos.getMiddleBlockPosition(64).distSqr(p.blockPosition());
                    if (dist < minDistance) minDistance = dist;
                }
                return minDistance;
            }));
            for (ChunkPos pos : foundChunks) {
                // IMPORTANTE: addLast per preservare l'ordine dell'onda iniziale
                ChunkProcessor.registerDynamicChunk(level, pos);
            }
        }
        private void randomChunkDestruction(List<ChunkPos> foundChunks, ServerLevel level){
            // --- EFFETTO ONDA CORRETTO (Dal più vicino al più lontano) ---
            Collections.shuffle(foundChunks);

            for (ChunkPos pos : foundChunks) {
                // IMPORTANTE: addLast per preservare l'ordine dell'onda iniziale
                ChunkProcessor.registerInitialChunk(level, pos);
            }
        }
        private void surroundingChunkDestruction(List<ChunkPos> foundChunks, List<ServerPlayer> players, ServerLevel level) {
            if (foundChunks.isEmpty() || players.isEmpty()) return;

            // 1. Ordiniamo la lista dal PIÙ LONTANO al PIÙ VICINO
            foundChunks.sort(Comparator.comparingDouble((ChunkPos pos) -> {
                double minDistanceSq = Double.MAX_VALUE;

                // Calcoliamo il centro del chunk manualmente (X_chunk * 16 + 8)
                int centerX = (pos.x << 4) + 8;
                int centerZ = (pos.z << 4) + 8;

                for (ServerPlayer p : players) {
                    // Calcoliamo la distanza tra il player e il centro del chunk
                    double distSq = p.distanceToSqr(centerX, p.getY(), centerZ);
                    if (distSq < minDistanceSq) {
                        minDistanceSq = distSq;
                    }
                }
                return minDistanceSq;
            }).reversed()); // <--- REVERSED è fondamentale per la coda Priority!

            for (ChunkPos pos : foundChunks) {
                // IMPORTANTE: addLast per preservare l'ordine dell'onda iniziale
                ChunkProcessor.registerInitialChunk(level, pos);
            }
        }

        public int getFloodHeight(String biomeId, ServerLevel level) {
            return floodedHeights.getOrDefault(biomeId, level.getMinBuildHeight());
        }
        public void startFloodForBiome(String biomeId, ServerLevel level) {
            floodedHeights.putIfAbsent(biomeId, level.getMinBuildHeight());
            this.setDirty();
        }
        public boolean tickFloodHeights(ServerLevel level) {
            if (level.getGameTime() % 100 != 0) return false;

            boolean changed = false;
            int sky = level.getMaxBuildHeight();
            for (var entry : floodedHeights.entrySet()) {
                if (entry.getValue() < sky) {
                    entry.setValue(entry.getValue() + 2);
                    changed = true;
                }
            }


            if (changed) {
                resetFloodWaves(level);
                this.setDirty();
            }
            return changed;
        }

        public void startSunBurn(String biomeId, ServerLevel level) {
            sunBurnStartTicks.putIfAbsent(biomeId, level.getGameTime());
            setDirty();
        }
        public long getSunBurnElapsedTicks(String biomeId, ServerLevel level) {
            return level.getGameTime() - sunBurnStartTicks.getOrDefault(biomeId, level.getGameTime());
        }
        //------------------------------------------


        /**
         * @param world -> Il server
         * @param saveOnDisk -> Salva i dati sul disco (sul server)
         * @param sendClientPacket -> Invia un packetto dati al client.
         * Utilizza i metodi sendClientPacket(LevelAccessor) oppure saveDataOnDisk() se è solo necessario inviare un pacchetto o salvare su disco.
         *                         Dove possibile è meglio utilizzare syncData(LevelAccessor, boolean, boolean): è più dinamico;
         *                         se per esempio non si riesce ad accedere a un LevelAccessor allora si utilizza il metodo saveDataOnDisk();
         */
        public void syncData(LevelAccessor world, boolean saveOnDisk, boolean sendClientPacket) {
            if (saveOnDisk) this.setDirty();
            if (!sendClientPacket) return;
            if (world instanceof Level level && !world.isClientSide()) {
                MapVariables snapshot = this.copyForSync(level.registryAccess());
                PacketDistributor.sendToAllPlayers(new SavedDataSyncMessage(0, snapshot));
            }
        }
        public void sendClientPacket(LevelAccessor world) {
            if (world instanceof Level level && !world.isClientSide()) {
                MapVariables snapshot = this.copyForSync(level.registryAccess());
                PacketDistributor.sendToAllPlayers(new SavedDataSyncMessage(0, snapshot));
            }
        }
        public void saveDataOnDisk() {
            this.setDirty();
        }

        public MapVariables copyForSync(HolderLookup.Provider provider) {
            CompoundTag tag = this.save(new CompoundTag(), provider);
            return MapVariables.load(tag, provider);
        }

        public static MapVariables clientSide = new MapVariables();

        public static MapVariables get(LevelAccessor world) {
            if (world instanceof ServerLevelAccessor serverLevelAcc) {
                return serverLevelAcc.getLevel().getServer().getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(new SavedData.Factory<>(MapVariables::new, MapVariables::load), DATA_NAME);
            } else {
                return clientSide;
            }
        }
    }

    public record SavedDataSyncMessage(int dataType, SavedData data) implements CustomPacketPayload {
        public static final Type<SavedDataSyncMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BiomesOfCataclysms.MODID, "saved_data_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SavedDataSyncMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, SavedDataSyncMessage message) -> {
            buffer.writeInt(message.dataType);
            if (message.data != null)
                buffer.writeNbt(message.data.save(new CompoundTag(), buffer.registryAccess()));
        }, (RegistryFriendlyByteBuf buffer) -> {
            int dataType = buffer.readInt();
            CompoundTag nbt = buffer.readNbt();
            SavedData data = null;
            if (nbt != null) {
                if (dataType == 0)
                    data = new MapVariables();
                if (data instanceof MapVariables mapVariables)
                    mapVariables.read(nbt, buffer.registryAccess());
            }
            return new SavedDataSyncMessage(dataType, data);
        });

        @Override
        public Type<SavedDataSyncMessage> type() {
            return TYPE;
        }

        public static void handleData(final SavedDataSyncMessage message, final IPayloadContext context) {
            if (context.flow() == PacketFlow.CLIENTBOUND && message.data != null) {
                context.enqueueWork(() -> {
                    if (message.dataType == 0)
                        MapVariables.clientSide.read(message.data.save(new CompoundTag(), context.player().registryAccess()), context.player().registryAccess());
                    }).exceptionally(e -> {
                    context.connection().disconnect(Component.literal(e.getMessage()));
                    return null;
                });
            }
        }
    }
}
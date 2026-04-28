package net.calca.biomesofcataclysms.data;


import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.calca.biomesofcataclysms.data.chunk.ChunkMod;
import net.calca.biomesofcataclysms.data.chunk.ChunkState;
import net.calca.biomesofcataclysms.data.chunk.DeletionQueueManager;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
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

import static net.calca.biomesofcataclysms.data.chunk.DeletionQueueManager.DataSavingHelper.*;


@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class ModVariables {
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
                SavedData worlddata = WorldVariables.get(event.getEntity().level());
                if (mapdata != null)
                    PacketDistributor.sendToPlayer(player, new SavedDataSyncMessage(0, mapdata));
                if (worlddata != null)
                    PacketDistributor.sendToPlayer(player, new SavedDataSyncMessage(1, worlddata));
            }
        }

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                SavedData worlddata = WorldVariables.get(event.getEntity().level());
                if (worlddata != null)
                    PacketDistributor.sendToPlayer(player, new SavedDataSyncMessage(1, worlddata));
            }
        }
    }

    public static class WorldVariables extends SavedData {
        public static final String DATA_NAME = "biomesofcataclysm_worldvars";

        public static WorldVariables load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
            WorldVariables data = new WorldVariables();
            data.read(tag, lookupProvider);
            return data;
        }

        public void read(CompoundTag nbt, HolderLookup.Provider lookupProvider) {
        }

        @Override
        public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookupProvider) {
            return nbt;
        }

        public void syncData(LevelAccessor world) {
            this.setDirty();
            if (world instanceof ServerLevel level)
                PacketDistributor.sendToPlayersInDimension(level, new SavedDataSyncMessage(1, this));
        }

        static WorldVariables clientSide = new WorldVariables();

        public static WorldVariables get(LevelAccessor world) {
            if (world instanceof ServerLevel level) {
                return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorldVariables::new, WorldVariables::load), DATA_NAME);
            } else {
                return clientSide;
            }
        }
    }

    //---------------------------------------------------

    public static class MapVariables extends SavedData {
        public static final String DATA_NAME = "biomesofcataclysms_mapvars";
        // |||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||
        // runtime cache: la lasci pure fuori dal salvataggio
        public final Map<ResourceKey<Level>, Map<Long, ChunkMod>> chunks = new HashMap<>();
        public final Map<ResourceKey<Level>, ArrayDeque<Long>> initialOrder = new HashMap<>();
        public final Map<ResourceKey<Level>, ArrayDeque<Long>> dynamicOrder = new HashMap<>();

        public final Map<ResourceKey<Level>, DeletionQueueManager.DimensionState> initialStates = new HashMap<>();
        public final Map<ResourceKey<Level>, DeletionQueueManager.DimensionState> dynamicStates = new HashMap<>();


        public int totalBiomes = 0;
        public List<String> shuffledBiomes = new ArrayList<>(); // Coda dei prossimi biomi
        public Set<String> deletedBiomes = new HashSet<>();    // Registro dei biomi già cancellati
        public Set<String> processedChunks = new HashSet<>();

        public int biomesToAffect = totalBiomes;
        public int biomesAffected = 0;

        public int mode = 0; //0 = Biom Remover: chunks of a given biome will get completely deleted;
                            // 1 = Apocalypse: random disasters will affect all chunks of a given biome.

        public int difficulty = 1;
        //0 = Extremely easy: disaster will slowly affect chucks, grace period, The warning arrives 30 seconds before cataclysm, biome + cataclysm are revealed
        //1 = Easy:           disaster will slowly affect chucks, grace period,  The warning arrives 20 seconds before cataclysm, biome + cataclysm are revealed
        //2 = Hard:           disaster will normally affect chucks, grace period,  The warning arrives 10 seconds before cataclysm, biome + cataclysm are revealed
        //3 = Impossible:     disaster will "as fast as possible" affect chucks, The warning arrives 10 seconds before cataclysm, biome + cataclysm are hidden
        //4 = Hardcore:       disaster will "as fast as possible" affect chucks, No warning, biome + cataclysm are hidden

        public int tickDelayBetweenCataclysm = 600; // 6000 = 5 minutes default
        public int timer = -1;//When -1 timer is disabled
        public int state = 0; //0 = paused; 1 = paused (to confirm); 2 = playing;
        public int dataCondition = 0; //0 = data is fine; 1 = data is corrupted; -1 = data needs to be analyzed;

        public String nextBiomeToAffect = "None";
        public String cataclysm = "Destroyed";
        public int tickToNextCataclysm = tickDelayBetweenCataclysm;
        public int gracePeriod = 0;

        public static final String[] possibleCataclysm = {
                Component.translatable("possibleCataclysm.biomesofctataclysms.flooded").toString(),
                Component.translatable("possibleCataclysm.biomesofctataclysms.sun_burnt").toString()
        };

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
                Map<Long, ChunkMod> dimChunks = new HashMap<>();

                CompoundTag dimTag = chunksRoot.getCompound(dimId);
                ListTag chunksList = dimTag.getList("chunks", Tag.TAG_COMPOUND);

                for (int i = 0; i < chunksList.size(); i++) {
                    CompoundTag chunkTag = chunksList.getCompound(i);
                    long packedPos = chunkTag.getLong("packedPos");
                    ChunkMod mod = ChunkMod.load(chunkTag, lookupProvider);
                    dimChunks.put(packedPos, mod);
                }
                chunks.put(dim, dimChunks);
            }

            // --- Caricamento Shuffled Biomes ---
            shuffledBiomes.clear();
            ListTag shuffledList = nbt.getList("shuffledBiomes", Tag.TAG_STRING);
            for (int i = 0; i < shuffledList.size(); i++) {
                shuffledBiomes.add(shuffledList.getString(i));
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
            biomesToAffect = nbt.getInt("biomesToAffect");
            biomesAffected = nbt.getInt("biomesAffected");
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
            Map<ResourceKey<Level>, Map<Long, ChunkMod>> chunksCopy = new HashMap<>();
            for (var e : chunks.entrySet()) {
                chunksCopy.put(e.getKey(), new HashMap<>(e.getValue()));
            }
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

            writeLongDequeMap(nbt, "initialOrder", initialOrder);
            writeLongDequeMap(nbt, "dynamicOrder", dynamicOrder);
            writeStateMap(nbt, "initialStates", initialStates);
            writeStateMap(nbt, "dynamicStates", dynamicStates);

            // --- Salvataggio Shuffled Biomes ---
            ListTag shuffledTag = new ListTag();
            for (String b : shuffledBiomes) shuffledTag.add(StringTag.valueOf(b));
            nbt.put("shuffledBiomes", shuffledTag);

            // --- Salvataggio Deleted Biomes ---
            ListTag deletedTag = new ListTag();
            for (String b : deletedBiomes) deletedTag.add(StringTag.valueOf(b));
            nbt.put("deletedBiomes", deletedTag);

            ListTag processedTag = new ListTag();
            for (String s : processedChunks) processedTag.add(StringTag.valueOf(s));
            nbt.put("processedChunks", processedTag);

            nbt.putInt("totalBiomes", totalBiomes);
            nbt.putInt("biomesToAffect", biomesToAffect);
            nbt.putInt("biomesAffected", biomesAffected);
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
                    .filter(id -> !id.contains("minecraft:empty")) // Evitiamo biomi nulli
                    .collect(Collectors.toList());

            Collections.shuffle(this.shuffledBiomes);
            this.totalBiomes = shuffledBiomes.size();
            if (!shuffledBiomes.isEmpty()) this.nextBiomeToAffect = this.shuffledBiomes.get(0);
            this.syncData(level);
        }
        public void selectNextBiomeGlobal(MinecraftServer server) {
            if (this.shuffledBiomes.isEmpty()) return;

            String targetBiomeId = this.shuffledBiomes.remove(0);
            this.deletedBiomes.add(targetBiomeId);
            this.biomesAffected++;

            if (!this.shuffledBiomes.isEmpty()) {
                this.nextBiomeToAffect = this.shuffledBiomes.get(0);
            }

            // TRIGGER IMMEDIATO: Inizia subito a scansionare per evitare il lag di 30s
            if (difficulty < 4){ //If chunk destruction DOESNT start on player pos
                for (ServerLevel level : server.getAllLevels()) {
                    scanAndQueueChunks(level, targetBiomeId, true); // true = priorità alta (onda)
                }
            }

            this.setDirty();
        }

        // Aggiorna anche scanContinuous per usare la logica a onda più leggera
        // Sostituisci questo metodo in MapVariables
        public void scanContinuous(ServerLevel level) {
            if (this.deletedBiomes.isEmpty()) return;

            int radius = 16;
            String dimKey = level.dimension().location().toString();

            for (ServerPlayer player : level.players()) {
                ChunkPos playerChunk = new ChunkPos(player.blockPosition());

                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        ChunkPos targetPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);

                        if (!level.hasChunk(targetPos.x, targetPos.z)) continue;

                        String chunkKey = targetPos.x + "," + targetPos.z + "," + dimKey;
                        ChunkMod mod = DeletionQueueManager.getOrCreateChunkMod(level, targetPos);

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

                        this.setDirty();
                        DeletionQueueManager.registerDynamicChunk(level, targetPos);
                    }
                }
            }
        }
        private void scanAndQueueChunks(ServerLevel level, String targetBiomeId, boolean priority) {
            int radius = 16;
            List<ChunkPos> foundChunks = new ArrayList<>();
            List<ServerPlayer> players = level.players();

            if (players.isEmpty()) return;

            for (ServerPlayer player : players) {
                ChunkPos playerChunk = new ChunkPos(player.blockPosition());
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        ChunkPos targetPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);

                        // Controlliamo se il chunk esiste e non è già vuoto
                        if (level.hasChunk(targetPos.x, targetPos.z)) {
                            Holder<Biome> biomeAtPos = level.getBiome(targetPos.getMiddleBlockPosition(64));
                            if (biomeAtPos.unwrapKey().isPresent() &&
                                    biomeAtPos.unwrapKey().get().location().toString().equals(targetBiomeId)) {

                                // Aggiungiamo solo se non è già stato processato
                                if (!foundChunks.contains(targetPos)) {
                                    foundChunks.add(targetPos);
                                }
                            }
                        }
                    }
                }
            }

            if (this.difficulty == 0 || this.difficulty == 1){
                surroundingChunkDestruction(foundChunks, players, level);
            } else if (this.difficulty == 2) {
                randomChunkDestruction(foundChunks, level);
            } else if (this.difficulty == 3 || this .difficulty == 4) {
                instantDestruction(foundChunks, players, level);
            }

            // Inseriamo nella coda principale
            for (ChunkPos pos : foundChunks) {
                if (priority) {
                    DeletionQueueManager.registerDynamicChunk(level, pos);
                } else {
                    DeletionQueueManager.registerInitialChunk(level, pos);
                }
            }
        }

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
                DeletionQueueManager.registerDynamicChunk(level, pos);
            }
        }
        private void randomChunkDestruction(List<ChunkPos> foundChunks, ServerLevel level){
            // --- EFFETTO ONDA CORRETTO (Dal più vicino al più lontano) ---
            Collections.shuffle(foundChunks);

            for (ChunkPos pos : foundChunks) {
                // IMPORTANTE: addLast per preservare l'ordine dell'onda iniziale
                DeletionQueueManager.registerInitialChunk(level, pos);
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
                DeletionQueueManager.registerInitialChunk(level, pos);
            }
        }

        public void syncDataGlobal() {
            this.setDirty();
            // Invia il pacchetto a chiunque sia connesso, ovunque si trovi
            PacketDistributor.sendToAllPlayers(new SavedDataSyncMessage(0, this));
        }

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
            this.syncData(level);
        }

        //------------------------------------------


        public void syncData(LevelAccessor world) {
            this.setDirty();
            if (world instanceof Level && !world.isClientSide())
                PacketDistributor.sendToAllPlayers(new SavedDataSyncMessage(0, this));
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
                data = dataType == 0 ? new MapVariables() : new WorldVariables();
                if (data instanceof MapVariables mapVariables)
                    mapVariables.read(nbt, buffer.registryAccess());
                else if (data instanceof WorldVariables worldVariables)
                    worldVariables.read(nbt, buffer.registryAccess());
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
                    else
                        WorldVariables.clientSide.read(message.data.save(new CompoundTag(), context.player().registryAccess()), context.player().registryAccess());
                }).exceptionally(e -> {
                    context.connection().disconnect(Component.literal(e.getMessage()));
                    return null;
                });
            }
        }
    }
}

// Caused by: java.lang.RuntimeException: Failed encoding custom payload biomesofcataclysms:saved_data_sync: java.util.ConcurrentModificationException
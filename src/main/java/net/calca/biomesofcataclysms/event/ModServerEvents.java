package net.calca.biomesofcataclysms.event;

import net.calca.biomesofcataclysms.BiomesOfCataclysms;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.chunk.ChunkMod;
import net.calca.biomesofcataclysms.data.chunk.DeletionQueueManager;
import net.calca.biomesofcataclysms.data.chunk.DeletionQueueManager.RuntimeBuffers;
import net.calca.biomesofcataclysms.data.ModVariables;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// Done with the help of https://github.com/CoFH/CoFHCore/blob/1.19.x/src/main/java/cofh/core/event/AreaEffectEvents.java
// Don't be a jerk License
@EventBusSubscriber(modid = BiomesOfCataclysms.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ModServerEvents {
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        for (ServerLevel server : event.getServer().getAllLevels()){
            loadRuntimeFromSaved(server);
        }
    }

    public static void loadRuntimeFromSaved(ServerLevel server) {
        ModVariables.MapVariables saved = ModVariables.MapVariables.get(server);
        RuntimeBuffers.INITIAL_ORDER.clear();
        RuntimeBuffers.DYNAMIC_ORDER.clear();
        RuntimeBuffers.INITIAL_STATES.clear();
        RuntimeBuffers.DYNAMIC_STATES.clear();
        RuntimeBuffers.CHUNKS.clear();

        for (var e : saved.initialOrder.entrySet()) RuntimeBuffers.INITIAL_ORDER.put(e.getKey(), new ArrayDeque<>(e.getValue()));
        for (var e : saved.dynamicOrder.entrySet()) RuntimeBuffers.DYNAMIC_ORDER.put(e.getKey(), new ArrayDeque<>(e.getValue()));
        for (var e : saved.initialStates.entrySet()) RuntimeBuffers.INITIAL_STATES.put(e.getKey(), copyState(e.getValue()));
        for (var e : saved.dynamicStates.entrySet()) RuntimeBuffers.DYNAMIC_STATES.put(e.getKey(), copyState(e.getValue()));
        for (var dimEntry : saved.chunks.entrySet()) {
            Map<Long, ChunkMod> dimCopy = new HashMap<>();
            for (var chunkEntry : dimEntry.getValue().entrySet()) {
                dimCopy.put(chunkEntry.getKey(), chunkEntry.getValue());
            }
            RuntimeBuffers.CHUNKS.put(dimEntry.getKey(), dimCopy);
        }
        saved.syncData(server);
    }


    @SubscribeEvent
    public static void onServerStop(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();

        for (ServerLevel serverLevel : server.getAllLevels()) {
            flushRuntimeToSaved(serverLevel);
            //-------------------
            ModVariables.MapVariables variables = ModVariables.MapVariables.get(serverLevel);
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack()
                            .withSuppressedOutput()
                            .withPermission(4),   // livello OP massimo
                    "biomesOfCataclysms pause"
            );
            variables.syncData(serverLevel);

            ModUtils.sendChatMessage(serverLevel, Component.literal("Server Shutting Down: Game paused"));
        }

    }

    public static void flushRuntimeToSaved(ServerLevel server) {
        ModVariables.MapVariables saved = ModVariables.MapVariables.get(server);
        saved.initialOrder.clear();
        saved.dynamicOrder.clear();
        saved.initialStates.clear();
        saved.dynamicStates.clear();
        saved.chunks.clear();

        for (var e : RuntimeBuffers.INITIAL_ORDER.entrySet()) saved.initialOrder.put(e.getKey(), new ArrayDeque<>(e.getValue()));
        for (var e : RuntimeBuffers.DYNAMIC_ORDER.entrySet()) saved.dynamicOrder.put(e.getKey(), new ArrayDeque<>(e.getValue()));
        for (var e : RuntimeBuffers.INITIAL_STATES.entrySet()) saved.initialStates.put(e.getKey(), copyState(e.getValue()));
        for (var e : RuntimeBuffers.DYNAMIC_STATES.entrySet()) saved.dynamicStates.put(e.getKey(), copyState(e.getValue()));
        for (var dimEntry : RuntimeBuffers.CHUNKS.entrySet()) {
            Map<Long, ChunkMod> dimCopy = new HashMap<>();
            for (var chunkEntry : dimEntry.getValue().entrySet()) {
                dimCopy.put(chunkEntry.getKey(), chunkEntry.getValue());
            }
            saved.chunks.put(dimEntry.getKey(), dimCopy);
        }
        saved.syncData(server);
    }
    private static DeletionQueueManager.DimensionState copyState(DeletionQueueManager.DimensionState src) {
        DeletionQueueManager.DimensionState dst = new DeletionQueueManager.DimensionState();
        dst.currentKey = src.currentKey;
        dst.step = src.step;
        return dst;
    }


    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerLevel serverLevel = (ServerLevel) event.getEntity().level();
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(serverLevel);
        Player player = event.getEntity();

        if (variables.dataCondition == -1) {
            MutableComponent errorMsg = ModUtils.buildErrorMessage(
                    true,
                    3,
                    Component.literal("onPlayerLogin"),
                    Component.translatable("error.biomesofcataclysms.error3"));
            ModUtils.sendLocalChatMessageTo(player, errorMsg);
            ModUtils.playLocalErrorSound(player);
            return;
        } else if (variables.dataCondition == 1) {
            MutableComponent errorMsg = ModUtils.buildErrorMessage(
                    true,
                    4,
                    Component.literal("onPlayerLogin"),
                    Component.translatable("error.biomesofcataclysms.error4"));
            ModUtils.sendLocalChatMessageTo(player, errorMsg);
            ModUtils.playLocalErrorSound(player);
            return;
        }

        if (variables.state == 0 || variables.state == 1) {
            if (variables.tickToNextCataclysm == variables.tickDelayBetweenCataclysm && variables.biomesAffected == 0) {
                ModUtils.sendLocalChatMessageTo(player, Component.translatable("event.biomesofcataclysms.playerJoining.firstTime").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                if (player.hasPermissions(2)) {
                    ModUtils.sendLocalChatMessageTo(player, Component.translatable("event.biomesofcataclysms.playerJoining.firstTime_op").withStyle(ChatFormatting.GRAY));
                    sendCommandList(player);
                } else {
                    ModUtils.sendLocalChatMessageTo(player, Component.translatable("event.biomesofcataclysms.playerJoining.firstTime_nonOp").withStyle(ChatFormatting.GRAY));
                }
            } else { //A game instance is already running
                ModUtils.sendLocalChatMessageTo(player, Component.translatable("event.biomesofcataclysms.playerJoining.gameStartedBefore").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
                if (player.hasPermissions(2)) {
                    ModUtils.sendLocalChatMessageTo(player, Component.translatable("event.biomesofcataclysms.playerJoining.gameStartedBefore_op").withStyle(ChatFormatting.GRAY));
                    resumeCommand(player);
                } else {
                    ModUtils.sendLocalChatMessageTo(player, Component.translatable("event.biomesofcataclysms.playerJoining.gameStartedBefore_nonOp").withStyle(ChatFormatting.GRAY));
                }
            }
        } else {

            MutableComponent errorMsg = ModUtils.buildErrorMessage(
                    true,
                    2,
                    Component.literal("onPlayerLogin"),
                    Component.translatable("error.biomesofcataclysms.error2"));
            ModUtils.sendLocalChatMessageTo(player, errorMsg);
            ModUtils.playLocalErrorSound(player);

            MinecraftServer server = player.getServer();
            if (server != null) {
                server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack()
                                .withSuppressedOutput()
                                .withPermission(4),   // livello OP massimo
                        "biomesOfCataclysms pause"
                );
            }
            variables.dataCondition = -1;
            variables.syncData(serverLevel);
        }

    }

    private static void sendCommandList(Player player) {
        for (int i = 0; i < 4; i++) {
            String translationKey;
            String commandString;

            switch (i) {
                case 0 -> {
                    translationKey = "event.biomesofcataclysms.setDifficulty";
                    commandString = "/biomesOfCataclysms setDifficulty";
                }
                case 1 -> {
                    translationKey = "event.biomesofcataclysms.setMode";
                    commandString = "/biomesOfCataclysms setMode";
                }
                case 2 -> {
                    translationKey = "event.biomesofcataclysms.timeSettings";
                    commandString = "/biomesOfCataclysms timeSettings";
                }
                case 3 -> {
                    translationKey = "event.biomesofcataclysms.optimization";
                    commandString = "/biomesOfCataclysms optimization";
                }
                default -> {
                    continue;
                }
            }

            MutableComponent warning = Component.literal(" [!] ")
                    .withStyle(ChatFormatting.RED);

            MutableComponent msg = Component.translatable(translationKey)
                    .withStyle(style -> style
                            .withUnderlined(false)
                            .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.SUGGEST_COMMAND,
                                    commandString
                            ))
                            .withColor(ChatFormatting.LIGHT_PURPLE)
                            .withBold(true)
                    );

            player.sendSystemMessage(warning.append(msg));
        }

        MutableComponent warning = Component.literal(" [!] ")
                .withStyle(ChatFormatting.RED);

        player.sendSystemMessage(
                Component.translatable("event.biomesofcataclysms.youCanStartGame")
                        .withStyle(ChatFormatting.GRAY)
        );

        MutableComponent startMsg = Component.translatable("event.biomesofcataclysms.start")
                .withStyle(style -> style
                        .withUnderlined(false)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                "/biomesOfCataclysms startOrResume"
                        ))
                        .withColor(ChatFormatting.LIGHT_PURPLE)
                        .withBold(true)
                );

        player.sendSystemMessage(warning.append(startMsg));

        player.sendSystemMessage(
                Component.translatable("event.biomesofcataclysms.goToWiki")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
        );

        warning = Component.literal(" [!] ")
                .withStyle(ChatFormatting.RED);

        MutableComponent wikiMsg = Component.translatable("event.biomesofctataclysms.wiki")
                .withStyle(style -> style
                        .withUnderlined(false)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.OPEN_URL,
                                "work in progress"
                        ))
                        .withColor(ChatFormatting.LIGHT_PURPLE)
                        .withBold(true)
                );

        player.sendSystemMessage(warning.append(wikiMsg));
    }

    private static void resumeCommand(Player player) {
        MutableComponent warning = Component.literal(" [!] ")
                .withStyle(ChatFormatting.RED);
        MutableComponent resume = Component.translatable("event.biomesofcataclysms.resume")
                .withStyle(style -> style
                        .withUnderlined(false)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND,
                                "/biomesOfCataclysms startOrResume"
                        ))
                        .withColor(ChatFormatting.LIGHT_PURPLE)
                        .withBold(true)
                );
        player.sendSystemMessage(warning.append(resume));

    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ModVariables.MapVariables globalVars = ModVariables.MapVariables.get(server.overworld());

        if (globalVars.state != 2) return;

        for (ServerLevel level : server.getAllLevels()) {

            int deletionMeter = 0;

            for (ServerPlayer player : level.players()) {
                ChunkPos pPos = new ChunkPos(player.blockPosition());
                Holder<Biome> currentBiome = level.getBiome(player.blockPosition());
                Optional<ResourceKey<Biome>> biomeKey = currentBiome.unwrapKey();

                if (DeletionQueueManager.hasChunksInRadius(level, pPos, 20)) {
                    deletionMeter = 2;
                } else if (DeletionQueueManager.hasChunksInRadius(level, pPos, 5)) {
                    deletionMeter = 4;
                } else {
                    deletionMeter = 1;
                }

                if (biomeKey.isPresent()) {
                    String biomeId = biomeKey.get().location().toString();

                    if (globalVars.deletedBiomes.contains(biomeId)) {
                        if (server.getTickCount() % 2000 == 0) {
                            DeletionQueueManager.rescueMissedChunks(level, 8);
                        }
                        break;

                    }
                }
            }

            if (server.getTickCount() % 10 == 0) {
                globalVars.scanContinuous(level);
            }

            if (server.getTickCount() % 20 == 0) {
                DeletionQueueManager.pruneDynamicQueue(level);
                DeletionQueueManager.refreshDynamicQueue(level);
            }

            if (globalVars.difficulty == 5 && deletionMeter > 3) {
                deletionMeter = 3;
            }

            DeletionQueueManager.processInitialQueue(level, 4);
            if (globalVars.gracePeriod <= 0){
                DeletionQueueManager.processDynamicQueue(level, deletionMeter);
            }
        }

        gracePeriodCheck(server, globalVars.gracePeriod);

        if (globalVars.gracePeriod == 0) {
            globalVars.tickToNextCataclysm--;
            if (!graceCheckHappen){
                for (ServerLevel level : server.getAllLevels()) {
                    ModUtils.sendChatMessage(level, Component.translatable("command.biomesofcataclysms.endOfGracePeriod")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.playNotifySound(
                                SoundEvents.NOTE_BLOCK_BASS.value(), // suono
                                SoundSource.MASTER,                 // categoria audio
                                1F,                               // volume
                                0.8F                                // pitch
                        );
                    }
                }
            }
            graceCheckHappen = true;
        }else{
            globalVars.gracePeriod--;
            globalVars.syncDataGlobal();
        }

        countDown(globalVars, server, globalVars.tickToNextCataclysm, globalVars.nextBiomeToAffect);

        if (globalVars.tickToNextCataclysm <= 0) {
            globalVars.tickToNextCataclysm = globalVars.tickDelayBetweenCataclysm;
            globalVars.selectNextBiomeGlobal(server);

            if (globalVars.difficulty == 0 || globalVars.difficulty == 1) {
                int totalChunks = RuntimeBuffers.INITIAL_ORDER.values().stream()
                        .mapToInt(ArrayDeque::size)
                        .sum();
                globalVars.gracePeriod = (totalChunks/4) * 4;
                globalVars.syncDataGlobal();
                if (globalVars.gracePeriod > 0){
                    graceCheckHappen = false;
                    for (ServerLevel level : server.getAllLevels()) {
                        MutableComponent component1 = Component.translatable("command.biomesofcataclysms.gracePeriod").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                        ModUtils.sendChatMessage(level, Component.translatable("command.biomesofcataclysms.setGracePeriod", globalVars.gracePeriod / 20, component1)
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
                    }
                }
            } else if (globalVars.difficulty == 2) {
                int totalChunks = RuntimeBuffers.INITIAL_ORDER.values().stream()
                        .mapToInt(ArrayDeque::size)
                        .sum();
                globalVars.gracePeriod = (totalChunks/4) * 4;
                if (globalVars.gracePeriod > 15*20) globalVars.gracePeriod = 15*20;
                globalVars.syncDataGlobal();
                if (globalVars.gracePeriod > 0){
                    graceCheckHappen = false;
                    for (ServerLevel level : server.getAllLevels()) {
                        MutableComponent component1 = Component.translatable("command.biomesofcataclysms.gracePeriod").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                        ModUtils.sendChatMessage(level, Component.translatable("command.biomesofcataclysms.setGracePeriod", globalVars.gracePeriod / 20, component1)
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
                    }
                }
            }

        }
        globalVars.syncDataGlobal();
    }

// RIMUOVI O COMMENTA l'evento onChunkLoad precedente.
// Non serve più perché scanContinuous ora copre tutto ciò che entra nel raggio!

    private static boolean graceCheckHappen = true;
    private static void countDown(ModVariables.MapVariables variables, MinecraftServer server, int ticks, String nextBiome) {
        if (ticks >= 0 && ticks <= 200 && ticks % 20 == 0) {
            int seconds = ticks / 20;
            Component cataclysm = Component.literal(variables.cataclysm).withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD);
            ;
            Component secondsText = Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD);

            String translationKey = Util.makeDescriptionId("biome", ResourceLocation.parse(nextBiome));

            Component nextBiomeText = Component.translatable(translationKey).withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD);
            ;


            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Component msg;
                if (ticks == 200) {
                    msg = Component.translatable("event.biomesofcataclysms.countDown", nextBiomeText, cataclysm, secondsText)
                            .withStyle(ChatFormatting.GREEN);
                } else if (ticks == 0) {
                    player.sendSystemMessage(
                            Component.translatable("event.biomesofcataclysms.biomeAffected", nextBiomeText, cataclysm)
                                    .withStyle(ChatFormatting.GREEN), false); // true = action bar
                    msg = secondsText.copy().append("...");
                } else {
                    msg = secondsText.copy().append("...");
                }
                if (ticks > 0) {
                    player.sendSystemMessage(msg, false); // true = action bar
                    if (!(seconds % 2 == 0)) {
                        float pitch = (float) (((10 - seconds) - 1) * 0.06);
                        player.playNotifySound(SoundEvents.COMPARATOR_CLICK, SoundSource.MASTER, 0.6F, 0.6F + pitch);
                    } else {
                        float pitch = (float) (((10 - seconds) - 2) * 0.08);
                        player.playNotifySound(SoundEvents.COMPARATOR_CLICK, SoundSource.MASTER, 0.6F, 0.4F + pitch);
                    }
                } else {
                    ModUtils.playLocalBiomeAffectedSound(player);
                }
            }

        }
    }
    private static void gracePeriodCheck(MinecraftServer server, int ticks) {
        if (graceCheckHappen) return;
        if (ticks >= 0 && ticks % 20 == 0) {

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (ticks == 0) {
                    ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.endOfGracePeriod")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
                    player.playNotifySound(
                            SoundEvents.NOTE_BLOCK_BASS.value(), // suono
                            SoundSource.MASTER,                 // categoria audio
                            1F,                               // volume
                            0.8F                                // pitch
                    );
                }
            }

        }
    }


    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        // Lo facciamo solo lato server e ogni 10 tick (circa ogni 0.5 secondi)
        // per ottimizzare le prestazioni
        if (!player.level().isClientSide && player.level().getGameTime() % 10 == 0) {

            // 1. Otteniamo il bioma alla posizione attuale del giocatore
            Holder<Biome> biomeHolder = player.level().getBiome(player.blockPosition());

            // 2. Recuperiamo il nome del bioma.
            // Usiamo il translation key per averlo nella lingua del giocatore (es. "Pianura" invece di "plains")
            String biomeName = "Sconosciuto";
            Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();

            if (biomeKey.isPresent()) {
                ResourceLocation location = biomeKey.get().location();
                // Creiamo un componente traducibile: biome.minecraft.plains
                String translationKey = "biome." + location.getNamespace() + "." + location.getPath();

                // 3. Prepariamo il messaggio
                MutableComponent message = Component.translatable("event.biomesofcataclysms.playerTick.actionbar")
                        .append(Component.literal(" "))
                        .withStyle(ChatFormatting.GREEN)
                        .withStyle(ChatFormatting.BOLD)
                        .append(Component.translatable(translationKey)
                                .withStyle(ChatFormatting.RED)
                                .withStyle(ChatFormatting.BOLD));

                // 4. Inviamo il messaggio nella Action Bar
                // Il parametro 'true' indica che deve andare nella action bar e non in chat
                ModUtils.sendLocalActionBarMessageTo(player, message);
            }
        }

    }

    // BreakEvent è difensivo: se qualcosa riesce a raggiungere la rottura, annulla il break
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            if (event.getState().is(Blocks.STONE)) {
                ModVariables.MapVariables vars = ModVariables.MapVariables.get(level);
                String target = Biomes.FOREST.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);
            }
        }
    }

}

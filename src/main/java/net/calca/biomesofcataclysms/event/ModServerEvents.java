package net.calca.biomesofcataclysms.event;

import net.calca.biomesofcataclysms.BiomesOfCataclysms;

import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.bar.ProgressBarManager;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.RuntimeData;
import net.calca.biomesofcataclysms.manager.GameManager;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.cataclysm.sunburn.SunBurnStage;
import net.calca.biomesofcataclysms.data.chunk.ChunkInstance;
import net.calca.biomesofcataclysms.manager.ChunkProcessorManager;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

import static net.calca.biomesofcataclysms.manager.ChunkProcessorManager.getSunBurnStage;

// Done with the help of https://github.com/CoFH/CoFHCore/blob/1.19.x/src/main/java/cofh/core/event/AreaEffectEvents.java
// Don't be a jerk License
@EventBusSubscriber(modid = BiomesOfCataclysms.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ModServerEvents {
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
            PersistentData.MapVariables variables = PersistentData.MapVariables.get(event.getServer().overworld());
            if (variables.state == 2){ //Game data might be corrupted: The server was not shut down correctly last time.
                variables.dataCondition = 2;
                variables.syncData(event.getServer().overworld(), true, false);

            }
            for (ServerLevel serverLevel : event.getServer().getAllLevels()){
                ProgressBarManager.initializeOnServerStart(variables, serverLevel);
            }
            loadPersistentToRuntime(event.getServer().overworld());
    }
    public static void loadPersistentToRuntime(ServerLevel server) {
        PersistentData.MapVariables saved = PersistentData.MapVariables.get(server);
        RuntimeData.INITIAL_ORDER.clear();
        RuntimeData.DYNAMIC_ORDER.clear();
        RuntimeData.INITIAL_STATES.clear();
        RuntimeData.DYNAMIC_STATES.clear();
        RuntimeData.CHUNKS.clear();

        for (var e : saved.initialOrder.entrySet()) RuntimeData.INITIAL_ORDER.put(e.getKey(), new ArrayDeque<>(e.getValue()));
        for (var e : saved.dynamicOrder.entrySet()) RuntimeData.DYNAMIC_ORDER.put(e.getKey(), new ArrayDeque<>(e.getValue()));
        for (var e : saved.initialStates.entrySet()) RuntimeData.INITIAL_STATES.put(e.getKey(), copyState(e.getValue()));
        for (var e : saved.dynamicStates.entrySet()) RuntimeData.DYNAMIC_STATES.put(e.getKey(), copyState(e.getValue()));
        for (var dimEntry : saved.chunks.entrySet()) {
            Map<Long, ChunkInstance> dimCopy = new HashMap<>(dimEntry.getValue());
            RuntimeData.CHUNKS.put(dimEntry.getKey(), dimCopy);
        }
        saved.syncData(server, true, false);
    }


    @SubscribeEvent
    public static void onServerStop(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();

        for (ServerLevel serverLevel : server.getAllLevels()) {
            //-------------------
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack()
                            .withSuppressedOutput()
                            .withPermission(4),   // livello OP massimo
                    "biomesOfCataclysms pause"
            );

            ModUtils.sendChatMessage(serverLevel, Component.literal("Server Shutting Down: Game paused"));
        }

    }

    public static void loadRuntimeToPersistent(ServerLevel server) {
        PersistentData.MapVariables saved = PersistentData.MapVariables.get(server);
        saved.initialOrder.clear();
        saved.dynamicOrder.clear();
        saved.initialStates.clear();
        saved.dynamicStates.clear();
        saved.chunks.clear();

        for (var e : RuntimeData.INITIAL_ORDER.entrySet()) saved.initialOrder.put(e.getKey(), new ArrayDeque<>(e.getValue()));
        for (var e : RuntimeData.DYNAMIC_ORDER.entrySet()) saved.dynamicOrder.put(e.getKey(), new ArrayDeque<>(e.getValue()));
        for (var e : RuntimeData.INITIAL_STATES.entrySet()) saved.initialStates.put(e.getKey(), copyState(e.getValue()));
        for (var e : RuntimeData.DYNAMIC_STATES.entrySet()) saved.dynamicStates.put(e.getKey(), copyState(e.getValue()));
        for (var dimEntry : RuntimeData.CHUNKS.entrySet()) {
            Map<Long, ChunkInstance> dimCopy = new HashMap<>();
            for (var chunkEntry : dimEntry.getValue().entrySet()) {
                dimCopy.put(chunkEntry.getKey(), chunkEntry.getValue());
            }
            saved.chunks.put(dimEntry.getKey(), dimCopy);
        }
        saved.setDirty(); // solo salvataggio su disco
    }
    private static ChunkProcessorManager.DimensionState copyState(ChunkProcessorManager.DimensionState src) {
        ChunkProcessorManager.DimensionState dst = new ChunkProcessorManager.DimensionState();
        dst.currentKey = src.currentKey;
        dst.step = src.step;
        return dst;
    }


    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerLevel serverLevel = (ServerLevel) event.getEntity().level();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(serverLevel);
        Player player = event.getEntity();
        ProgressBarManager.addPlayerOnLogIn(player);

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
        } else if (variables.dataCondition == 2) {
            MutableComponent errorMsg = ModUtils.buildErrorMessage(
                    true,
                    2,
                    Component.literal("onPlayerLogin"),
                    Component.translatable("error.biomesofcataclysms.error2"));
            ModUtils.sendLocalChatMessageTo(player, errorMsg);
            ModUtils.playLocalErrorSound(player);

            if (variables.state != 0 && variables.state != 1){
                MinecraftServer server = player.getServer();
                if (server != null) {
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack()
                                    .withSuppressedOutput()
                                    .withPermission(4),   // livello OP massimo
                            "biomesOfCataclysms pause"
                    );
                }
            }

            if (variables.dataCondition != -1){
                variables.dataCondition = -1;
                variables.syncData(serverLevel, true, false);
            }
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

        MutableComponent wikiMsg = Component.translatable("event.biomesofcataclysms.wiki")
                .withStyle(style -> style
                        .withUnderlined(false)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.OPEN_URL,
                                "https://www.notion.so/Biomes-O-Cataclysms-Wiki-06701d6d4acb83929ed7818799b2dde9?source=copy_link"
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
        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(server.overworld());

        if (globalVars.state != 2) return;
        AllCataclysms type = ModUtils.decodeCataclysmFromString(globalVars.cataclysm);


        if (globalVars.tickFloodHeights(server.overworld())) {
            // Se l'altezza è cambiata, chiediamo al manager di rimettere in coda i chunk PARTIAL
            ChunkProcessorManager.wakeUpPartialChunks(server.overworld());
        }

        int initialDestructionSpeed = 4;
        for (ServerLevel level : server.getAllLevels()) {

            int dynamicDestructionSpeed = 4;

            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) continue;
                if (globalVars.difficulty <= 2) {
                    player.forceAddEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, true), player);
                }
                ChunkPos pPos = new ChunkPos(player.blockPosition());
                Holder<Biome> currentBiome = level.getBiome(player.blockPosition());
                Optional<ResourceKey<Biome>> biomeKey = currentBiome.unwrapKey();
                if (biomeKey.isPresent()) {
                String biomeId = biomeKey.get().location().toString();
                SunBurnStage stage = SunBurnStage.FIRE; // Default
                if (type == AllCataclysms.SUN_BURNT) {
                    long elapsed = globalVars.getSunBurnElapsedTicks(biomeId, level);
                    stage = getSunBurnStage(elapsed);
                }

                if ((type != AllCataclysms.SUN_BURNT || stage == SunBurnStage.INSTANT_TRANSFORM) && type != AllCataclysms.FLOODED){
                    if (globalVars.pcPower == 0) { // Potato
                        dynamicDestructionSpeed = 1;
                        initialDestructionSpeed = 1;
                    } else if (globalVars.pcPower == 1) { //Low
                        if (ChunkProcessorManager.hasChunksInRadius(level, pPos, 8)) {
                            dynamicDestructionSpeed = 2;
                        } else {
                            dynamicDestructionSpeed = 1;
                        }
                        initialDestructionSpeed = 2;
                    } else if (globalVars.pcPower == 2) { //Medium
                        if (ChunkProcessorManager.hasChunksInRadius(level, pPos, 8)) {
                            dynamicDestructionSpeed = 3;
                        } else {
                            dynamicDestructionSpeed = 2;
                        }
                        initialDestructionSpeed = 3;
                    } else if (globalVars.pcPower == 3) { //High
                        if (ChunkProcessorManager.hasChunksInRadius(level, pPos, 8)) {
                            dynamicDestructionSpeed = 4;
                        } else {
                            dynamicDestructionSpeed = 2;
                        }
                        initialDestructionSpeed = 4;
                    } else if (globalVars.pcPower == 4) { // Estreme
                        if (ChunkProcessorManager.hasChunksInRadius(level, pPos, 8)) {
                            dynamicDestructionSpeed = 4;
                        } else if (ChunkProcessorManager.hasChunksInRadius(level, pPos, 16)) {
                            dynamicDestructionSpeed = 3;
                        } else {
                            dynamicDestructionSpeed = 1;
                        }
                        initialDestructionSpeed = 4;
                    } else if (globalVars.pcPower == 5) { // Max
                        if (ChunkProcessorManager.hasChunksInRadius(level, pPos, 8)) {
                            dynamicDestructionSpeed = 4;
                        } else if (ChunkProcessorManager.hasChunksInRadius(level, pPos, 16)) {
                            dynamicDestructionSpeed = 3;
                        } else if (ChunkProcessorManager.hasChunksInRadius(level, pPos, 24)) {
                            dynamicDestructionSpeed = 3;
                        } else {
                            dynamicDestructionSpeed = 2;
                        }
                        initialDestructionSpeed = 4;
                    }
                } else if (type == AllCataclysms.FLOODED) {
                    dynamicDestructionSpeed = 2;
                }

                    if (globalVars.deletedBiomes.contains(biomeId)) {
                        if (server.getTickCount() % 2000 == 0) {
                            ChunkProcessorManager.rescueMissedChunks(level, 8);
                        }
                        break;
                    }
                }
            }

            if (server.getTickCount() % 10 == 0) {
                globalVars.scanContinuous(level);
            }

            if (server.getTickCount() % 20 == 0) {
                ChunkProcessorManager.pruneDynamicQueue(level);
                ChunkProcessorManager.refreshDynamicQueue(level);
            }

            if (globalVars.difficulty == 5 && dynamicDestructionSpeed > 3) {
                dynamicDestructionSpeed = 3;
            }

            if (type != AllCataclysms.SUN_BURNT && type != AllCataclysms.FLOODED){
                ChunkProcessorManager.processInitialQueue(level, initialDestructionSpeed);
            }else{
                boolean queueEmpty = ChunkProcessorManager.isQueueEmpty(level);
                boolean timerElapsed = (level.getGameTime() % 200 == 0); // 200 tick = 10 secondi
                if (queueEmpty || timerElapsed) {
                    ChunkProcessorManager.resetSunBurntWaves(level);
                }
            }
            if (globalVars.gracePeriod <= 0) {
                ChunkProcessorManager.processDynamicQueue(level, dynamicDestructionSpeed);
            }
        }

        gracePeriodCheck(globalVars, server);

        if (globalVars.gracePeriod == 0) {
            globalVars.tickToNextCataclysm--;
            if (globalVars.timer != -1){
                if (globalVars.timer <= 0){
                    for (ServerLevel level : server.getAllLevels()) {
                        for (ServerPlayer player : level.players()) {
                            if (player.isSpectator()) continue;
                            if (!player.hasEffect(MobEffects.WATER_BREATHING) && !player.isUnderWater()){
                                player.setAirSupply(player.getAirSupply() - 5);
                            } else if (player.hasEffect(MobEffects.WATER_BREATHING)) {
                                if (player.getAirSupply() > 290){
                                    player.setAirSupply(290);
                                }

                            }
                        }
                    }
                }else{
                    globalVars.timer--;
                    ProgressBarManager.TimerProgressBar.tick(globalVars);
                }
            }
            if (!globalVars.graceCheckHappen) {
                for (ServerLevel level : server.getAllLevels()) {
                    ModUtils.sendChatMessage(level, Component.translatable("command.biomesofcataclysms.endOfGracePeriod")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.playNotifySound(
                                SoundEvents.NOTE_BLOCK_BASS.value(), // suono
                                SoundSource.MASTER,                 // categoria audio
                                1F,                                 // volume
                                0.8F                                // pitch
                        );
                    }
                }
            }
            globalVars.graceCheckHappen = true;
        } else {
            globalVars.gracePeriod--;
        }

        countDown(globalVars, server, globalVars.tickToNextCataclysm, globalVars.nextBiomeToAffect);

        if (globalVars.tickToNextCataclysm <= 0) {
            globalVars.tickToNextCataclysm = globalVars.tickDelayBetweenCataclysm;

            if (type == AllCataclysms.SUN_BURNT) {
                for (ServerLevel level : server.getAllLevels()) {
                    String biomeId = globalVars.nextBiomeToAffect;
                    globalVars.startSunBurn(biomeId, level);
                }
            }

            if (type == AllCataclysms.FLOODED) {
                for (ServerLevel level : server.getAllLevels()) {
                    globalVars.startFloodForBiome(globalVars.nextBiomeToAffect, level);
                }
            }

            globalVars.selectNextBiomeGlobal(server);

            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    GameManager.biomesCheck(player, level, globalVars);
                }
            }

            if (type != AllCataclysms.SUN_BURNT && type != AllCataclysms.FLOODED){
                if (globalVars.difficulty == 0 || globalVars.difficulty == 1) {
                    int totalChunks = RuntimeData.INITIAL_ORDER.values().stream()
                            .mapToInt(ArrayDeque::size)
                            .sum();
                    globalVars.gracePeriod = (totalChunks / initialDestructionSpeed) * 4;
                    if (globalVars.gracePeriod > 0) {
                        ProgressBarManager.TimerProgressBar.TIMER_PROGRESS_BAR.setColor(BossEvent.BossBarColor.YELLOW);
                        globalVars.graceCheckHappen = false;
                        MutableComponent component1 = Component.translatable("command.biomesofcataclysms.gracePeriod").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                        int seconds = globalVars.gracePeriod / 20;
                        MutableComponent component2 = Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                        for (ServerLevel level : server.getAllLevels()) {
                            ModUtils.sendChatMessage(level, Component.translatable("command.biomesofcataclysms.setGracePeriod", component2, component1)
                                    .withStyle(ChatFormatting.YELLOW)); //Starting
                        }
                    }
                } else if (globalVars.difficulty == 2) {
                    int totalChunks = RuntimeData.INITIAL_ORDER.values().stream()
                            .mapToInt(ArrayDeque::size)
                            .sum();
                    globalVars.gracePeriod = (totalChunks / 4) * 4;
                    if (globalVars.gracePeriod > 15 * 20) globalVars.gracePeriod = 15 * 20;
                    if (globalVars.gracePeriod > 0) {
                        ProgressBarManager.TimerProgressBar.TIMER_PROGRESS_BAR.setColor(BossEvent.BossBarColor.YELLOW);
                        globalVars.graceCheckHappen = false;
                        MutableComponent component1 = Component.translatable("command.biomesofcataclysms.gracePeriod").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                        int seconds = globalVars.gracePeriod / 20;
                        MutableComponent component2 = Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                        ModUtils.sendChatMessage(server.overworld(), Component.translatable("command.biomesofcataclysms.setGracePeriod", component2, component1)
                                .withStyle(ChatFormatting.YELLOW)); //Starting
                    }
                }

            }
        }

        //globalVars.syncData(server.overworld());
        for (ServerLevel level : server.getAllLevels()) {
            globalVars.syncData(level, true, false);
        }

    }

    private static void countDown(PersistentData.MapVariables variables, MinecraftServer server, int ticks, String nextBiome) {
        if (ticks >= 0 && ticks % 20 == 0) {
            int seconds = ticks / 20;
            Component cataclysm = Component.literal(variables.cataclysm).withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD);
            Component secondsText = Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD);
            String translationKey = Util.makeDescriptionId("biome", ResourceLocation.parse(nextBiome));
            Component nextBiomeText = Component.translatable(translationKey).withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD);

            if (ticks <= 200){
                    Component msg;
                    if (ticks == 200) {
                        if (variables.difficulty >= 3){
                         nextBiomeText = Component.literal("?7/?fhs?")
                                         .withStyle(ChatFormatting.RED)
                                         .withStyle(ChatFormatting.BOLD)
                                         .withStyle(ChatFormatting.OBFUSCATED);
                        }
                        msg = Component.translatable("event.biomesofcataclysms.countDown", nextBiomeText, cataclysm, secondsText)
                                .withStyle(ChatFormatting.GREEN);
                    } else if (ticks == 0) {
                        for (ServerLevel level : server.getAllLevels()){
                            ModUtils.sendChatMessage(level, Component.translatable("event.biomesofcataclysms.biomeAffected", nextBiomeText, cataclysm)
                                    .withStyle(ChatFormatting.GREEN));
                        }

                        msg = secondsText.copy().append("...");
                    } else {
                        msg = secondsText.copy().append("...");
                    }
                    if (ticks > 0) {
                        if (variables.difficulty < 4){
                            for (ServerLevel level : server.getAllLevels()) {
                                ModUtils.sendChatMessage(level, msg);
                            }
                        }
                        if (!(seconds % 2 == 0)) {
                            if (variables.difficulty < 4){
                                float pitch = (float) (((10 - seconds) - 1) * 0.06);
                                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                                    player.playNotifySound(SoundEvents.COMPARATOR_CLICK, SoundSource.MASTER, 0.6F, 0.6F + pitch);
                                }
                            }
                        } else {
                            if (variables.difficulty < 4){
                                float pitch = (float) (((10 - seconds) - 2) * 0.08);
                                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                                    player.playNotifySound(SoundEvents.COMPARATOR_CLICK, SoundSource.MASTER, 0.6F, 0.4F + pitch);
                                }
                            }
                        }
                    } else {
                        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                            ModUtils.playLocalBiomeAffectedSound(player);
                        }
                    }

            }else{
                if (variables.difficulty == 0){
                    if (variables.tickToNextCataclysm == 30*20){
                        for (ServerLevel level : server.getAllLevels()){
                            ModUtils.sendChatMessage(level, Component.translatable("event.biomesofcataclysms.countDown", nextBiomeText, cataclysm, secondsText)
                                    .withStyle(ChatFormatting.YELLOW));
                        }
                    }
                }else if (variables.difficulty == 1){
                    if (variables.tickToNextCataclysm == 20*20) {
                        for (ServerLevel level : server.getAllLevels()) {
                            ModUtils.sendChatMessage(level, Component.translatable("event.biomesofcataclysms.countDown", nextBiomeText, cataclysm, secondsText)
                                    .withStyle(ChatFormatting.YELLOW));
                        }
                    }
                }
            }



        }

    }
    private static void gracePeriodCheck(PersistentData.MapVariables globalVars, MinecraftServer server) {
        if (globalVars.graceCheckHappen) return;
        int ticks = globalVars.gracePeriod;
        if (ticks == 0) {
            globalVars.graceCheckHappen = true;
            for (ServerLevel level : server.getAllLevels()) {
                ModUtils.sendChatMessage(level, Component.translatable("command.biomesofcataclysms.endOfGracePeriod")
                        .withStyle(ChatFormatting.YELLOW));
                for (ServerPlayer player : level.players()) {
                    player.playNotifySound(
                            SoundEvents.NOTE_BLOCK_BASS.value(), // suono
                            SoundSource.MASTER,                 // categoria audio
                            1F,                               // volume
                            0.8F                                // pitch
                    );
                }
            }
            if (globalVars.deletedBiomes.size() != globalVars.totalBiomes){
                ProgressBarManager.TimerProgressBar.TIMER_PROGRESS_BAR.setColor(BossEvent.BossBarColor.BLUE);
            }
        }
    }


    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(Objects.requireNonNull(player.getServer()).overworld());
        if (globalVars.difficulty == 4){
            MutableComponent message = Component.translatable("event.biomesofcataclysms.playerTick.actionbar")
                    .append(Component.literal(" "))
                    .withStyle(ChatFormatting.GREEN)
                    .withStyle(ChatFormatting.BOLD)
                    .append(Component.literal("?7/?fhs?")
                            .withStyle(ChatFormatting.RED)
                            .withStyle(ChatFormatting.BOLD)
                            .withStyle(ChatFormatting.OBFUSCATED));

            ModUtils.sendLocalActionBarMessageTo(player, message);

        }else{
            if (!player.level().isClientSide && player.level().getGameTime() % 10 == 0) {
                Holder<Biome> biomeHolder = player.level().getBiome(player.blockPosition());
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

        //spawnMonstersAroundEternalDarkness(player, event.getEntity().level());

    }

    private static void spawnMonstersAroundEternalDarkness(Player player, Level level) {
        // Eseguiamo il codice solo sul Server e ogni 100 tick (5 secondi)
        if (!level.isClientSide() && level.getGameTime() % 100 == 0) {
            ServerLevel serverLevel = (ServerLevel) level;

            // Definisci un raggio d'azione (es. da 0 a 32 blocchi)
            int radius = 16;
            int xOffset = player.getRandom().nextIntBetweenInclusive(-radius, radius);
            int zOffset = player.getRandom().nextIntBetweenInclusive(-radius, radius);
            int yOffset = player.getRandom().nextIntBetweenInclusive(-4, 4); // Cerca anche un po' sopra/sotto

            BlockPos spawnPos = player.blockPosition().offset(xOffset, yOffset, zOffset);

            // Scegliamo un mostro a caso (es. uno Zombie)
            EntityType<? extends Mob> entityType = EntityType.ZOMBIE;

            // Verifichiamo se il blocco è adatto allo spawn (aria per l'entità, solido sotto)
            if (serverLevel.getBlockState(spawnPos).isAir() && serverLevel.getBlockState(spawnPos.below()).isSolid()) {

                // Spawna l'entità ignorando le restrizioni di luce e distanza di vanilla
                Mob mob = entityType.create(serverLevel);
                if (mob != null) {
                    mob.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getRandom().nextFloat() * 360F, 0.0F);

                    // Finalizza lo spawn (imposta equipaggiamento, effetti, ecc.)
                    mob.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), MobSpawnType.NATURAL, null);

                    serverLevel.addFreshEntity(mob);
                }
            }
        }

    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event){
        Player player = event.getEntity();
        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(Objects.requireNonNull(player.getServer()).overworld());
        playerRespawnManager(globalVars, player);
    }

    private static void playerRespawnManager(PersistentData.MapVariables globalVars, Player player){
        if (globalVars.difficulty == 0) exEsDifficultyRespawn(globalVars, player);
        else if (globalVars.difficulty == 1) esDifficultyRespawn(globalVars, player);
        else if (globalVars.difficulty == 2) haDifficultyRespawn(globalVars, player);
        else if (globalVars.difficulty == 3) imDifficultyRespawn(globalVars, player);
        else if (globalVars.difficulty == 4) {
            if (player instanceof ServerPlayer serverPlayer){
                serverPlayer.setGameMode(GameType.SPECTATOR);
                ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.setToSpectator")
                        .withStyle(ChatFormatting.GREEN));
            }
        }

    }
    private static void exEsDifficultyRespawn(PersistentData.MapVariables globalVars, Player player){
        if (globalVars.deletedBiomes.size() >=35){
            player.getInventory().add(new ItemStack(Items.IRON_SWORD, 1));
            player.getInventory().add(new ItemStack(Items.IRON_PICKAXE, 1));
            player.getInventory().add(new ItemStack(Items.IRON_SHOVEL, 1));
            player.getInventory().add(new ItemStack(Items.IRON_AXE, 1));
            player.getInventory().add(new ItemStack(Items.IRON_HOE, 1));
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 64));
            player.getInventory().add(new ItemStack(Items.COBBLESTONE, 128));
            player.getInventory().add(new ItemStack(Items.BREAD, 24));
            player.getInventory().add(new ItemStack(Items.ENDER_PEARL, 6));
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items. CHAINMAIL_HELMET));
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items. CHAINMAIL_LEGGINGS));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items. CHAINMAIL_BOOTS));

        } else if (globalVars.deletedBiomes.size() >= 20) {
            player.getInventory().add(new ItemStack(Items.STONE_SWORD, 1));
            player.getInventory().add(new ItemStack(Items.STONE_PICKAXE, 1));
            player.getInventory().add(new ItemStack(Items.STONE_SHOVEL, 1));
            player.getInventory().add(new ItemStack(Items.STONE_AXE, 1));
            player.getInventory().add(new ItemStack(Items.STONE_HOE, 1));
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
            player.getInventory().add(new ItemStack(Items.COBBLESTONE, 32));
            player.getInventory().add(new ItemStack(Items.BREAD, 12));

        } else if (globalVars.deletedBiomes.size() >= 10) {
            player.getInventory().add(new ItemStack(Items.WOODEN_SWORD, 1));
            player.getInventory().add(new ItemStack(Items.WOODEN_PICKAXE, 1));
            player.getInventory().add(new ItemStack(Items.WOODEN_SHOVEL, 1));
            player.getInventory().add(new ItemStack(Items.WOODEN_AXE, 1));
            player.getInventory().add(new ItemStack(Items.WOODEN_HOE, 1));
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
            player.getInventory().add(new ItemStack(Items.BREAD, 6));

        }

    }
    private static void esDifficultyRespawn(PersistentData.MapVariables globalVars, Player player){
        if (globalVars.deletedBiomes.size() >=30){
            player.getInventory().add(new ItemStack(Items.STONE_SWORD, 1));
            player.getInventory().add(new ItemStack(Items.STONE_PICKAXE, 1));
            player.getInventory().add(new ItemStack(Items.STONE_SHOVEL, 1));
            player.getInventory().add(new ItemStack(Items.STONE_AXE, 1));
            player.getInventory().add(new ItemStack(Items.STONE_HOE, 1));
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
            player.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
            player.getInventory().add(new ItemStack(Items.BREAD, 12));
            player.getInventory().add(new ItemStack(Items.ENDER_PEARL, 4));

        } else if (globalVars.deletedBiomes.size() >= 15) {
            player.getInventory().add(new ItemStack(Items.WOODEN_SWORD, 1));
            player.getInventory().add(new ItemStack(Items.WOODEN_PICKAXE, 1));
            player.getInventory().add(new ItemStack(Items.WOODEN_SHOVEL, 1));
            player.getInventory().add(new ItemStack(Items.WOODEN_AXE, 1));
            player.getInventory().add(new ItemStack(Items.WOODEN_HOE, 1));
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
            player.getInventory().add(new ItemStack(Items.BREAD, 6));

        }

    }
    private static void haDifficultyRespawn(PersistentData.MapVariables globalVars, Player player){
        if (globalVars.deletedBiomes.size() >= 40){
            player.getInventory().add(new ItemStack(Items.STONE_SWORD, 1));
            player.getInventory().add(new ItemStack(Items.STONE_PICKAXE, 1));
            player.getInventory().add(new ItemStack(Items.STONE_SHOVEL, 1));
            player.getInventory().add(new ItemStack(Items.STONE_AXE, 1));
            player.getInventory().add(new ItemStack(Items.STONE_HOE, 1));
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
            player.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
            player.getInventory().add(new ItemStack(Items.BREAD, 12));
            player.getInventory().add(new ItemStack(Items.ENDER_PEARL, 4));

        } else if (globalVars.deletedBiomes.size() >= 25) {
            player.getInventory().add(new ItemStack(Items.WOODEN_SWORD, 1));
            player.getInventory().add(new ItemStack(Items.WOODEN_PICKAXE, 1));
            player.getInventory().add(new ItemStack(Items.WOODEN_SHOVEL, 1));
            player.getInventory().add(new ItemStack(Items.WOODEN_AXE, 1));
            player.getInventory().add(new ItemStack(Items.WOODEN_HOE, 1));
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
            player.getInventory().add(new ItemStack(Items.BREAD, 6));

        }

    }
    private static void imDifficultyRespawn(PersistentData.MapVariables globalVars, Player player){
        if (globalVars.deletedBiomes.size() >= 40){
            player.getInventory().add(new ItemStack(Items.STONE_SWORD, 1));
            player.getInventory().add(new ItemStack(Items.STONE_PICKAXE, 1));
            player.getInventory().add(new ItemStack(Items.STONE_SHOVEL, 1));
            player.getInventory().add(new ItemStack(Items.STONE_AXE, 1));
            player.getInventory().add(new ItemStack(Items.STONE_HOE, 1));
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));
            player.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
            player.getInventory().add(new ItemStack(Items.BREAD, 8));
            player.getInventory().add(new ItemStack(Items.ENDER_PEARL, 3));

        }

    }

    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType().getCategory() != MobCategory.MONSTER) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
    }

    @SubscribeEvent
    public static void onSpawnPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        event.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
    }

    //Used only in debug mode
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PersistentData.MapVariables vars = PersistentData.MapVariables.get(level);
            if (!vars.debugMode) return;
            if (event.getState().is(Blocks.STONE)) {
                    String target = Biomes.FOREST.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);
            }else if (event.getState().is(Blocks.GRASS_BLOCK)){
                String target = Biomes.PLAINS.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.DIRT)) {
                String target = Biomes.RIVER.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.NETHERRACK)) {
                String target = Biomes.NETHER_WASTES.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.SOUL_SAND)) {
                String target = Biomes.SOUL_SAND_VALLEY.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.END_STONE)) {
                String target = Biomes.THE_END.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.JUNGLE_LOG)) {
                String target = Biomes.JUNGLE.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.ACACIA_LOG)) {
                String target = Biomes.SAVANNA.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.SPRUCE_LOG)) {
                String target = Biomes.OLD_GROWTH_PINE_TAIGA.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.PRISMARINE)) {
                String target = Biomes.OCEAN.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.ICE)) {
                String target = Biomes.COLD_OCEAN.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.PACKED_ICE)) {
                String target = Biomes.LUKEWARM_OCEAN.location().toString();

                vars.forceNextBiome(target, level);

                // Debug in chat
                event.getPlayer().displayClientMessage(
                        Component.literal("Destino modificato! Prossimo: " + vars.nextBiomeToAffect)
                                .withStyle(ChatFormatting.GOLD),
                        false
                );

                level.playSound(null, event.getPos(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 1.0f, 0.7f);

            } else if (event.getState().is(Blocks.SCULK)) {
                String target = Biomes.DEEP_DARK.location().toString();

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

package net.calca.biomesofcataclysms.command;


import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.ModVariables;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.commands.Commands;

import java.util.Objects;

import static net.calca.biomesofcataclysms.ModUtils.decodeCataclysmFromEnum;

@EventBusSubscriber
public class ModCommands {
    private static void gameAboutToStartSettingsMessage(LevelAccessor levelAccessor, ServerPlayer serverPlayer, ModVariables.MapVariables variables){
            ModUtils.sendChatMessage((ServerLevel) levelAccessor, Component.translatable("command.biomesofcataclysms.aboutToStart")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Confirm

            int totalSettings = 5;
            for (int i = 0; i < totalSettings; i++) {
                String translationKey;
                String value = "";

                switch (i) {
                    case 0 -> {
                        translationKey = "command.biomesofcataclysms.difficulty";
                        if (variables.difficulty == 0){
                            value = "command.biomesofcataclysms.Difficulty.0";
                        } else if (variables.difficulty == 1) {
                            value = "command.biomesofcataclysms.Difficulty.1";
                        } else if (variables.difficulty == 2) {
                            value = "command.biomesofcataclysms.Difficulty.2";
                        } else if (variables.difficulty == 3) {
                            value = "command.biomesofcataclysms.Difficulty.3";
                        } else if (variables.difficulty == 4) {
                            value = "command.biomesofcataclysms.Difficulty.4";
                        }
                    }
                    case 1 -> {
                        translationKey = "command.biomesofcataclysms.mode";
                        if (variables.mode == 0){
                            value = "command.biomesofcataclysms.classicMode";
                        } else if (variables.mode == 1) {
                            value = "command.biomesofcataclysms.apocalypseMode";
                        }
                    }
                    case 2 -> {
                        translationKey = "command.biomesofcataclysms.delayBetweenCataclysms";
                        int totalSeconds = variables.tickDelayBetweenCataclysm / 20;

                        int minutes = totalSeconds / 60;
                        int seconds = totalSeconds % 60;

                        if (seconds > 0){
                            if (seconds < 10){
                                value = String.valueOf((minutes + ":0" + seconds));
                            }else{
                                value = String.valueOf((minutes + ":" + seconds));
                            }
                        }else{
                            value = String.valueOf((minutes));
                        }

                    }
                    case 3 -> {
                        translationKey = "command.biomesofcataclysms.pcPower";
                        value = decodePcPowerToString(variables).toString();
                    }
                    case 4 -> {
                        translationKey = "command.biomesofcataclysms.timer";
                        if (variables.timer > 0){
                            value = String.valueOf((variables.timer/20)/60);
                        }else{
                            value = "command.biomesofcataclysms.none";
                        }
                    }
                    default -> {
                        continue;
                    }
                }

                MutableComponent warning = Component.literal(" |-> ")
                        .withStyle(ChatFormatting.DARK_GREEN);

                MutableComponent msg = Component.translatable(translationKey, Component.translatable(value))
                        .withStyle(style -> style
                                .withUnderlined(false)
                                .withColor(ChatFormatting.GRAY)
                                .withBold(false)
                        );

                ModUtils.sendChatMessage((ServerLevel) levelAccessor, warning.append(msg)); //Confirm
            }

            MutableComponent warning = Component.literal(" [!] ")
                    .withStyle(ChatFormatting.RED);
            MutableComponent msg = Component.translatable("command.biomesofcataclysms.confirm")
                    .withStyle(style -> style
                            .withUnderlined(false)
                            .withClickEvent(new ClickEvent(
                                    ClickEvent.Action.RUN_COMMAND,
                                    "/biomesOfCataclysms startOrResume"
                            ))
                            .withColor(ChatFormatting.LIGHT_PURPLE)
                            .withBold(true)
                    );

            ModUtils.sendLocalChatMessageTo(serverPlayer, warning.append(msg)); //Confirm
    }
    private static String decodePcPowerToString(ModVariables.MapVariables variables){
        MutableComponent returnValue;
        if (variables.pcPower >= 0 && variables.pcPower <= 5){
            returnValue = Component.translatable("command.biomesofcataclysms.pcPower." + variables.pcPower);
        }else{
            returnValue = ModUtils.buildErrorMessage(false,9, Component.literal("decodePcPowerToString"),
                    Component.translatable("error.biomesofcataclysms.error9"));
        }
        return returnValue.getString();
    }

    private static MutableComponent decodeModeToString(ModVariables.MapVariables variables){
        if (variables.mode == 0){
            return Component.translatable("command.biomesofcataclysms.classicMode").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        }else if (variables.mode == 1){
            return Component.translatable("command.biomesofcataclysms.apocalypseMode").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        }else{
            return Component.literal("Unknow Error: Mode = Null").withStyle(ChatFormatting.DARK_RED);
        }
    }
    private static boolean changeSettingsCheck(ModVariables.MapVariables variables, Player player){
        if ((variables.state == 2 || ModUtils.isAGameAlreadyStarted(variables))){
            MutableComponent msg = Component.translatable("command.biomesofcataclysms.cannotChangeSettingsWhileInGame")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            ModUtils.sendLocalChatMessageTo(player, msg);
                player.playNotifySound(
                        SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                        SoundSource.MASTER,                 // categoria audio
                        1F,                               // volume
                        0.4F                                // pitch
                );
            return false;
        } else if ((variables.state == 0 || variables.state == 1) && !ModUtils.isAGameAlreadyStarted(variables)) {
            return true;
        }else{
            MutableComponent errorMsg = ModUtils.buildErrorMessage(
                    false,
                    1,
                    Component.literal("changeSettingsCheck"),
                    Component.translatable("error.biomesofcataclysms.error1"));
            ModUtils.sendLocalChatMessageTo(player, errorMsg);
            ModUtils.playLocalErrorSound(player);

            return false;
        }
    }
    private static void dataRecoveryCommand(Player player) {
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

    private static void setDifficultyMsg(int difficulty, MinecraftServer server, ServerLevel serverLevel){
        ModUtils.sendChatMessage(serverLevel, Component.translatable("command.biomesofcataclysms.setDifficulty" + "." + difficulty)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                    SoundSource.MASTER,                 // categoria audio
                    1F,                               // volume
                    0.9F                                // pitch
            );
        }
    }
    private static void setPcPowerMsg(int pcPower, MinecraftServer server, ServerLevel serverLevel){
        ModUtils.sendChatMessage(serverLevel, Component.translatable("command.biomesofcataclysms.setPcPower." + pcPower)
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                    SoundSource.MASTER,                 // categoria audio
                    1F,                               // volume
                    0.9F                                // pitch
            );
        }
    }

    private static void settingUpPcPowerComponents(ModVariables.MapVariables variables, ServerLevel world, MinecraftServer server){
        if (variables.pcPower == 0){ //Potato
            variables.radius = 8;
            variables.destructionSpeed = 0.25;
        } else if (variables.pcPower == 1) { // Low
            variables.radius = 16;
            variables.destructionSpeed = 0.5;
        } else if (variables.pcPower == 2) { // Medium
            variables.radius = 16;
            variables.destructionSpeed = 0.75;
        } else if (variables.pcPower == 3) { // High
            variables.radius = 16;
            variables.destructionSpeed = 1;
        } else if (variables.pcPower == 4) { // Extreme
            variables.radius = 24;
            variables.destructionSpeed = 1;
        } else if (variables.pcPower == 5) { // Max
            variables.radius = 32;
            variables.destructionSpeed = 1;
        }else{
            ModUtils.sendChatMessage(world, ModUtils.buildErrorMessage(true, 9,
                    Component.literal("settingPcPowerComponents"), Component.translatable("error.biomesofcataclysms.error9")));

        }
        variables.syncData(world);
        Minecraft.getInstance().options.renderDistance().set(variables.radius);
        server.getPlayerList().setViewDistance(variables.radius);
    }
    private static void settingUpApocalypseMode(ModVariables.MapVariables variables, ServerLevel level){
        AllCataclysms[] values = AllCataclysms.values();
        AllCataclysms random =
                values[1 + new java.util.Random().nextInt(values.length - 1)];
        variables.cataclysm = decodeCataclysmFromEnum(random);
        MutableComponent component = Component.literal(decodeCataclysmFromEnum(random))
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        MutableComponent warning = Component.literal(" [!] ")
                .withStyle(ChatFormatting.RED);
        MutableComponent msg = Component.translatable("command.biomesofcataclysms.changeCataclysm")
                .withStyle(style -> style
                        .withUnderlined(false)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.SUGGEST_COMMAND,
                                "/biomesOfCataclysms game setCataclysm"
                        ))
                        .withColor(ChatFormatting.LIGHT_PURPLE)
                        .withBold(true)
                );


        BiomesOfCataclysms.queueServerWork(10, () -> {
            ModUtils.sendChatMessage(level, Component.translatable("command.biomesofcataclysms.extractingRandomCataclysm")
                    .withStyle(ChatFormatting.YELLOW));
            ModUtils.sendChatMessage(level, warning.append(msg));
            BiomesOfCataclysms.queueServerWork(30, () -> {
                ModUtils.sendChatMessage(level, Component.translatable("command.biomesofcataclysms.randomCataclysmIs", component)
                        .withStyle(ChatFormatting.YELLOW));
            });
        });

    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {//OP players
        event.getDispatcher().register(Commands.literal("biomesOfCataclysms").requires(s -> s.hasPermission(4))
                .then(Commands.literal("startOrResume")
                        .executes(arguments -> {
                            ServerPlayer serverPlayer = arguments.getSource().getPlayer();
                            assert serverPlayer != null;
                            MinecraftServer server = arguments.getSource().getServer();
                            ServerLevel world = arguments.getSource().getLevel();
                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                            if (variables.tickToNextCataclysm == variables.tickDelayBetweenCataclysm && variables.biomesAffected == 0){
                                if (variables.state == 0){
                                    variables.state = 1;
                                    gameAboutToStartSettingsMessage(world, serverPlayer, variables);
                                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                                        player.playNotifySound(
                                                SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                SoundSource.MASTER,                 // categoria audio
                                                1F,                               // volume
                                                0.8F                                // pitch
                                        );
                                    }
                                } else if (variables.state == 1) {
                                    variables.state = 2;
                                    ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.start")
                                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
                                    if (variables.totalBiomes == 0){
                                        variables.generateFullBiomeList(world);
                                    }

                                    if (variables.mode == 0){ //Classic
                                        variables.cataclysm = decodeCataclysmFromEnum(AllCataclysms.DESTROYED);

                                    } else if (variables.mode == 1) { //Apocalypse
                                        settingUpApocalypseMode(variables, world);
                                    }
                                    settingUpPcPowerComponents(variables, world, server);
                                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                                        player.playNotifySound(
                                                SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                SoundSource.MASTER,                 // categoria audio
                                                1F,                               // volume
                                                1.2F                                // pitch
                                        );
                                    }
                                }
                            }else if (variables.state == 1){
                                variables.state = 2;
                                ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.resume")
                                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Resuming
                                settingUpPcPowerComponents(variables, world, server);
                                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                                    player.playNotifySound(
                                            SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                            SoundSource.MASTER,                 // categoria audio
                                            1F,                               // volume
                                            1.2F                                // pitch
                                    );
                                }
                            } else if (variables.state == 2) {
                                ModUtils.sendLocalChatMessageTo(serverPlayer, Component.translatable("command.biomesofcataclysms.gameAlreadyStarted")
                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                                arguments.getSource().getPlayer().playNotifySound(
                                        SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                        SoundSource.MASTER,                 // categoria audio
                                        1F,                               // volume
                                        0.4F                                // pitch
                                );
                            }else{
                                ModUtils.sendLocalChatMessageTo(serverPlayer,
                                        ModUtils.buildErrorMessage(
                                        true,
                                        7,
                                        Component.literal("startOrResume[C]"),
                                        Component.translatable("error.biomesofcataclysms.error7")
                                ));
                                ModUtils.playLocalErrorSound(serverPlayer);
                            }

                            variables.syncData(world);

                            return 0;
                        }))
                .then(Commands.literal("pause")
                        .executes(arguments -> {
                            ServerLevel world = arguments.getSource().getLevel();
                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                            if (variables.state == 2){
                                ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.pause")
                                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));

                                MinecraftServer server = arguments.getSource().getServer();
                                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                                    player.playNotifySound(
                                            SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                            SoundSource.MASTER,                 // categoria audio
                                            1F,                               // volume
                                            0.6F                                // pitch
                                    );
                                }

                                variables.state = 1;
                                variables.syncData(world);
                            }else{
                                ServerPlayer player = arguments.getSource().getPlayer();
                                assert player != null;
                                ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.alreadyPaused")
                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                                player.playNotifySound(
                                        SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                        SoundSource.MASTER,                 // categoria audio
                                        1F,                               // volume
                                        0.4F                                // pitch
                                );
                            }

                            return 0;
                        }))
                .then(Commands.literal("reset")
                        .executes(arguments -> {
                            ServerLevel world = arguments.getSource().getLevel();
                            ServerPlayer player = arguments.getSource().getPlayer();
                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                            ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.reset")
                                    .withStyle(ChatFormatting.RED)); //Starting
                            if (variables.state != 0 && variables.state != 1) {
                                ModUtils.executeCommandAsEntity(player, "biomesOfCataclysms pause");
                            }else{
                                player.playNotifySound(
                                        SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                        SoundSource.MASTER,                 // categoria audio
                                        1F,                               // volume
                                        0.6F                                // pitch
                                );
                            }


                            return 0;
                        }))

                .then(Commands.literal("setMode")
                        .then(Commands.literal("classic")
                                .executes(arguments -> {
                                    ServerLevel world = arguments.getSource().getLevel();
                                    ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);
                                    if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
                                        return 0;
                                    }

                                    if (variables.mode == 1){
                                        variables.mode = 0;
                                        variables.syncData(world);
                                        MutableComponent modeToString = decodeModeToString(variables).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                                        ModUtils.sendChatMessage(world,
                                                Component.translatable("command.biomesofcataclysms.setModeTo", modeToString)
                                                        .withStyle(ChatFormatting.GREEN));
                                        for (ServerPlayer players : world.players()){
                                            players.playNotifySound(
                                                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                    SoundSource.MASTER,                 // categoria audio
                                                    1F,                               // volume
                                                    1.4F                                // pitch
                                            );
                                        }
                                    }else{
                                        MutableComponent modeToString = decodeModeToString(variables).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                                        ModUtils.sendChatMessage(world,
                                                Component.translatable("command.biomesofcataclysms.modeAlreadySet", modeToString)
                                                        .withStyle(ChatFormatting.YELLOW));
                                        Objects.requireNonNull(arguments.getSource().getPlayer()).playNotifySound(
                                                SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                SoundSource.MASTER,                 // categoria audio
                                                1F,                               // volume
                                                0.4F                                // pitch
                                        );
                                    }

                                    return 0;
                                })
                        )
                        .then(Commands.literal("apocalypse")
                                .executes(arguments -> {
                                    ServerLevel world = arguments.getSource().getLevel();
                                    ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                    if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
                                        return 0;
                                    }

                                    if (variables.mode == 0){
                                        variables.mode = 1;
                                        variables.syncData(world);
                                        MutableComponent modeToString = decodeModeToString(variables).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                                        ModUtils.sendChatMessage(world,
                                                Component.translatable("command.biomesofcataclysms.setModeTo", modeToString)
                                                        .withStyle(ChatFormatting.GREEN));
                                        for (ServerPlayer players : world.players()){
                                            players.playNotifySound(
                                                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                    SoundSource.MASTER,                 // categoria audio
                                                    1F,                               // volume
                                                    1.4F                                // pitch
                                            );
                                        }
                                    }else{
                                        MutableComponent modeToString = decodeModeToString(variables).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                                        ModUtils.sendChatMessage(world,
                                                Component.translatable("command.biomesofcataclysms.modeAlreadySet", modeToString)
                                                        .withStyle(ChatFormatting.YELLOW));
                                        Objects.requireNonNull(arguments.getSource().getPlayer()).playNotifySound(
                                                SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                SoundSource.MASTER,                 // categoria audio
                                                1F,                               // volume
                                                0.4F                                // pitch
                                        );
                                    }


                                    return 0;
                                })
                        )
                )

                .then(Commands.literal("timeSettings")
                        .then(Commands.literal("setTimer")
                                .then(Commands.argument("minutes", IntegerArgumentType.integer())
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                            if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
                                                return 0;
                                            }

                                            Integer minutes = arguments.getArgument("minutes", Integer.class);

                                            int maxBiomePerMinute = 1;
                                            if (variables.totalBiomes == 0){
                                                variables.generateFullBiomeList(world);
                                            }
                                            int minimumMinute = variables.totalBiomes / maxBiomePerMinute;
                                            MutableComponent minutesString = Component.literal(String.valueOf(minutes)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                                            Component minimumMinuteString = Component.literal(String.valueOf(minimumMinute)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                                            if (minutes < minimumMinute){
                                                ModUtils.sendLocalChatMessageTo(Objects.requireNonNull(arguments.getSource().getPlayer()),
                                                        Component.translatable("command.biomesofcataclysms.cannotSetTimer", minutesString,
                                                        minimumMinuteString)
                                                                .withStyle(ChatFormatting.YELLOW)); //Starting
                                                Objects.requireNonNull(arguments.getSource().getPlayer()).playNotifySound(
                                                        SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                        SoundSource.MASTER,                 // categoria audio
                                                        1F,                               // volume
                                                        0.4F                                // pitch
                                                );

                                                return 0;
                                            }

                                            variables.timer = minutes*60*20;

                                            String key = "command.biomesofcataclysms.minutes";


                                            minutesString = minutesString
                                                    .append(" ")
                                                    .append(Component.translatable(key)
                                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

                                            double biomePerMinutes = (double) (variables.timer / 20 / 60) /variables.totalBiomes;
                                            MutableComponent biomePerMinuteString;
                                            int biomePerMinutesMinutes = (int) biomePerMinutes;
                                            int biomePerMinutesSeconds = (int) Math.round((biomePerMinutes - biomePerMinutesMinutes) * 60);

                                            variables.tickDelayBetweenCataclysm = (biomePerMinutesMinutes*60*20) + (biomePerMinutesSeconds*20);
                                            variables.tickToNextCataclysm = variables.tickDelayBetweenCataclysm;
                                            variables.syncData(world);
                                            if (biomePerMinutes % 1 != 0) {
                                                biomePerMinuteString = Component.literal(String.valueOf(biomePerMinutesMinutes))
                                                        .append(" ")
                                                        .append(Component.translatable(key))
                                                        .append(" ")
                                                        .append(Component.translatable("command.biomesofcataclysms.secondsWithArgument", biomePerMinutesSeconds))
                                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                                            }else{
                                                biomePerMinuteString = Component.literal(String.valueOf(biomePerMinutesMinutes))
                                                        .append(" ")
                                                        .append(Component.translatable(key))
                                                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                                            }

                                            MutableComponent stringOf1 = Component.literal(String.valueOf(1))
                                                    .append(" ")
                                                    .append(Component.translatable("command.biomesofcataclysms.biome"))
                                                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                                            ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.setTimer", minutesString,
                                                            stringOf1, biomePerMinuteString)
                                                    .withStyle(ChatFormatting.GREEN));
                                            Objects.requireNonNull(arguments.getSource().getPlayer()).playNotifySound(
                                                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                    SoundSource.MASTER,                 // categoria audio
                                                    1F,                               // volume
                                                    0.9F                                // pitch
                                            );

                                            return 0;
                                        })
                                )
                                .then(Commands.literal("turnOff")
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                            if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
                                                return 0;
                                            }

                                            if (variables.timer == -1){
                                                ModUtils.sendLocalChatMessageTo(Objects.requireNonNull(arguments.getSource().getPlayer()),
                                                        Component.translatable("command.biomesofcataclysms.timerAlreadyOff")
                                                                .withStyle(ChatFormatting.YELLOW));
                                            }else{
                                                ModVariables.MapVariables defaultVariables = new ModVariables.MapVariables();
                                                variables.timer = -1;
                                                variables.tickDelayBetweenCataclysm = defaultVariables.tickDelayBetweenCataclysm;
                                                variables.tickToNextCataclysm = variables.tickDelayBetweenCataclysm;

                                                ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.timerRemoved")
                                                        .withStyle(ChatFormatting.GREEN));
                                                Objects.requireNonNull(arguments.getSource().getPlayer()).playNotifySound(
                                                        SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                        SoundSource.MASTER,                 // categoria audio
                                                        1F,                               // volume
                                                        0.9F                                // pitch
                                                );
                                                variables.syncData(world);
                                            }

                                            return 0;
                                        })
                                )
                        )
                        .then(Commands.literal("setDelayBetweenCataclysms")
                                .then(Commands.argument("minutes", IntegerArgumentType.integer())
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                            if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
                                                return 0;
                                            }

                                            Integer minutes = arguments.getArgument("minutes", Integer.class);
                                            
                                            int minimumMinute = 1;
                                            Component minutesString = Component.literal(String.valueOf(minutes)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
                                            Component minimumMinuteString = Component.literal(String.valueOf(minimumMinute)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                                            if (minutes < minimumMinute){
                                                ModUtils.sendLocalChatMessageTo(Objects.requireNonNull(arguments.getSource().getPlayer()),
                                                        Component.translatable("command.biomesofcataclysms.cannotSetDelay", minutesString,
                                                                        minimumMinuteString)
                                                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)); //Starting
                                                Objects.requireNonNull(arguments.getSource().getPlayer()).playNotifySound(
                                                        SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                        SoundSource.MASTER,                 // categoria audio
                                                        1F,                               // volume
                                                        0.4F                                // pitch
                                                );

                                                return 0;
                                            }

                                            if (variables.timer != -1){
                                                ModUtils.executeCommandAsEntity(arguments.getSource().getPlayer(), "timeSettings setTimer turnOff");
                                            }

                                            variables.tickDelayBetweenCataclysm = minutes*60*20;
                                            variables.tickToNextCataclysm = variables.tickDelayBetweenCataclysm;
                                            variables.syncData(world);

                                            ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.setDelayBetweenCataclysms",
                                                    minutesString).withStyle(ChatFormatting.BOLD, ChatFormatting.GREEN)); //Starting
                                            Objects.requireNonNull(arguments.getSource().getPlayer()).playNotifySound(
                                                    SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                    SoundSource.MASTER,                 // categoria audio
                                                    1F,                               // volume
                                                    0.9F                                // pitch
                                            );

                                            return 0;
                                        })
                                )
                        )
                )

                .then(Commands.literal("setDifficulty")
                        .then(Commands.literal("extremelyEasy")
                                .executes(arguments -> {
                                    ServerLevel world = arguments.getSource().getLevel();
                                    ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                    if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
                                        return 0;
                                    }

                                    setDifficultyMsg(0, arguments.getSource().getServer(), world);

                                    variables.difficulty = 0;
                                    variables.syncData(world);

                                    return 0;
                                })
                        )
                        .then(Commands.literal("easy")
                                .executes(arguments -> {
                                    ServerLevel world = arguments.getSource().getLevel();
                                    ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                    if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
                                        return 0;
                                    }

                                    setDifficultyMsg(1, arguments.getSource().getServer(), world);

                                    variables.difficulty = 1;
                                    variables.syncData(world);


                                    return 0;
                                })
                        )
                        .then(Commands.literal("hard")
                                .executes(arguments -> {
                                    ServerLevel world = arguments.getSource().getLevel();
                                    ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                    if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
                                        return 0;
                                    }

                                    setDifficultyMsg(2, arguments.getSource().getServer(), world);

                                    variables.difficulty = 2;
                                    variables.syncData(world);


                                    return 0;
                                })
                        )
                        .then(Commands.literal("impossible")
                                .executes(arguments -> {
                                    ServerLevel world = arguments.getSource().getLevel();
                                    ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                    if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
                                        return 0;
                                    }

                                    setDifficultyMsg(3, arguments.getSource().getServer(), world);

                                    variables.difficulty = 3;
                                    variables.syncData(world);


                                    return 0;
                                })
                        )
                        .then(Commands.literal("hardcore")
                                .executes(arguments -> {
                                    ServerLevel world = arguments.getSource().getLevel();
                                    ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                    if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
                                        return 0;
                                    }

                                    setDifficultyMsg(4, arguments.getSource().getServer(), world);

                                    variables.difficulty = 4;
                                    variables.syncData(world);


                                    return 0;
                                })
                        )
                )
                .then(Commands.literal("optimization")
                        .then(Commands.literal("pcPower")
                                .then(Commands.literal("Max")
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                            setPcPowerMsg(5, arguments.getSource().getServer(), world);

                                            variables.pcPower = 5;
                                            variables.syncData(world);

                                            settingUpPcPowerComponents(variables, world, arguments.getSource().getServer());
                                            return 0;
                                        })
                                )
                                .then(Commands.literal("Extreme")
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                            setPcPowerMsg(4, arguments.getSource().getServer(), world);

                                            variables.pcPower = 4;
                                            variables.syncData(world);

                                            settingUpPcPowerComponents(variables, world, arguments.getSource().getServer());
                                            return 0;
                                        })
                                )
                                .then(Commands.literal("High")
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                            setPcPowerMsg(3, arguments.getSource().getServer(), world);

                                            variables.pcPower = 3;
                                            variables.syncData(world);

                                            settingUpPcPowerComponents(variables, world, arguments.getSource().getServer());
                                            return 0;
                                        })
                                )
                                .then(Commands.literal("Medium")
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                            setPcPowerMsg(2, arguments.getSource().getServer(), world);

                                            variables.pcPower = 2;
                                            variables.syncData(world);

                                            settingUpPcPowerComponents(variables, world, arguments.getSource().getServer());
                                            return 0;
                                        })
                                )
                                .then(Commands.literal("Low")
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                            setPcPowerMsg(1, arguments.getSource().getServer(), world);

                                            variables.pcPower = 1;
                                            variables.syncData(world);

                                            settingUpPcPowerComponents(variables, world, arguments.getSource().getServer());
                                            return 0;
                                        })
                                )
                                .then(Commands.literal("Potato")
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

                                            setPcPowerMsg(0, arguments.getSource().getServer(), world);

                                            variables.pcPower = 0;
                                            variables.syncData(world);

                                            settingUpPcPowerComponents(variables, world, arguments.getSource().getServer());
                                            return 0;
                                        })
                                )
                        )
                )
                .then(Commands.literal("dataRecovery")
                        .requires(source -> false)
                        .executes(arguments -> {
                            ModUtils.sendLocalChatMessageTo(Objects.requireNonNull(arguments.getSource().getPlayer()), ModUtils.buildUnknowErrorMessage());
                            return 0;
                        })
                )
                .then(Commands.literal("devsOnly")
                        .then(Commands.literal("debugMode")
                                .then(Commands.argument("enable", BoolArgumentType.bool())
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            boolean enabled = BoolArgumentType.getBool(arguments, "enable");
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);
                                            variables.debugMode = enabled;
                                            variables.syncData(world);
                                            if (enabled){
                                                ModUtils.sendLocalChatMessageTo(Objects.requireNonNull(arguments.getSource().getPlayer()),
                                                        ModUtils.buildWarningMessage(true, Component.literal("Debug"),
                                                                Component.translatable("warning.biomesofcataclysms.setDebugMode")));
                                                ModUtils.sendChatMessage(world, ModUtils.buildWarningMessage(false, Component.literal("Debug"),
                                                                Component.translatable("warning.biomesofcataclysms.debugModeEnabled")));

                                            }else{
                                                ModUtils.sendChatMessage(world, ModUtils.buildWarningMessage(false, Component.literal("Debug"),
                                                        Component.translatable("warning.biomesofcataclysms.debugModeDisabled")));
                                            }

                                            return 0;
                                        })
                                )
                        )
                        .then(Commands.literal("forceNextBiome")
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);
                                            if (variables.debugMode){
                                                if (variables.state == 2){
                                                    ModUtils.sendChatMessage(world, ModUtils.buildWarningMessage(false, Component.literal("Debug"),
                                                            Component.translatable("warning.biomesofcataclysms.forceNextBiomeCommand1")));
                                                    variables.tickToNextCataclysm = 20;
                                                    variables.syncData(world);
                                                }else{
                                                    ModUtils.sendChatMessage(world, ModUtils.buildWarningMessage(false, Component.literal("Debug"),
                                                            Component.translatable("command.biomesofcataclysms.aGameMustBeRunning")));
                                                }

                                            }else{
                                                ModUtils.sendLocalChatMessageTo(Objects.requireNonNull(arguments.getSource().getPlayer()),
                                                        ModUtils.buildErrorMessage(false, 8, Component.literal("forceNextBiome[C]"),
                                                        Component.translatable("error.biomesofcataclysms.error8")));
                                            }

                                            return 0;
                                        })
                        )
                        .then(Commands.literal("forceTimerTo0")
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);
                                            if (variables.debugMode){
                                                if (variables.state == 2){
                                                    variables.timer = 0;
                                                    ModUtils.sendChatMessage(world, ModUtils.buildWarningMessage(false, Component.literal("Debug"),
                                                            Component.translatable("warning.biomesofcataclysms.forceTimerTo0")));
                                                    variables.syncData(world);
                                                }else{
                                                    ModUtils.sendChatMessage(world, ModUtils.buildWarningMessage(false, Component.literal("Debug"),
                                                            Component.translatable("command.biomesofcataclysms.aGameMustBeRunning")));
                                                }

                                            }else{
                                                ModUtils.sendLocalChatMessageTo(Objects.requireNonNull(arguments.getSource().getPlayer()),
                                                        ModUtils.buildErrorMessage(false, 8, Component.literal("forceNextBiome[C]"),
                                                        Component.translatable("error.biomesofcataclysms.error8")));
                                            }

                                            return 0;
                                        })
                        )
                        .then(Commands.literal("forceApocalypseTo")
                                .then(Commands.literal("shuffle")
                                        .executes(arguments -> {
                                            ServerLevel world = arguments.getSource().getLevel();
                                            ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);
                                            if (variables.debugMode) {
                                                if (variables.state == 2) {
                                                    settingUpApocalypseMode(variables, world);
                                                }
                                            }
                                            return 0;
                                        })
                                )
                        )
                )
                .then(Commands.literal("game")
                        .then(Commands.literal("setCataclysm")
                            .then(Commands.literal("flood")
                                .executes(arguments -> {
                                    ServerLevel world = arguments.getSource().getLevel();
                                    ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);
                                    ServerPlayer player = arguments.getSource().getPlayer();
                                    assert player != null;
                                    if (variables.mode != 1){
                                        ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.modeMustBeSetToApocalypse")
                                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                                        player.playNotifySound(
                                                SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                SoundSource.MASTER,                 // categoria audio
                                                1F,                               // volume
                                                0.4F                                // pitch
                                        );
                                        return 0;
                                    }
                                    if (variables.state < 2){
                                        ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.aGameMustBeRunning")
                                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                                        player.playNotifySound(
                                                SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                SoundSource.MASTER,                 // categoria audio
                                                1F,                               // volume
                                                0.4F                                // pitch
                                        );
                                        return 0;
                                    } //Check
                                    if (!variables.deletedBiomes.isEmpty()){
                                        ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.cannotChangeCataclusm")
                                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                                        player.playNotifySound(
                                                SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                SoundSource.MASTER,                 // categoria audio
                                                1F,                               // volume
                                                0.4F                                // pitch
                                        );
                                        return 0;

                                    } //Check

                                    variables.cataclysm = ModUtils.decodeCataclysmFromEnum(AllCataclysms.FLOODED);
                                    variables.syncData(world);
                                    MutableComponent cataclysm = Component.literal(variables.cataclysm)
                                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                                    ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.setCataclysmTo", cataclysm)
                                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
                                    for (ServerPlayer serverPlayer : arguments.getSource().getServer().getPlayerList().getPlayers()) {
                                        serverPlayer.playNotifySound(
                                                SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                SoundSource.MASTER,                 // categoria audio
                                                1F,                               // volume
                                                0.9F                                // pitch
                                        );
                                    }
                                    return 0;
                                })
                            )
                            .then(Commands.literal("solar_storm")
                                .executes(arguments -> {
                                    ServerLevel world = arguments.getSource().getLevel();
                                    ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);
                                    ServerPlayer player = arguments.getSource().getPlayer();
                                    assert player != null;
                                    if (variables.state < 2){
                                        ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.aGameMustBeRunning")
                                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                                        player.playNotifySound(
                                                SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                SoundSource.MASTER,                 // categoria audio
                                                1F,                               // volume
                                                0.4F                                // pitch
                                        );
                                        return 0;
                                    } //Check
                                    if (!variables.deletedBiomes.isEmpty()){
                                        ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.cannotChangeCataclusm")
                                                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                                        player.playNotifySound(
                                                SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                SoundSource.MASTER,                 // categoria audio
                                                1F,                               // volume
                                                0.4F                                // pitch
                                        );
                                        return 0;

                                    } //Check

                                    variables.cataclysm = ModUtils.decodeCataclysmFromEnum(AllCataclysms.SUN_BURNT);
                                    variables.syncData(world);
                                    MutableComponent cataclysm = Component.literal(variables.cataclysm)
                                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

                                    ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.setCataclysmTo", cataclysm)
                                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Starting
                                    for (ServerPlayer serverPlayer : arguments.getSource().getServer().getPlayerList().getPlayers()) {
                                        serverPlayer.playNotifySound(
                                                SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                                                SoundSource.MASTER,                 // categoria audio
                                                1F,                               // volume
                                                0.9F                                // pitch
                                        );
                                    }
                                    return 0;
                                })
                            )
                        )
                )
        );
    }

    public static class DataRecovery{
        public static ModVariables.MapVariables variables;
        public static ServerLevel serverLevel;

        public DataRecovery(ModVariables.MapVariables variables, ServerLevel serverLevel){
            DataRecovery.variables = variables;
            DataRecovery.serverLevel = serverLevel;
        }

        protected static void analyzeCode(){
            MutableComponent infoMsg = ModUtils.buildWarningMessage(false, Component.literal("DataRecovery.analyzeCode"), Component.literal(""));
            ModUtils.sendChatMessage(serverLevel, infoMsg);

            if (variables.shuffledBiomes.isEmpty()){
                ModUtils.sendChatMessage(serverLevel, ModUtils.buildErrorMessage(true, 5,  Component.literal("shuffledBiomes"),
                        Component.translatable("error.biomesofcataclysms.error5")));
                variables.dataCondition = 1;
                return;
            }

            if (variables.mode == 0){
                ModUtils.sendChatMessage(serverLevel, ModUtils.buildWarningMessage(false, Component.literal("mode"),
                        Component.translatable("warning.biomesofcataclysms.mode0")));
                variables.dataCondition = 1;
            } else if (variables.mode == 1) {
                ModUtils.sendChatMessage(serverLevel, ModUtils.buildWarningMessage(false, Component.literal("mode"),
                        Component.translatable("warning.biomesofcataclysms.mode1")));
                variables.dataCondition = 1;
            }else{
                ModUtils.sendChatMessage(serverLevel, ModUtils.buildErrorMessage(true, 6,  Component.literal("mode"),
                        Component.translatable("error.biomesofcataclysms.error6")));
                variables.dataCondition = 1;
                return;
            }

            if (variables.deletedBiomes.isEmpty()) {
                ModUtils.sendChatMessage(serverLevel, ModUtils.buildWarningMessage(true, Component.literal("deletedBiomes"),
                        Component.translatable("warning.biomesofcataclysms.deletedBiomes")));
                variables.dataCondition = 1;
            }

            if (variables.state == 2){
                ModUtils.sendChatMessage(serverLevel, ModUtils.buildWarningMessage(false, Component.literal("state"),
                        Component.translatable("warning.biomesofcataclysms.state")));
                variables.dataCondition = 1;
            } else if (variables.state == 0) {
                ModUtils.sendChatMessage(serverLevel, ModUtils.buildWarningMessage(true, Component.literal("state"),
                        Component.translatable("warning.biomesofcataclysms.stateCritical")));
                variables.dataCondition = 1;
            }


            //To finish

        }

        protected static void attemptDataRecovery(){
            if (variables.state != 1){
                variables.state = 1;
            }
            if (variables.shuffledBiomes.isEmpty()){
                variables.generateFullBiomeList(serverLevel);
                variables.shuffledBiomes.removeIf(variables.deletedBiomes::contains);
                variables.syncData(serverLevel);
                ModUtils.sendChatMessage(serverLevel, ModUtils.buildWarningMessage(false, Component.literal("attemptDataRecovery"),
                        Component.translatable("warning.biomesofcataclysms.shuffledBiomesRecovered", variables.shuffledBiomes, variables.deletedBiomes)));
            }

            if (variables.mode == 0){
                ModUtils.sendChatMessage(serverLevel, ModUtils.buildWarningMessage(false, Component.literal("attemptDataRecovery"),
                        Component.translatable("warning.biomesofcataclysms.mode0")));
            } else if (variables.mode == 1) {
                ModUtils.sendChatMessage(serverLevel, ModUtils.buildWarningMessage(false, Component.literal("attemptDataRecovery"),
                        Component.translatable("warning.biomesofcataclysms.mode1")));
            }else{
                ModUtils.sendChatMessage(serverLevel, ModUtils.buildWarningMessage(true, Component.literal("attemptDataRecovery"),
                        Component.translatable("warning.biomesofcataclysms.modeOuOfBound")));
            }

            //To finish
        }

    }
}

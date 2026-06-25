package net.calca.biomesofcataclysms.command.common;


import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.calca.biomesofcataclysms.data.cataclysm.AllCataclysms;
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

import static net.calca.biomesofcataclysms.ModUtils.decodeCataclysmFromEnum;

public class ModCommandsCommon {
    protected static final String PREFIX = "biomesOfCataclysms";
    protected static final int PERMISION_LEVEL = 4;

    protected static void gameAboutToStartSettingsMessage(LevelAccessor levelAccessor, ServerPlayer serverPlayer, PersistentData.MapVariables variables){
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
    protected static String decodePcPowerToString(PersistentData.MapVariables variables){
        MutableComponent returnValue;
        if (variables.pcPower >= 0 && variables.pcPower <= 5){
            returnValue = Component.translatable("command.biomesofcataclysms.pcPower." + variables.pcPower);
        }else{
            returnValue = ModUtils.buildErrorMessage(false,9, Component.literal("decodePcPowerToString"),
                    Component.translatable("error.biomesofcataclysms.error9"));
        }
        return returnValue.getString();
    }

    protected static MutableComponent decodeModeToString(PersistentData.MapVariables variables){
        if (variables.mode == 0){
            return Component.translatable("command.biomesofcataclysms.classicMode").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        }else if (variables.mode == 1){
            return Component.translatable("command.biomesofcataclysms.apocalypseMode").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        }else{
            return Component.literal("Unknow Error: Mode = Null").withStyle(ChatFormatting.DARK_RED);
        }
    }
    protected static boolean changeSettingsCheck(PersistentData.MapVariables variables, Player player){
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
    protected static void dataRecoveryCommand(Player player) {
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

    protected static void setDifficultyMsg(int difficulty, MinecraftServer server, ServerLevel serverLevel){
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
    protected static void setPcPowerMsg(int pcPower, MinecraftServer server, ServerLevel serverLevel){
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

    protected static void settingUpPcPowerComponents(PersistentData.MapVariables variables, ServerLevel world, MinecraftServer server){
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
        variables.syncData(world, true, false);
        Minecraft.getInstance().options.renderDistance().set(variables.radius);
        server.getPlayerList().setViewDistance(variables.radius);
    }
    protected static void settingUpApocalypseMode(PersistentData.MapVariables variables, ServerLevel level){
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



    public static class DataRecovery{
        public static PersistentData.MapVariables variables;
        public static ServerLevel serverLevel;

        public DataRecovery(PersistentData.MapVariables variables, ServerLevel serverLevel){
            DataRecovery.variables = variables;
            DataRecovery.serverLevel = serverLevel;
        }

        protected static void analyzeCode(){
            MutableComponent infoMsg = ModUtils.buildWarningMessage(false, Component.literal("DataRecovery.analyzeCode"), Component.literal(""));
            ModUtils.sendChatMessage(serverLevel, infoMsg);

            if (variables.shuffledBiomes.isEmpty()){
                ModUtils.sendChatMessage(serverLevel, ModUtils.buildErrorMessage(true, 5,  Component.literal("shuffledBiomes[R]"),
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
                variables.syncData(serverLevel, true, false);
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

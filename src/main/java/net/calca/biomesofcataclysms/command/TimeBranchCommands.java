package net.calca.biomesofcataclysms.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.bar.ProgressBarManager;
import net.calca.biomesofcataclysms.command.common.ModCommandsCommon;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Objects;

@EventBusSubscriber
public class TimeBranchCommands extends ModCommandsCommon {
    protected static final String COMMAND_5 = "setTimer";
    protected static final String COMMAND_6 = "setDelayBetweenCataclysms";

    private static int setTimerMinutes(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);

        if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())) {
            return 0;
        }

        Integer minutes = arguments.getArgument("minutes", Integer.class);

        int maxBiomePerMinute = 1;
        if (variables.totalBiomes == 0) {
            variables.generateFullBiomeList(world);
        }
        int minimumMinute = variables.totalBiomes / maxBiomePerMinute;
        MutableComponent minutesString = Component.literal(String.valueOf(minutes)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        Component minimumMinuteString = Component.literal(String.valueOf(minimumMinute)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        if (minutes < minimumMinute) {
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

        variables.timer = minutes * 60 * 20;

        String key = "command.biomesofcataclysms.minutes";


        minutesString = minutesString
                .append(" ")
                .append(Component.translatable(key)
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        double biomePerMinutes = (double) (variables.timer / 20 / 60) / variables.totalBiomes;
        MutableComponent biomePerMinuteString;
        int biomePerMinutesMinutes = (int) biomePerMinutes;
        int biomePerMinutesSeconds = (int) Math.round((biomePerMinutes - biomePerMinutesMinutes) * 60);

        variables.tickDelayBetweenCataclysm = (biomePerMinutesMinutes * 60 * 20) + (biomePerMinutesSeconds * 20);
        variables.tickToNextCataclysm = variables.tickDelayBetweenCataclysm;
        variables.syncData(world, true, false);
        if (biomePerMinutes % 1 != 0) {
            biomePerMinuteString = Component.literal(String.valueOf(biomePerMinutesMinutes))
                    .append(" ")
                    .append(Component.translatable(key))
                    .append(" ")
                    .append(Component.translatable("command.biomesofcataclysms.secondsWithArgument", biomePerMinutesSeconds))
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        } else {
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
        ProgressBarManager.TimerProgressBar.initialize(variables, world);

        return 0;

    }
    private static int setTimerOff(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);

        if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())) {
            return 0;
        }

        if (variables.timer == -1) {
            ModUtils.sendLocalChatMessageTo(Objects.requireNonNull(arguments.getSource().getPlayer()),
                    Component.translatable("command.biomesofcataclysms.timerAlreadyOff")
                            .withStyle(ChatFormatting.YELLOW));
        } else {
            PersistentData.MapVariables defaultVariables = new PersistentData.MapVariables();
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
            variables.syncData(world, true, false);

            ProgressBarManager.TimerProgressBar.TIMER_PROGRESS_BAR.removeForAllPlayers();
        }

        return 0;

    }
    private static int setDelayBetweenCataclysmsMinutes(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);

        if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())) {
            return 0;
        }

        Integer minutes = arguments.getArgument("minutes", Integer.class);

        int minimumMinute = 1;
        Component minutesString = Component.literal(String.valueOf(minutes)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        Component minimumMinuteString = Component.literal(String.valueOf(minimumMinute)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        if (minutes < minimumMinute) {
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

        if (variables.timer != -1) {
            ModUtils.executeCommandAsEntity(arguments.getSource().getPlayer(), "timeSettings setTimer turnOff");
        }

        variables.tickDelayBetweenCataclysm = minutes * 60 * 20;
        variables.tickToNextCataclysm = variables.tickDelayBetweenCataclysm;
        variables.syncData(world, true, false);

        ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.setDelayBetweenCataclysms",
                minutesString).withStyle(ChatFormatting.BOLD, ChatFormatting.GREEN)); //Starting
        Objects.requireNonNull(arguments.getSource().getPlayer()).playNotifySound(
                SoundEvents.NOTE_BLOCK_PLING.value(), // suono
                SoundSource.MASTER,                 // categoria audio
                1F,                               // volume
                0.9F                                // pitch
        );

        return 0;
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {//OP players
        event.getDispatcher().register(Commands.literal(PREFIX).requires(s -> s.hasPermission(PERMISION_LEVEL))
                .then(Commands.literal("timeSettings")
                        .then(Commands.literal(COMMAND_5)
                                .then(Commands.argument("minutes", IntegerArgumentType.integer()) //parameter
                                        .executes(TimeBranchCommands::setTimerMinutes)
                                )
                                .then(Commands.literal("turnOff")
                                        .executes(TimeBranchCommands::setTimerOff)
                                )
                        )
                        .then(Commands.literal(COMMAND_6)
                                .then(Commands.argument("minutes", IntegerArgumentType.integer())
                                        .executes(TimeBranchCommands::setDelayBetweenCataclysmsMinutes)
                                )
                        )
                )
        );
    }
}

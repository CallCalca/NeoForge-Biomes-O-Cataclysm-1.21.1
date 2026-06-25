package net.calca.biomesofcataclysms.command;

import com.mojang.brigadier.context.CommandContext;
import net.calca.biomesofcataclysms.bar.ProgressBarManager;
import net.calca.biomesofcataclysms.command.common.ModCommandsCommon;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber
public class DifficultyBranchCommands extends ModCommandsCommon {
    protected static final String COMMAND_7 = "extremelyEasy";
    protected static final String COMMAND_8 = "easy";
    protected static final String COMMAND_9 = "hard";
    protected static final String COMMAND_10 = "impossible";
    protected static final String COMMAND_11 = "hardcore";

    private static int setDifficultyExEasy(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);

        if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
            return 0;
        }

        setDifficultyMsg(0, arguments.getSource().getServer(), world);

        variables.difficulty = 0;
        variables.syncData(world, true, false);

        ProgressBarManager.TimerProgressBar.setTitle(variables);

        return 0;

    }
    private static int setDifficultyEasy(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);

        if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
            return 0;
        }

        setDifficultyMsg(1, arguments.getSource().getServer(), world);

        variables.difficulty = 1;
        variables.syncData(world, true, false);

        ProgressBarManager.TimerProgressBar.setTitle(variables);

        return 0;

    }
    private static int setDifficultyHard(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);

        if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
            return 0;
        }

        setDifficultyMsg(2, arguments.getSource().getServer(), world);

        variables.difficulty = 2;
        variables.syncData(world, true, false);

        ProgressBarManager.TimerProgressBar.setTitle(variables);

        return 0;

    }
    private static int setDifficultyImpossible(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);

        if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
            return 0;
        }

        setDifficultyMsg(3, arguments.getSource().getServer(), world);

        variables.difficulty = 3;
        variables.syncData(world, true, false);

        ProgressBarManager.TimerProgressBar.setTitle(variables);

        return 0;

    }
    private static int setDifficultyHardcore(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);

        if (!changeSettingsCheck(variables, arguments.getSource().getPlayer())){
            return 0;
        }

        setDifficultyMsg(4, arguments.getSource().getServer(), world);

        variables.difficulty = 4;
        variables.syncData(world, true, false);

        ProgressBarManager.TimerProgressBar.setTitle(variables);

        return 0;

    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {//OP players
        event.getDispatcher().register(Commands.literal(PREFIX).requires(s -> s.hasPermission(PERMISION_LEVEL))
                .then(Commands.literal("setDifficulty")
                        .then(Commands.literal(COMMAND_7) //exEasy
                                .executes(DifficultyBranchCommands::setDifficultyExEasy)
                        )
                        .then(Commands.literal(COMMAND_8) //easy
                                .executes(DifficultyBranchCommands::setDifficultyEasy)
                        )
                        .then(Commands.literal(COMMAND_9) //hard
                                .executes(DifficultyBranchCommands::setDifficultyHard)
                        )
                        .then(Commands.literal(COMMAND_10) //impossible
                                .executes(DifficultyBranchCommands::setDifficultyImpossible)
                        )
                        .then(Commands.literal(COMMAND_11) //hardcore
                                .executes(DifficultyBranchCommands::setDifficultyHardcore)
                        )
                )
        );
    }
}

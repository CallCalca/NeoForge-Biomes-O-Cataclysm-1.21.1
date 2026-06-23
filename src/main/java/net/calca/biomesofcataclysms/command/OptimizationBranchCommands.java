
package net.calca.biomesofcataclysms.command;

import com.mojang.brigadier.context.CommandContext;
import net.calca.biomesofcataclysms.command.common.ModCommandsCommon;
import net.calca.biomesofcataclysms.data.ModVariables;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber
public class OptimizationBranchCommands extends ModCommandsCommon {
    protected static final String COMMAND_12 = "Max";
    protected static final String COMMAND_13 = "Extreme";
    protected static final String COMMAND_14 = "High";
    protected static final String COMMAND_15 = "Medium";
    protected static final String COMMAND_16 = "Low";
    protected static final String COMMAND_17 = "Potato";

    private static int pcPowerMax(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

        setPcPowerMsg(5, arguments.getSource().getServer(), world);

        variables.pcPower = 5;
        variables.syncData(world, true, false);

        settingUpPcPowerComponents(variables, world, arguments.getSource().getServer());

        return 0;

    }
    private static int pcPowerExtreme(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

        setPcPowerMsg(4, arguments.getSource().getServer(), world);

        variables.pcPower = 4;
        variables.syncData(world, true, false);

        settingUpPcPowerComponents(variables, world, arguments.getSource().getServer());

        return 0;

    }
    private static int pcPowerHigh(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

        setPcPowerMsg(3, arguments.getSource().getServer(), world);

        variables.pcPower = 3;
        variables.syncData(world, true, false);

        settingUpPcPowerComponents(variables, world, arguments.getSource().getServer());

        return 0;

    }
    private static int pcPowerMedium(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

        setPcPowerMsg(2, arguments.getSource().getServer(), world);

        variables.pcPower = 2;
        variables.syncData(world, true, false);

        settingUpPcPowerComponents(variables, world, arguments.getSource().getServer());

        return 0;

    }
    private static int pcPowerLow(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

        setPcPowerMsg(1, arguments.getSource().getServer(), world);

        variables.pcPower = 1;
        variables.syncData(world, true, false);

        settingUpPcPowerComponents(variables, world, arguments.getSource().getServer());

        return 0;

    }
    private static int pcPowerPotato(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        ModVariables.MapVariables variables = ModVariables.MapVariables.get(world);

        setPcPowerMsg(0, arguments.getSource().getServer(), world);

        variables.pcPower = 0;
        variables.syncData(world, true, false);

        settingUpPcPowerComponents(variables, world, arguments.getSource().getServer());

        return 0;

    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {//OP players
        event.getDispatcher().register(Commands.literal(PREFIX).requires(s -> s.hasPermission(PERMISION_LEVEL))
                .then(Commands.literal("optimization")
                        .then(Commands.literal("pcPower")
                                .then(Commands.literal(COMMAND_12) //Max
                                        .executes(OptimizationBranchCommands::pcPowerMax)
                                )
                                .then(Commands.literal(COMMAND_13) //Extreme
                                        .executes(OptimizationBranchCommands::pcPowerExtreme)
                                )
                                .then(Commands.literal(COMMAND_14) //High
                                        .executes(OptimizationBranchCommands::pcPowerHigh)
                                )
                                .then(Commands.literal(COMMAND_15) //Medium
                                        .executes(OptimizationBranchCommands::pcPowerMedium)
                                )
                                .then(Commands.literal(COMMAND_16) //Low
                                        .executes(OptimizationBranchCommands::pcPowerLow)
                                )
                                .then(Commands.literal(COMMAND_17) //Potato
                                        .executes(OptimizationBranchCommands::pcPowerPotato)
                                )
                        )
                )
        );
    }
}

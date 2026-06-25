package net.calca.biomesofcataclysms.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.bar.ProgressBarManager;
import net.calca.biomesofcataclysms.command.common.ModCommandsCommon;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Objects;

@EventBusSubscriber
public class DebugBranchCommands extends ModCommandsCommon {
    protected static final String DEBUG = "Debug";
    protected static final String COMMAND_18 = "debugMode";
    protected static final String COMMAND_19 = "forceNextBiome";
    protected static final String COMMAND_20 = "forceTimerTo0";
    protected static final String COMMAND_21 = "forceApocalypseTo";

    private static int debugMode(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        boolean enabled = BoolArgumentType.getBool(arguments, "enable");
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);
        variables.debugMode = enabled;
        variables.syncData(world, true, false);
        if (enabled){
            ModUtils.sendLocalChatMessageTo(Objects.requireNonNull(arguments.getSource().getPlayer()),
                    ModUtils.buildWarningMessage(true, Component.literal(COMMAND_18 + "[C]"),
                            Component.translatable("warning.biomesofcataclysms.setDebugMode")));
            ModUtils.sendChatMessage(world, ModUtils.buildWarningMessage(false, Component.literal(COMMAND_18 + "[C]"),
                    Component.translatable("warning.biomesofcataclysms.debugModeEnabled")));

        }else{
            ModUtils.sendChatMessage(world, ModUtils.buildWarningMessage(false, Component.literal(COMMAND_18+ "[C]"),
                    Component.translatable("warning.biomesofcataclysms.debugModeDisabled")));
        }

        return 0;

    }
    private static int forceNextBiome(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);
        if (variables.debugMode){
            if (variables.state == 2){
                ModUtils.sendChatMessage(world, ModUtils.buildWarningMessage(false, Component.literal(DEBUG),
                        Component.translatable("warning.biomesofcataclysms.forceNextBiomeCommand1")));
                variables.tickToNextCataclysm = 20;
                variables.syncData(world, true, false);
            }else{
                ModUtils.sendChatMessage(world, ModUtils.buildWarningMessage(false, Component.literal(DEBUG),
                        Component.translatable("command.biomesofcataclysms.aGameMustBeRunning")));
            }

        }else{
            ModUtils.sendLocalChatMessageTo(Objects.requireNonNull(arguments.getSource().getPlayer()),
                    ModUtils.buildWarningMessage(false, Component.literal(COMMAND_19 + "[C]"),
                            Component.translatable("warning.biomesofcataclysms.debugModeMustBeActive")));
        }

        return 0;

    }
    private static int forceTimerTo0(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);
        if (variables.debugMode){
            if (variables.state == 2){
                variables.timer = 0;
                ProgressBarManager.TimerProgressBar.tick(variables);
                ProgressBarManager.TimerProgressBar.TIMER_PROGRESS_BAR.setColor(BossEvent.BossBarColor.RED);
                ModUtils.sendChatMessage(world, ModUtils.buildWarningMessage(false, Component.literal(DEBUG),
                        Component.translatable("warning.biomesofcataclysms.forceTimerTo0")));
                variables.syncData(world, true, false);
            }else{
                ModUtils.sendChatMessage(world, ModUtils.buildWarningMessage(false, Component.literal(DEBUG),
                        Component.translatable("command.biomesofcataclysms.aGameMustBeRunning")));
            }

        }else{
            ModUtils.sendLocalChatMessageTo(Objects.requireNonNull(arguments.getSource().getPlayer()),
                    ModUtils.buildWarningMessage(false, Component.literal(COMMAND_20 + "[C]"),
                            Component.translatable("warning.biomesofcataclysms.debugModeMustBeActive")));
        }

        return 0;

    }
    private static int forceApocalypseToShuffle(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);
        if (variables.debugMode) {
            if (variables.state == 2) {
                settingUpApocalypseMode(variables, world);
            }
        }

        return 0;

    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {//OP players
        event.getDispatcher().register(Commands.literal(PREFIX).requires(s -> s.hasPermission(PERMISION_LEVEL))
                .then(Commands.literal("devsOnly")
                        .then(Commands.literal(COMMAND_18) //debugMode
                                .then(Commands.argument("enable", BoolArgumentType.bool())
                                        .executes(DebugBranchCommands::debugMode)
                                )
                        )
                        .then(Commands.literal(COMMAND_19) // forceNextBiome
                                .executes(DebugBranchCommands::forceNextBiome)
                        )
                        .then(Commands.literal(COMMAND_20) //forceTimerTo0
                                .executes(DebugBranchCommands::forceTimerTo0)
                        )
                        .then(Commands.literal(COMMAND_21) //forceApocalypseTo
                                .then(Commands.literal("shuffle")
                                        .executes(DebugBranchCommands::forceApocalypseToShuffle)
                                )
                        )
                )
        );
    }
}

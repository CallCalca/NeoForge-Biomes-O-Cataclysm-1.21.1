package net.calca.biomesofcataclysms.command;

import com.mojang.brigadier.context.CommandContext;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.command.common.ModCommandsCommon;
import net.calca.biomesofcataclysms.data.server.PersistentData;
import net.calca.biomesofcataclysms.data.server.RuntimeData;
import net.calca.biomesofcataclysms.cataclysm.AllCataclysms;
import net.calca.biomesofcataclysms.data.server.chunk.ChunkInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

import static net.calca.biomesofcataclysms.ModUtils.decodeCataclysmFromEnum;
import static net.calca.biomesofcataclysms.data.server.DataSync.copyState;

@EventBusSubscriber
public class SingleCommands extends ModCommandsCommon {
    protected static final String COMMAND_0 = "startOrResume";
    protected static final String COMMAND_1 = "pause";
    protected static final String COMMAND_2 = "reset";

    private static int startOrResume(CommandContext<CommandSourceStack> arguments){
        ServerPlayer serverPlayer = arguments.getSource().getPlayer();
        assert serverPlayer != null;
        MinecraftServer server = arguments.getSource().getServer();
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);

        if (variables.tickToNextCataclysm == variables.tickDelayBetweenCataclysm && variables.biomesAffected == 0){
            if (variables.state == 0){
                variables.state = 1;
                gameAboutToStartSettingsMessage(world, serverPlayer, variables);
                ModUtils.playGlobalNoteBlockSound(0.8F);
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
                ModUtils.playGlobalNoteBlockSound(1.2F);
            }
        }else if (variables.state == 1){
            variables.state = 2;
            ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.resume")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)); //Resuming
            settingUpPcPowerComponents(variables, world, server);
            ModUtils.playGlobalNoteBlockSound(1.2F);
        } else if (variables.state == 2) {
            ModUtils.sendLocalChatMessageTo(serverPlayer, Component.translatable("command.biomesofcataclysms.gameAlreadyStarted")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            ModUtils.playLocalNoteBlockSound(arguments.getSource().getPlayer(), 0.4F);
        }else{
            ModUtils.sendLocalChatMessageTo(serverPlayer,
                    ModUtils.buildErrorMessage(
                            true,
                            7,
                            Component.literal(COMMAND_0 + "[C]"),
                            Component.translatable("error.biomesofcataclysms.error7")
                    ));
            ModUtils.playLocalErrorSound(serverPlayer);
        }

        variables.syncData(world, true, false);

        return 0;
    }
    private static int pause(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);

        if (variables.state == 2){
            ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.pause")
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));

            MinecraftServer server = arguments.getSource().getServer();
            ModUtils.playGlobalNoteBlockSound(0.6F);

            for (ServerLevel serverLevel : server.getAllLevels()) {
                loadRuntimeToPersistent(serverLevel);

                if (!ModUtils.isAGameAlreadyStarted(variables) && variables.state == 1){
                    variables.state = 0;
                }
            }

            variables.state = 1;
            variables.syncData(world, true, false);
        }else{
            ServerPlayer player = arguments.getSource().getPlayer();
            assert player != null;
            ModUtils.sendLocalChatMessageTo(player, Component.translatable("command.biomesofcataclysms.alreadyPaused")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            ModUtils.playLocalNoteBlockSound(player, 0.4F);
        }
        return 0;
    }
    private static int reset(CommandContext<CommandSourceStack> arguments){
        ServerLevel world = arguments.getSource().getLevel();
        ServerPlayer player = arguments.getSource().getPlayer();
        assert player != null;
        PersistentData.MapVariables variables = PersistentData.MapVariables.get(world);

        ModUtils.sendChatMessage(world, Component.translatable("command.biomesofcataclysms.reset")
                .withStyle(ChatFormatting.RED)); //Starting
        if (variables.state != 0 && variables.state != 1) {
            ModUtils.executeCommandAsEntity(player, "biomesOfCataclysms " + COMMAND_1);
        }else{
            ModUtils.playLocalNoteBlockSound(player, 0.6F);
        }

        return 0;
    }

    private static void loadRuntimeToPersistent(ServerLevel server) {
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

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {//OP players
        event.getDispatcher().register(Commands.literal(PREFIX).requires(s -> s.hasPermission(PERMISION_LEVEL))
                .then(Commands.literal(COMMAND_0) //startOrResume
                        .executes(SingleCommands::startOrResume))
                .then(Commands.literal(COMMAND_1) //pause
                        .executes(SingleCommands::pause))
                .then(Commands.literal(COMMAND_2) //reset
                        .executes(SingleCommands::reset)));
    }
}

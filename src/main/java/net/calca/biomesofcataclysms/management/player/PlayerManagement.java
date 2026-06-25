package net.calca.biomesofcataclysms.management.player;


import net.calca.biomesofcataclysms.BiomesOfCataclysms;
import net.calca.biomesofcataclysms.ModUtils;
import net.calca.biomesofcataclysms.bar.ProgressBarManager;
import net.calca.biomesofcataclysms.data.PersistentData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Objects;
import java.util.Optional;

@EventBusSubscriber(modid = BiomesOfCataclysms.MODID, bus = EventBusSubscriber.Bus.GAME)
public class PlayerManagement {

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

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event){
        Player player = event.getEntity();
        PersistentData.MapVariables globalVars = PersistentData.MapVariables.get(Objects.requireNonNull(player.getServer()).overworld());
        playerRespawnManager(globalVars, player);
    }



}

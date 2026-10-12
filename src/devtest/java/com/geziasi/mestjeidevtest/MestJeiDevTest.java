/*
 * Parts of this driver (world creation, the ME network and the terminal setup) are adapted from
 * the ME Spliced Terminal's development test driver and remain under its MIT license:
 *
 * Copyright (c) 2026 lhy512103
 * Copyright (c) 2026 GeZiAsi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions: The above copyright notice and this
 * permission notice shall be included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 */
package com.geziasi.mestjeidevtest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.LoadingErrorScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuLocators;

import com.lhy.mest.client.MESTScreen;
import com.lhy.mest.item.ItemMEST;
import com.lhy.mest.registry.ModItems;

/**
 * Development-only driver (never shipped): with {@code -Dmestjei.devtest=true} it creates or loads a
 * flat test world, builds a small powered ME network, opens a linked spliced terminal and runs the
 * checks in {@link DevTestSteps}, saving screenshots to {@code screenshots/}. Every result is logged
 * with the {@code MESTJEI-DEVTEST} prefix.
 */
@Mod(MestJeiDevTest.MODID)
public class MestJeiDevTest {
    public static final String MODID = "mestjeidevtest";
    static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "mestjeidev";

    private enum Stage { TITLE, WORLD, BUILD, FILL, OPEN, SHOOT, ACTIONS, DONE }

    private Stage stage = Stage.TITLE;
    private int ticks;
    private static BlockPos base;
    private final List<Step> steps = new ArrayList<>();
    private int stepIndex;
    static int passed;
    static int failed;

    public MestJeiDevTest() {
        if (FMLEnvironment.dist == Dist.CLIENT && Boolean.getBoolean("mestjei.devtest")) {
            MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
            log("enabled");
            resetLayout();
            if (Boolean.getBoolean("mestjei.devtest.quit")) {
                startWatchdog();
            }
        }
    }

    /** Start every run from the terminal's bundled default layout. */
    private static void resetLayout() {
        var dir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("mesplicedterminal");
        try {
            java.nio.file.Files.deleteIfExists(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve("mestjei").resolve("windows.properties"));
            java.nio.file.Files.deleteIfExists(net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                    .resolve("jei").resolve("bookmarks.ini"));
        } catch (java.io.IOException e) {
            log("could not reset placement marker: {}", e);
        }
        for (String file : new String[] { "layout.json", "presets.json", "preferences.json" }) {
            try {
                java.nio.file.Files.deleteIfExists(dir.resolve(file));
            } catch (java.io.IOException e) {
                log("could not reset {}: {}", file, e);
            }
        }
    }

    private static void startWatchdog() {
        long deadline = System.currentTimeMillis() + Long.getLong("mestjei.devtest.timeout", 600) * 1000L;
        Thread watchdog = new Thread(() -> {
            int errorScreenPolls = 0;
            while (true) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
                Minecraft mc = Minecraft.getInstance();
                errorScreenPolls = mc != null && mc.screen instanceof LoadingErrorScreen ? errorScreenPolls + 1 : 0;
                if (errorScreenPolls >= 5) {
                    LOGGER.error("MESTJEI-DEVTEST FAIL mod loading failed, quitting");
                    mc.execute(mc::stop);
                    errorScreenPolls = Integer.MIN_VALUE;
                }
                if (System.currentTimeMillis() > deadline) {
                    LOGGER.error("MESTJEI-DEVTEST FAIL timed out, halting");
                    Runtime.getRuntime().halt(2);
                }
            }
        }, "MEST JEI devtest watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    static void log(String message, Object... args) {
        LOGGER.info("MESTJEI-DEVTEST " + message, args);
    }

    static void check(boolean ok, String what, Object... args) {
        if (ok) {
            passed++;
            log("PASS " + what, args);
        } else {
            failed++;
            log("FAIL " + what, args);
        }
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        try {
            switch (stage) {
                case TITLE -> {
                    if (mc.screen instanceof AccessibilityOnboardingScreen) {
                        mc.options.onboardAccessibility = false;
                        mc.options.save();
                        mc.setScreen(new TitleScreen(true));
                    }
                    if (mc.screen instanceof TitleScreen title && ticks > 40) {
                        openWorld(mc, title);
                        stage = Stage.WORLD;
                        ticks = 0;
                    }
                }
                case WORLD -> {
                    if (mc.player != null && mc.getSingleplayerServer() != null && mc.level != null && ticks > 60) {
                        stage = Stage.BUILD;
                        ticks = 0;
                    }
                }
                case BUILD -> {
                    runOnServer(mc, this::buildNetwork);
                    stage = Stage.FILL;
                    ticks = 0;
                }
                case FILL -> {
                    if (ticks == 30) {
                        runOnServer(mc, this::insertCells);
                    }
                    if (ticks > 120) {
                        runOnServer(mc, this::fillNetworkAndGiveTerminal);
                        stage = Stage.OPEN;
                        ticks = 0;
                    }
                }
                case OPEN -> {
                    if (ticks == 40) {
                        runOnServer(mc, this::openTerminal);
                    }
                    if (mc.screen instanceof MESTScreen) {
                        log("terminal screen opened");
                        stage = Stage.SHOOT;
                        ticks = 0;
                    } else if (ticks > 200) {
                        check(false, "terminal screen did not open, screen={}", mc.screen);
                        stage = Stage.DONE;
                    }
                }
                case SHOOT -> {
                    if (ticks == 1) {
                        mc.options.guiScale().set(Integer.getInteger("mestjei.devtest.guiScale", 2));
                        mc.resizeDisplay();
                        mc.getToasts().clear();
                    }
                    if (ticks == 60) {
                        DevTestSteps.register(steps);
                        stage = Stage.ACTIONS;
                        ticks = 0;
                    }
                }
                case ACTIONS -> {
                    if (stepIndex >= steps.size()) {
                        stage = Stage.DONE;
                        ticks = 0;
                        break;
                    }
                    Step step = steps.get(stepIndex);
                    if (ticks == 1) {
                        log("step {} start: {}", stepIndex, step.name());
                        try {
                            step.action().accept(mc);
                        } catch (Throwable t) {
                            failed++;
                            LOGGER.error("MESTJEI-DEVTEST FAIL step {} threw", step.name(), t);
                        }
                    }
                    if (ticks >= step.waitTicks()) {
                        if (step.screenshot() != null) {
                            screenshot(mc, step.screenshot());
                        }
                        stepIndex++;
                        ticks = 0;
                    }
                }
                case DONE -> {
                    if (ticks == 20) {
                        log("finished: {} passed, {} failed", passed, failed);
                        if (Boolean.getBoolean("mestjei.devtest.quit")) {
                            mc.stop();
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("MESTJEI-DEVTEST FAIL in stage {}", stage, t);
            failed++;
            stage = Stage.DONE;
            ticks = 0;
        }
    }

    private static void openWorld(Minecraft mc, TitleScreen title) {
        if (mc.getLevelSource().levelExists(WORLD)) {
            log("loading existing world {}", WORLD);
            mc.createWorldOpenFlows().loadLevel(title, WORLD);
            return;
        }
        log("creating world {}", WORLD);
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        var settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    static void runOnServer(Minecraft mc, Consumer<ServerPlayer> work) {
        var server = mc.getSingleplayerServer();
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                log("FAIL no server player");
                return;
            }
            try {
                work.accept(player);
            } catch (Throwable t) {
                LOGGER.error("MESTJEI-DEVTEST FAIL on server", t);
            }
        });
    }

    private void buildNetwork(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        base = player.blockPosition().offset(3, 0, 0);
        level.setBlockAndUpdate(base, AEBlocks.CONTROLLER.block().defaultBlockState());
        level.setBlockAndUpdate(base.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(base.above(), AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        level.setBlockAndUpdate(base.below(), AEBlocks.DRIVE.block().defaultBlockState());
        log("network built at {}", base);
    }

    private void insertCells(ServerPlayer player) {
        if (player.serverLevel().getBlockEntity(base.below()) instanceof DriveBlockEntity drive) {
            drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
        }
    }

    private void fillNetworkAndGiveTerminal(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        var host = GridHelper.getNodeHost(level, base);
        var node = host == null ? null : host.getGridNode(Direction.UP);
        if (node != null && node.getGrid() != null) {
            var storage = node.getGrid().getStorageService().getInventory();
            for (var item : List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.REDSTONE,
                    Items.OAK_LOG, Items.OAK_PLANKS, Items.STICK, Items.COBBLESTONE)) {
                storage.insert(AEItemKey.of(item), 1000, Actionable.MODULATE, IActionSource.empty());
            }
        }
        ItemStack terminal = new ItemStack(ModItems.SPLICED_TERMINAL.get());
        WirelessTerminalItem.LINKABLE_HANDLER.link(terminal, GlobalPos.of(level.dimension(), base.above()));
        ItemMEST item = (ItemMEST) terminal.getItem();
        item.injectAEPower(terminal, item.getAEMaxPower(terminal), Actionable.MODULATE);
        player.getInventory().setItem(0, terminal);
        player.getInventory().selected = 0;
        log("terminal given and linked");
    }

    private void openTerminal(ServerPlayer player) {
        ItemStack stack = player.getInventory().getItem(0);
        if (stack.getItem() instanceof ItemMEST item) {
            item.tryOpen(player, MenuLocators.forInventorySlot(0), stack, false);
        }
    }

    static void screenshot(Minecraft mc, String name) {
        Screenshot.grab(new File(mc.gameDirectory.getAbsolutePath()), name + ".png", mc.getMainRenderTarget(),
                message -> log("screenshot {}", name));
    }

    record Step(String name, int waitTicks, String screenshot, Consumer<Minecraft> action) {
    }
}

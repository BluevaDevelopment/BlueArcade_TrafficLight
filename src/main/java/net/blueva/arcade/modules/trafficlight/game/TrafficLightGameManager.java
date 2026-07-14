package net.blueva.arcade.modules.trafficlight.game;

import net.blueva.arcade.api.config.CoreConfigAPI;
import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.ModuleAPI;
import net.blueva.arcade.api.visuals.VisualEffectsAPI;
import net.blueva.arcade.modules.trafficlight.state.LightState;
import net.blueva.arcade.modules.trafficlight.state.TrafficLightArenaState;
import net.blueva.arcade.modules.trafficlight.state.TrafficLightStateRegistry;
import net.blueva.arcade.modules.trafficlight.support.TrafficLightLoadoutService;
import net.blueva.arcade.modules.trafficlight.support.TrafficLightMessagingService;
import net.blueva.arcade.modules.trafficlight.support.TrafficLightStatsService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TrafficLightGameManager {

    private final ModuleConfigAPI moduleConfig;
    private final CoreConfigAPI coreConfig;
    private final ModuleInfo moduleInfo;
    private final TrafficLightStatsService statsService;
    private final TrafficLightLoadoutService loadoutService;
    private final TrafficLightMessagingService messagingService;
    private final TrafficLightStateRegistry stateRegistry;

    public TrafficLightGameManager(ModuleConfigAPI moduleConfig,
                                   CoreConfigAPI coreConfig,
                                   ModuleInfo moduleInfo,
                                   TrafficLightStatsService statsService) {
        this.moduleConfig = moduleConfig;
        this.coreConfig = coreConfig;
        this.moduleInfo = moduleInfo;
        this.statsService = statsService;
        this.loadoutService = new TrafficLightLoadoutService(moduleConfig);
        this.messagingService = new TrafficLightMessagingService(moduleConfig);
        this.stateRegistry = new TrafficLightStateRegistry();
    }

    public void handleStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        context.getSchedulerAPI().cancelArenaTasks(context.getArenaId());

        TrafficLightArenaState arenaState = stateRegistry.startArena(context);
        setInitialLightState(arenaState);
        messagingService.sendDescription(context);
    }

    public void handleCountdownTick(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, int secondsLeft) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) continue;

            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.countdown"));

            String title = coreConfig.getLanguage(player, "titles.starting_game.title")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            String subtitle = coreConfig.getLanguage(player, "titles.starting_game.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName())
                    .replace("{time}", String.valueOf(secondsLeft));

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 5);
        }
    }

    public void handleCountdownFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        for (Player player : context.getPlayers()) {
            if (!player.isOnline()) continue;

            String title = coreConfig.getLanguage(player, "titles.game_started.title")
                    .replace("{game_display_name}", moduleInfo.getName());

            String subtitle = coreConfig.getLanguage(player, "titles.game_started.subtitle")
                    .replace("{game_display_name}", moduleInfo.getName());

            context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 20, 20);
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.starting_game.start"));
        }
    }

    public boolean freezePlayersOnCountdown() {
        return true;
    }

    public void handleGameStart(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int initialTimeLeft = startGameTimer(context);

        for (Player player : context.getPlayers()) {
            loadoutService.giveStartingItems(player);
            loadoutService.applyStartingEffects(player);
            context.getScoreboardAPI().showModuleScoreboard(player);
        }

        LightState initialState = stateRegistry.getArenaState(context).getLightState();
        giveLightIndicators(context, initialState);
        updateGameHud(context, initialTimeLeft);
    }

    private int startGameTimer(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        TrafficLightArenaState arenaState = stateRegistry.getArenaState(context);
        if (arenaState == null) {
            return 0;
        }

        Integer gameTime = context.getDataAccess().getGameData("basic.time", Integer.class);
        if (gameTime == null || gameTime == 0) {
            gameTime = 60;
        }

        final int[] timeLeft = {gameTime};
        String taskId = "arena_" + context.getArenaId() + "_traffic_light_timer";

        context.getSchedulerAPI().runTimer(taskId, () -> {
            if (arenaState.isEnded()) {
                context.getSchedulerAPI().cancelTask(taskId);
                return;
            }

            timeLeft[0]--;

            List<Player> alivePlayers = context.getAlivePlayers();
            List<Player> allPlayers = context.getPlayers();
            List<Player> spectators = context.getSpectators();

            if (allPlayers.size() < 2 || alivePlayers.isEmpty() || spectators.size() >= Math.min(3, allPlayers.size()) || timeLeft[0] <= 0) {
                endGameOnce(context);
                return;
            }

            tickLightCycle(arenaState, context);

            updateGameHud(context, timeLeft[0], alivePlayers, spectators);
        }, 20L, 20L);

        return timeLeft[0];
    }

    private void updateGameHud(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, int timeLeft) {
        List<Player> alivePlayers = context.getAlivePlayers();
        List<Player> spectators = context.getSpectators();

        updateGameHud(context, timeLeft, alivePlayers, spectators);
    }

    private void updateGameHud(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, int timeLeft, List<Player> alivePlayers, List<Player> spectators) {
        List<Player> allPlayers = context.getPlayers();

        for (Player player : allPlayers) {
            if (!player.isOnline()) continue;

            String actionBarTemplate = coreConfig.getLanguage(player, "action_bar.in_game.global");
            String actionBarMessage = messagingService.formatActionBar(actionBarTemplate, context, timeLeft);
            context.getMessagesAPI().sendActionBar(player, actionBarMessage);

            Map<String, String> customPlaceholders = getCustomPlaceholders(player);
            customPlaceholders.put("time", formatCountdownTime(timeLeft));
            customPlaceholders.put("round", String.valueOf(context.getCurrentRound()));
            customPlaceholders.put("round_max", String.valueOf(context.getMaxRounds()));
            customPlaceholders.put("alive", String.valueOf(alivePlayers.size()));
            customPlaceholders.put("spectators", String.valueOf(spectators.size()));

            context.getScoreboardAPI().update(player, customPlaceholders);
        }
    }

    private void tickLightCycle(TrafficLightArenaState arenaState, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        int newTime = arenaState.getLightTimer() - 1;
        if (newTime <= 0) {
            switchLight(context, arenaState);
            return;
        }
        arenaState.setLightTimer(newTime);
    }

    private void switchLight(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, TrafficLightArenaState arenaState) {
        LightState current = arenaState.getLightState();
        LightState next;
        if (current == LightState.GREEN) {
            next = LightState.YELLOW;
            arenaState.setLightTimer(getRandomDuration("red_light.durations.yellow.min", "red_light.durations.yellow.max", 1, 3));
        } else if (current == LightState.YELLOW) {
            next = LightState.RED;
            arenaState.setLightTimer(getRandomDuration("red_light.durations.red.min", "red_light.durations.red.max", 1, 8));
        } else {
            next = LightState.GREEN;
            arenaState.setLightTimer(getRandomDuration("red_light.durations.green.min", "red_light.durations.green.max", 3, 6));
            respawnRedLockedPlayers(context, arenaState);
            clearPushbackState(arenaState);
        }

        arenaState.setLightState(next);
        giveLightIndicators(context, next);
    }

    private void setInitialLightState(TrafficLightArenaState arenaState) {
        arenaState.setLightState(LightState.GREEN);
        arenaState.setLightTimer(getRandomDuration("red_light.durations.green.min", "red_light.durations.green.max", 3, 6));
    }

    private int getRandomDuration(String minPath, String maxPath, int defaultMin, int defaultMax) {
        int min = moduleConfig.getInt(minPath, defaultMin);
        int max = moduleConfig.getInt(maxPath, defaultMax);
        if (min <= 0) min = defaultMin;
        if (max < min) max = min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void giveLightIndicators(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, LightState state) {
        Material indicator = Material.LIME_WOOL;
        if (state == LightState.YELLOW) {
            indicator = Material.YELLOW_WOOL;
        } else if (state == LightState.RED) {
            indicator = Material.RED_WOOL;
        }

        for (Player player : context.getPlayers()) {
            if (!context.isPlayerPlaying(player)) {
                continue;
            }

            org.bukkit.inventory.PlayerInventory inventory = player.getInventory();
            for (int slot = 0; slot < 36; slot++) {
                inventory.setItem(slot, new org.bukkit.inventory.ItemStack(indicator, 1));
            }
            inventory.setItemInOffHand(new org.bukkit.inventory.ItemStack(indicator, 1));
        }
    }

    private void respawnRedLockedPlayers(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, TrafficLightArenaState arenaState) {
        for (UUID uuid : new HashSet<>(arenaState.getRedLockedPlayers())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }

            if (!context.isPlayerPlaying(player)) {
                arenaState.getRedLockedPlayers().remove(uuid);
                continue;
            }

            context.respawnPlayer(player);
            player.setGameMode(GameMode.SURVIVAL);
            loadoutService.applyRespawnEffects(player);
            context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.respawn"));

            arenaState.getRedLockedPlayers().remove(uuid);
        }
    }

    private void clearPushbackState(TrafficLightArenaState arenaState) {
        arenaState.getPushbackPrimed().clear();
        arenaState.getPushbackWindows().clear();
    }

    private void endGameOnce(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        TrafficLightArenaState arenaState = stateRegistry.getArenaState(context);
        if (arenaState == null || arenaState.isEnded()) {
            return;
        }

        arenaState.markEnded();
        context.getSchedulerAPI().cancelArenaTasks(context.getArenaId());
        context.endGame();
    }

    public void handleEnd(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        context.getSchedulerAPI().cancelArenaTasks(context.getArenaId());

        statsService.recordGamesPlayed(context.getPlayers());
        stateRegistry.removeArena(context);
    }

    public void onDisable() {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> anyContext = stateRegistry.anyContext();
        if (anyContext != null) {
            anyContext.getSchedulerAPI().cancelModuleTasks("traffic_light");
        }
        stateRegistry.clear();
    }

    public void handlePlayerFinish(Player player) {
        TrafficLightArenaState arenaState = stateRegistry.getArenaState(player);
        if (arenaState == null) {
            return;
        }

        statsService.recordFinishCross(player);

        if (arenaState.getWinner() == null) {
            arenaState.setWinner(player.getUniqueId());
            statsService.recordWin(player);
        }
    }

    public Map<String, String> getCustomPlaceholders(Player player) {
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = getGameContext(player);
        TrafficLightArenaState arenaState = stateRegistry.getArenaState(player);
        if (context == null || arenaState == null) {
            return new java.util.HashMap<>();
        }

        Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("light_color", messagingService.formatLightColor(arenaState.getLightState()));
        placeholders.put("light_state", arenaState.getLightState().name());
        placeholders.put("round", String.valueOf(context.getCurrentRound()));
        placeholders.put("round_max", String.valueOf(context.getMaxRounds()));
        return placeholders;
    }

    public void handleRedLightMovement(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player, Location from, Location to) {
        TrafficLightArenaState arenaState = stateRegistry.getArenaState(context);
        if (arenaState == null) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (context.getSpectators().contains(player)) {
            return;
        }

        if (arenaState.getRedLockedPlayers().contains(uuid)) {
            return;
        }

        boolean pushbackEnabled = moduleConfig.getBoolean("red_light.pushback.enabled", true);
        int immunityTicks = moduleConfig.getInt("red_light.pushback.immunity_ticks", 15);
        double force = moduleConfig.getDouble("red_light.pushback.force", 1.1d);

        if (pushbackEnabled) {
            long now = System.currentTimeMillis();
            Long windowEnd = arenaState.getPushbackWindows().get(uuid);

            if (!arenaState.getPushbackPrimed().contains(uuid)) {
                applyPushback(player, from, to, force);
                arenaState.getPushbackPrimed().add(uuid);
                arenaState.getPushbackWindows().put(uuid, now + immunityTicks * 50L);
                messagingService.sendPushbackWarning(context, player);
                return;
            }

            if (windowEnd != null && now < windowEnd) {
                return;
            }
        }

        punishRedMovement(context, player, arenaState);
    }

    private void applyPushback(Player player, org.bukkit.Location from, org.bukkit.Location to, double force) {
        Vector direction = from.toVector().subtract(to.toVector());
        if (direction.lengthSquared() == 0) {
            direction = player.getLocation().getDirection().multiply(-1);
        }
        direction.setY(0);
        if (direction.lengthSquared() == 0) {
            direction = new Vector(0, 0.3, 0);
        } else {
            direction.normalize().multiply(force).setY(0.25);
        }
        player.setVelocity(direction);
    }

    private void punishRedMovement(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player, TrafficLightArenaState arenaState) {
        arenaState.getRedLockedPlayers().add(player.getUniqueId());
        arenaState.getPushbackPrimed().remove(player.getUniqueId());
        arenaState.getPushbackWindows().remove(player.getUniqueId());

        player.setGameMode(GameMode.SPECTATOR);
        context.getSoundsAPI().play(player, coreConfig.getSound("sounds.in_game.dead"));
        messagingService.handleRedLightDeathMessage(context, player);
    }

    public void handlePlayerRespawn(Player player) {
        loadoutService.applyRespawnEffects(player);
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getGameContext(Player player) {
        return stateRegistry.getContext(player);
    }

    public boolean isRedLight(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        TrafficLightArenaState arenaState = stateRegistry.getArenaState(context);
        return arenaState != null && arenaState.getLightState() == LightState.RED;
    }

    public void cleanupPlayerState(Player player) {
        TrafficLightArenaState arenaState = stateRegistry.getArenaState(player);
        if (arenaState == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        arenaState.getPushbackPrimed().remove(uuid);
        arenaState.getPushbackWindows().remove(uuid);
        arenaState.getRedLockedPlayers().remove(uuid);
    }

    public boolean isPlayerRedLocked(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        TrafficLightArenaState arenaState = stateRegistry.getArenaState(context);
        if (arenaState == null) {
            return false;
        }

        return arenaState.getRedLockedPlayers().contains(player.getUniqueId());
    }

    public void handlePlayerDeath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player, boolean deathBlock) {
        messagingService.handlePlayerDeath(context, player, deathBlock);
    }

    public void broadcastFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player, int position) {
        messagingService.broadcastFinish(context, player, position);
    }

    public CoreConfigAPI getCoreConfig() {
        return coreConfig;
    }

    public ModuleConfigAPI getModuleConfig() {
        return moduleConfig;
    }

    public void playDeathEffect(Player player, org.bukkit.Location location) {
        VisualEffectsAPI visualEffectsAPI = ModuleAPI.getVisualEffectsAPI();
        if (visualEffectsAPI == null || player == null) {
            return;
        }
        visualEffectsAPI.playDeathEffect(player, location != null ? location : player.getLocation());
    }

    private static String formatCountdownTime(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d", safeSeconds / 60, safeSeconds % 60);
    }

}

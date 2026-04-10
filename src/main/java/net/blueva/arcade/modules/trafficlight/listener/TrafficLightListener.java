package net.blueva.arcade.modules.trafficlight.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.trafficlight.game.TrafficLightGameManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

public class TrafficLightListener implements Listener {

    private final TrafficLightGameManager gameManager;

    public TrafficLightListener(TrafficLightGameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = gameManager.getGameContext(player);
        if (context == null) {
            return;
        }

        if (!context.isPlayerPlaying(player)) {
            return;
        }

        if (gameManager.isPlayerRedLocked(context, player)) {
            return;
        }

        if (to == null || (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        if (context.getPhase() == GamePhase.COUNTDOWN) {
            event.setTo(event.getFrom());
            return;
        }

        if (!context.isInsideBounds(to)) {
            Location deathLocation = player.getLocation();
            context.respawnPlayer(player);

            context.getSoundsAPI().play(player, gameManager.getCoreConfig().getSound("sounds.in_game.respawn"));
            gameManager.playDeathEffect(player, deathLocation);
            gameManager.handlePlayerDeath(context, player, false);
            gameManager.handlePlayerRespawn(player);
            gameManager.cleanupPlayerState(player);
            return;
        }

        Location blockBelow = to.clone().subtract(0, 1, 0);
        Material blockBelowType = blockBelow.getBlock().getType();

        Material deathBlock = getDeathBlock(context);
        if (blockBelowType == deathBlock) {
            Location deathLocation = player.getLocation();
            context.respawnPlayer(player);

            context.getSoundsAPI().play(player, gameManager.getCoreConfig().getSound("sounds.in_game.respawn"));
            gameManager.playDeathEffect(player, deathLocation);
            gameManager.handlePlayerDeath(context, player, true);
            gameManager.handlePlayerRespawn(player);
            gameManager.cleanupPlayerState(player);
            return;
        }

        if (gameManager.isRedLight(context)) {
            gameManager.handleRedLightMovement(context, player, from, to);
            return;
        }

        if (isInsideFinishLine(context, to)) {
            if (!context.getSpectators().contains(player)) {
                context.finishPlayer(player);
                gameManager.cleanupPlayerState(player);

                int position = context.getSpectators().indexOf(player) + 1;

                gameManager.handlePlayerFinish(player);
                gameManager.broadcastFinish(context, player, position);

                String title = gameManager.getModuleConfig().getStringFrom("language.yml", "titles.finished.title");
                String subtitle = gameManager.getModuleConfig().getStringFrom("language.yml", "titles.finished.subtitle")
                        .replace("{position}", String.valueOf(position));

                context.getTitlesAPI().sendRaw(player, title, subtitle, 0, 80, 20);
                context.getSoundsAPI().play(player, gameManager.getCoreConfig().getSound("sounds.in_game.classified"));
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = gameManager.getGameContext(player);

        if (!isTrafficLightParticipant(context, player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = gameManager.getGameContext(player);

        if (!isTrafficLightParticipant(context, player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = gameManager.getGameContext(player);

        if (!isTrafficLightParticipant(context, player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context = gameManager.getGameContext(victim);
        if (context == null || !context.isPlayerPlaying(victim)) {
            return;
        }

        if (!context.isPlayerPlaying(attacker)) {
            return;
        }

        event.setCancelled(true);
    }

    private Material getDeathBlock(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        try {
            String deathBlockName = context.getDataAccess().getGameData("basic.death_block", String.class);
            if (deathBlockName != null) {
                return Material.valueOf(deathBlockName.toUpperCase());
            }
        } catch (Exception ignored) {
            // Fallback
        }
        return Material.BARRIER;
    }

    private boolean isInsideFinishLine(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Location location) {
        try {
            Location finishMin = context.getDataAccess().getGameLocation("game.finish_line.bounds.min");
            Location finishMax = context.getDataAccess().getGameLocation("game.finish_line.bounds.max");

            if (finishMin == null || finishMax == null) {
                return false;
            }

            return location.getX() >= Math.min(finishMin.getX(), finishMax.getX()) &&
                    location.getX() <= Math.max(finishMin.getX(), finishMax.getX()) &&
                    location.getY() >= Math.min(finishMin.getY(), finishMax.getY()) &&
                    location.getY() <= Math.max(finishMin.getY(), finishMax.getY()) &&
                    location.getZ() >= Math.min(finishMin.getZ(), finishMax.getZ()) &&
                    location.getZ() <= Math.max(finishMin.getZ(), finishMax.getZ());

        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTrafficLightParticipant(
            GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context,
            Player player
    ) {
        return context != null && (context.isPlayerPlaying(player) || context.getSpectators().contains(player));
    }


}

package net.blueva.arcade.modules.trafficlight.state;

import net.blueva.arcade.api.game.GameContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrafficLightStateRegistry {

    private final Map<Integer, TrafficLightArenaState> arenas = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerArenas = new ConcurrentHashMap<>();

    public TrafficLightArenaState startArena(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        TrafficLightArenaState arenaState = new TrafficLightArenaState(context);
        arenas.put(context.getArenaId(), arenaState);
        for (Player player : context.getPlayers()) {
            playerArenas.put(player.getUniqueId(), context.getArenaId());
        }
        return arenaState;
    }

    public TrafficLightArenaState getArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null) {
            return null;
        }
        return arenas.get(context.getArenaId());
    }

    public TrafficLightArenaState getArenaState(Player player) {
        if (player == null) {
            return null;
        }
        Integer arenaId = playerArenas.get(player.getUniqueId());
        if (arenaId == null) {
            return null;
        }
        return arenas.get(arenaId);
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext(Player player) {
        TrafficLightArenaState arenaState = getArenaState(player);
        if (arenaState == null) {
            return null;
        }
        return arenaState.getContext();
    }

    public void removeArena(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        if (context == null) {
            return;
        }
        arenas.remove(context.getArenaId());
        for (Player player : context.getPlayers()) {
            playerArenas.remove(player.getUniqueId());
        }
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> anyContext() {
        return arenas.values().stream().findFirst().map(TrafficLightArenaState::getContext).orElse(null);
    }

    public void clear() {
        arenas.clear();
        playerArenas.clear();
    }
}

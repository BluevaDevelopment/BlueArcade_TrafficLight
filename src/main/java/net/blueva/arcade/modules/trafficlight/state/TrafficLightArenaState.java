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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrafficLightArenaState {

    private final GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context;
    private final Set<UUID> redLockedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> pushbackWindows = new ConcurrentHashMap<>();
    private final Set<UUID> pushbackPrimed = ConcurrentHashMap.newKeySet();
    private boolean ended;
    private UUID winner;
    private LightState lightState = LightState.GREEN;
    private int lightTimer;

    public TrafficLightArenaState(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        this.context = context;
    }

    public GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> getContext() {
        return context;
    }

    public Set<UUID> getRedLockedPlayers() {
        return redLockedPlayers;
    }

    public Map<UUID, Long> getPushbackWindows() {
        return pushbackWindows;
    }

    public Set<UUID> getPushbackPrimed() {
        return pushbackPrimed;
    }

    public boolean isEnded() {
        return ended;
    }

    public void markEnded() {
        this.ended = true;
    }

    public UUID getWinner() {
        return winner;
    }

    public void setWinner(UUID winner) {
        this.winner = winner;
    }

    public LightState getLightState() {
        return lightState;
    }

    public void setLightState(LightState lightState) {
        this.lightState = lightState;
    }

    public int getLightTimer() {
        return lightTimer;
    }

    public void setLightTimer(int lightTimer) {
        this.lightTimer = lightTimer;
    }
}

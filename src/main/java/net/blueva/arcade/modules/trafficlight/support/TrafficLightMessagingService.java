package net.blueva.arcade.modules.trafficlight.support;

import net.blueva.arcade.api.config.ModuleConfigAPI;
import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.modules.trafficlight.state.LightState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TrafficLightMessagingService {

    private final ModuleConfigAPI moduleConfig;

    public TrafficLightMessagingService(ModuleConfigAPI moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public void sendDescription(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context) {
        List<String> description = moduleConfig.getStringListFrom("language.yml", "description");

        for (Player player : context.getPlayers()) {
            for (String line : description) {
                context.getMessagesAPI().sendRaw(player, line);
            }
        }
    }

    public String formatActionBar(String template, GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, int timeLeft) {
        if (template == null) return "";

        return template
                .replace("{time}", String.valueOf(timeLeft))
                .replace("{round}", String.valueOf(context.getCurrentRound()))
                .replace("{round_max}", String.valueOf(context.getMaxRounds()));
    }

    public String formatLightColor(LightState state) {
        return switch (state) {
            case GREEN -> "<green>██████</green>";
            case YELLOW -> "<yellow>██████</yellow>";
            case RED -> "<red>██████</red>";
        };
    }

    public void sendPushbackWarning(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        String warning = moduleConfig.getStringFrom("language.yml", "messages.red_light.pushback_warning");
        if (warning != null && !warning.isEmpty()) {
            context.getMessagesAPI().sendRaw(player, warning);
        }
    }

    public void handleRedLightDeathMessage(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player) {
        String message = getRandomMessage("messages.deaths.red_light");
        if (message == null) {
            return;
        }

        message = message.replace("{player}", player.getName());
        for (Player target : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(target, message);
        }
    }

    public void handlePlayerDeath(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player, boolean deathBlock) {
        String path = deathBlock ? "messages.deaths.death_block" : "messages.deaths.void";
        String message = getRandomMessage(path);
        if (message == null) {
            return;
        }

        message = message.replace("{player}", player.getName());
        for (Player target : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(target, message);
        }
    }

    public void broadcastFinish(GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context, Player player, int position) {
        String message = getRandomMessage("messages.finish.crossed");
        if (message == null) {
            return;
        }

        message = message
                .replace("{player}", player.getName())
                .replace("{position}", String.valueOf(position));

        for (Player target : context.getPlayers()) {
            context.getMessagesAPI().sendRaw(target, message);
        }
    }

    private String getRandomMessage(String path) {
        List<String> messages = moduleConfig.getStringListFrom("language.yml", path);
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        int index = ThreadLocalRandom.current().nextInt(messages.size());
        return messages.get(index);
    }
}

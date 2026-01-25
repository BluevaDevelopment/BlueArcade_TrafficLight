package net.blueva.arcade.modules.trafficlight.support;

import net.blueva.arcade.api.module.ModuleInfo;
import net.blueva.arcade.api.stats.StatDefinition;
import net.blueva.arcade.api.stats.StatScope;
import net.blueva.arcade.api.stats.StatsAPI;
import org.bukkit.entity.Player;

import java.util.Collection;

public class TrafficLightStatsService {

    private final StatsAPI statsAPI;
    private final ModuleInfo moduleInfo;

    public TrafficLightStatsService(StatsAPI statsAPI, ModuleInfo moduleInfo) {
        this.statsAPI = statsAPI;
        this.moduleInfo = moduleInfo;
    }

    public void registerStats() {
        if (statsAPI == null) {
            return;
        }

        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("wins", "Wins", "Traffic Light wins", StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("games_played", "Games Played", "Traffic Light games played", StatScope.MODULE));
        statsAPI.registerModuleStat(moduleInfo.getId(),
                new StatDefinition("finish_line_crosses", "Finish line crosses", "Times you reached safety", StatScope.MODULE));
    }

    public void recordGamesPlayed(Collection<? extends Player> players) {
        if (statsAPI == null) {
            return;
        }
        for (Player player : players) {
            statsAPI.addModuleStat(player, moduleInfo.getId(), "games_played", 1);
        }
    }

    public void recordFinishCross(Player player) {
        if (statsAPI == null) {
            return;
        }
        statsAPI.addModuleStat(player, moduleInfo.getId(), "finish_line_crosses", 1);
    }

    public void recordWin(Player player) {
        if (statsAPI == null) {
            return;
        }
        statsAPI.addModuleStat(player, moduleInfo.getId(), "wins", 1);
        statsAPI.addGlobalStat(player, "wins", 1);
    }
}

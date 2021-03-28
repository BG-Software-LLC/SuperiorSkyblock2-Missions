package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StatisticsMissions extends Mission<Void> implements Listener {

    private static final SuperiorSkyblock superiorSkyblock = SuperiorSkyblockAPI.getSuperiorSkyblock();

    private final Map<List<String>, Integer> requiredStatistics = new HashMap<>();

    private JavaPlugin plugin;

    @Override
    public void load(JavaPlugin plugin, ConfigurationSection section) throws MissionLoadException {
        this.plugin = plugin;

        if(!section.contains("required-statistics"))
            throw new MissionLoadException("You must have the \"required-blocks\" section in the config.");

        for(String key : section.getConfigurationSection("required-statistics").getKeys(false)){
            List<String> blocks = section.getStringList("required-statistics." + key + ".statistics");
            int requiredAmount = section.getInt("required-statistics." + key + ".amount");
            requiredStatistics.put(blocks, requiredAmount);
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public double getProgress(SuperiorPlayer superiorPlayer) {
        double progress = 0.0;
        int totalRequiredAmount = 0;
        int totalItemAmount = 0;
        Player player = superiorPlayer.asPlayer();

        if(player == null)
            return progress;

        for (List<String> requiredStatistic : requiredStatistics.keySet()) {
            int requiredAmount = requiredStatistics.get(requiredStatistic);
            int statisticAmount = 0;
            for(String statistic : requiredStatistic){
                int currentStatisticAmount = getStatisticAmount(player, statistic);

                if(currentStatisticAmount == -1)
                    continue;

                //Making sure to not exceed the required item amount
                if(statisticAmount + currentStatisticAmount > requiredAmount)
                    currentStatisticAmount = requiredAmount - statisticAmount;

                //Summing the amount to a global variable
                statisticAmount += currentStatisticAmount;
            }

            totalRequiredAmount += requiredAmount;
            totalItemAmount += statisticAmount;
        }

        progress = Math.max(progress, (double) totalItemAmount / totalRequiredAmount);

        return progress;
    }

    @Override
    public int getProgressValue(SuperiorPlayer superiorPlayer) {
        int totalItemAmount = 0;

        Player player = superiorPlayer.asPlayer();
        if(player == null)
            return totalItemAmount;

        for (List<String> requiredStatistic : requiredStatistics.keySet()) {
            int requiredAmount = requiredStatistics.get(requiredStatistic);
            int statisticAmount = 0;
            for(String statistic : requiredStatistic){
                int currentItemAmount = getStatisticAmount(player, statistic);

                //Making sure to not exceed the required item amount
                if(statisticAmount + currentItemAmount > requiredAmount)
                    currentItemAmount = requiredAmount - statisticAmount;

                //Summing the amount to a global variable
                statisticAmount += currentItemAmount;
            }

            totalItemAmount += statisticAmount;
        }

        return totalItemAmount;
    }

    @Override
    public void onComplete(SuperiorPlayer superiorPlayer) {
        onCompleteFail(superiorPlayer);
    }

    @Override
    public void onCompleteFail(SuperiorPlayer superiorPlayer) {
        clearData(superiorPlayer);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerStatistic(PlayerStatisticIncrementEvent e){
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(e.getPlayer());

        if(!isMissionStatistic(e.getStatistic()) || !superiorSkyblock.getMissions().canCompleteNoProgress(superiorPlayer, this))
            return;

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if(canComplete(superiorPlayer))
                SuperiorSkyblockAPI.getSuperiorSkyblock().getMissions().rewardMission(this, superiorPlayer, true);
        }, 2L);
    }

    private boolean isMissionStatistic(Statistic statistic){
        if(statistic == null)
            return false;

        for (List<String> requiredStatistic : requiredStatistics.keySet()) {
            if(requiredStatistic.contains("ALL") || requiredStatistic.contains("all") || requiredStatistic.contains(statistic.name()))
                return true;
            if(statistic.isSubstatistic() && requiredStatistic.stream().anyMatch(line -> line.contains(statistic.name())))
                return true;
        }

        return false;
    }

    private static int getStatisticAmount(Player player, String statisticsString){
        String[] sections = statisticsString.split(":");
        int currentStatisticAmount = -1;

        try {
            Statistic statistic = Statistic.valueOf(sections[0]);

            if (sections.length == 1) {
                currentStatisticAmount = player.getStatistic(statistic);
            } else if (sections.length == 2) {
                Material material = getEnumSafe(Material.class, sections[1]);
                if (material != null) {
                    currentStatisticAmount = player.getStatistic(statistic, material);
                } else {
                    EntityType entityType = getEnumSafe(EntityType.class, sections[1]);
                    if (entityType != null)
                        currentStatisticAmount = player.getStatistic(statistic, entityType);
                }
            }
        }catch (Exception ignored){ }

        return currentStatisticAmount;
    }

    private static <T extends Enum<T>> T getEnumSafe(Class<T> clazz, String name){
        try{
            return Enum.valueOf(clazz, name);
        }catch (Throwable ex){
            return null;
        }
    }

}

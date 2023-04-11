package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.missions.common.Placeholders;
import com.bgsoftware.superiorskyblock.missions.common.Requirements;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StatisticsMissions extends Mission<Void> implements Listener {

    private final Map<Requirements, Integer> requiredStatistics = new LinkedHashMap<>();

    private SuperiorSkyblock plugin;

    @Override
    public void load(JavaPlugin plugin, ConfigurationSection section) throws MissionLoadException {
        this.plugin = (SuperiorSkyblock) plugin;

        ConfigurationSection requiredStatisticsSection = section.getConfigurationSection("required-statistics");

        if (requiredStatisticsSection == null)
            throw new MissionLoadException("You must have the \"required-blocks\" section in the config.");

        for (String key : requiredStatisticsSection.getKeys(false)) {
            List<String> statistics = section.getStringList("required-statistics." + key + ".statistics");
            int requiredAmount = section.getInt("required-statistics." + key + ".amount");
            requiredStatistics.put(new Requirements(statistics), requiredAmount);
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public double getProgress(SuperiorPlayer superiorPlayer) {
        double progress = 0.0;
        int totalRequiredAmount = 0;
        int totalItemAmount = 0;
        Player player = superiorPlayer.asPlayer();

        if (player == null)
            return progress;

        for (Map.Entry<Requirements, Integer> entry : this.requiredStatistics.entrySet()) {
            Requirements requirement = entry.getKey();
            int requiredAmount = entry.getValue();

            int statisticAmount = 0;
            for (String statistic : requirement) {
                int currentStatisticAmount = getStatisticAmount(player, statistic);

                if (currentStatisticAmount == -1)
                    continue;

                //Making sure to not exceed the required item amount
                if (statisticAmount + currentStatisticAmount > requiredAmount)
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
        if (player == null)
            return totalItemAmount;

        for (Map.Entry<Requirements, Integer> entry : this.requiredStatistics.entrySet()) {
            Requirements requirement = entry.getKey();
            int requiredAmount = entry.getValue();

            int statisticAmount = 0;
            for (String statistic : requirement) {
                int currentItemAmount = getStatisticAmount(player, statistic);

                //Making sure to not exceed the required item amount
                if (statisticAmount + currentItemAmount > requiredAmount)
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

    @Override
    public void formatItem(SuperiorPlayer superiorPlayer, ItemStack itemStack) {
        Player player = superiorPlayer.asPlayer();

        if (player == null)
            return;

        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta == null)
            return;

        Placeholders.PlaceholdersFunctions<String> placeholdersFunctions = new Placeholders.PlaceholdersFunctions<String>() {
            @Override
            public String getRequirementFromKey(String key) {
                return key;
            }

            @Override
            public Optional<Integer> lookupRequirement(String requirement) {
                return requiredStatistics.entrySet().stream()
                        .filter(e -> e.getKey().contains(requirement))
                        .findFirst()
                        .map(Map.Entry::getValue);
            }

            @Override
            public int getCountForRequirement(String requirement) {
                return getStatisticAmount(player, requirement);
            }
        };

        if (itemMeta.hasDisplayName())
            itemMeta.setDisplayName(Placeholders.parsePlaceholders(itemMeta.getDisplayName(), placeholdersFunctions));

        if (itemMeta.hasLore()) {
            List<String> lore = new ArrayList<>();
            for (String line : Objects.requireNonNull(itemMeta.getLore()))
                lore.add(Placeholders.parsePlaceholders(line, placeholdersFunctions));
            itemMeta.setLore(lore);
        }

        itemStack.setItemMeta(itemMeta);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerStatistic(PlayerStatisticIncrementEvent e) {
        SuperiorPlayer superiorPlayer = this.plugin.getPlayers().getSuperiorPlayer(e.getPlayer());

        if (!isMissionStatistic(e.getStatistic()) || !this.plugin.getMissions().canCompleteNoProgress(superiorPlayer, this))
            return;

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> superiorPlayer.runIfOnline(player -> {
            if (canComplete(superiorPlayer))
                this.plugin.getMissions().rewardMission(this, superiorPlayer, true);
        }), 2L);
    }

    private boolean isMissionStatistic(@Nullable Statistic statistic) {
        if (statistic == null)
            return false;

        for (Requirements requirement : requiredStatistics.keySet()) {
            if (requirement.contains(statistic.name()) || (statistic.isSubstatistic() &&
                    requirement.stream().anyMatch(line -> line.contains(statistic.name()))))
                return true;
        }

        return false;
    }

    private static int getStatisticAmount(Player player, String statisticsString) {
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
        } catch (Exception ignored) {
        }

        return currentStatisticAmount;
    }

    private static <T extends Enum<T>> T getEnumSafe(Class<T> clazz, String name) {
        try {
            return Enum.valueOf(clazz, name);
        } catch (Throwable ex) {
            return null;
        }
    }

}

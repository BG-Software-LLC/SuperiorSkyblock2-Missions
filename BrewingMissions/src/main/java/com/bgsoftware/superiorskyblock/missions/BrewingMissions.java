package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public final class BrewingMissions extends Mission<BrewingMissions.BrewingTracker> implements Listener {

    private static final SuperiorSkyblock superiorSkyblock = SuperiorSkyblockAPI.getSuperiorSkyblock();

    private static final boolean isUsing18 = Bukkit.getServer().getClass().getPackage().getName().contains("1_8");

    private static final Pattern percentagePattern = Pattern.compile("(.*)\\{percentage_(.+?)}(.*)"),
            valuePattern = Pattern.compile("(.*)\\{value_(.+?)}(.*)");

    private JavaPlugin plugin;
    private final Map<List<PotionData>, Integer> requiredPotions = new HashMap<>();
    private final Map<Location, boolean[]> trackedBrewItems = new HashMap<>();
    private boolean resetAfterFinish;

    @Override
    public void load(JavaPlugin plugin, ConfigurationSection section) throws MissionLoadException {
        this.plugin = plugin;

        if (!section.contains("required-potions"))
            throw new MissionLoadException("You must have the \"required-potions\" section in the config.");

        for (String key : section.getConfigurationSection("required-potions").getKeys(false)) {
            List<PotionData> potionDataList = new ArrayList<>();

            for (String potionSectionName : section.getConfigurationSection("required-potions." + key + ".potions").getKeys(false)) {
                ConfigurationSection potionSection = section.getConfigurationSection("required-potions." + key + ".potions." + potionSectionName);

                if (potionSection == null) {
                    plugin.getLogger().info(ChatColor.RED + "Potion section " + potionSectionName + " is invalid, skipping...");
                    continue;
                }

                PotionType potionType;

                try {
                    potionType = PotionType.valueOf(potionSection.getString("type"));
                } catch (Exception ex) {
                    plugin.getLogger().info(ChatColor.RED + "Potion type in section" + potionSectionName + " is invalid, skipping...");
                    continue;
                }

                boolean upgraded = potionSection.getBoolean("upgraded", false);
                boolean extended = potionSection.getBoolean("extended", false);
                boolean splash = potionSection.getBoolean("splash", false);

                potionDataList.add(new PotionData(potionType, upgraded, extended, splash));
            }

            if (!potionDataList.isEmpty()) {
                int requiredAmount = section.getInt("required-potions." + key + ".amount");
                requiredPotions.put(potionDataList, requiredAmount);
            }
        }

        if (requiredPotions.isEmpty()) {
            throw new MissionLoadException("There are no valid required potions for this mission.");
        }

        resetAfterFinish = section.getBoolean("reset-after-finish", false);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public double getProgress(SuperiorPlayer superiorPlayer) {
        BrewingTracker brewingTracker = get(superiorPlayer);

        if (brewingTracker == null)
            return 0.0;

        int requiredPotions = 0;
        int kills = 0;

        for (Map.Entry<List<PotionData>, Integer> requiredPotion : this.requiredPotions.entrySet()) {
            requiredPotions += requiredPotion.getValue();
            kills += Math.min(brewingTracker.getBrews(requiredPotion.getKey()), requiredPotion.getValue());
        }

        return (double) kills / requiredPotions;
    }

    @Override
    public int getProgressValue(SuperiorPlayer superiorPlayer) {
        BrewingTracker brewingTracker = get(superiorPlayer);

        if (brewingTracker == null)
            return 0;

        int kills = 0;

        for (Map.Entry<List<PotionData>, Integer> requiredEntity : this.requiredPotions.entrySet())
            kills += Math.min(brewingTracker.getBrews(requiredEntity.getKey()), requiredEntity.getValue());

        return kills;
    }

    @Override
    public void onComplete(SuperiorPlayer superiorPlayer) {
        if (resetAfterFinish)
            clearData(superiorPlayer);
    }

    @Override
    public void onCompleteFail(SuperiorPlayer superiorPlayer) {

    }

    @Override
    public void saveProgress(ConfigurationSection section) {
        for (Map.Entry<SuperiorPlayer, BrewingTracker> entry : entrySet()) {
            String uuid = entry.getKey().getUniqueId().toString();
            for (Map.Entry<PotionData, Integer> brokenEntry : entry.getValue().brewingTracker.entrySet()) {
                section.set(uuid + "." + brokenEntry.getKey(), brokenEntry.getValue());
            }
        }
    }

    @Override
    public void loadProgress(ConfigurationSection section) {
        for (String uuid : section.getKeys(false)) {
            BrewingTracker brewingTracker = new BrewingTracker();
            UUID playerUUID = UUID.fromString(uuid);
            SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(playerUUID);

            insertData(superiorPlayer, brewingTracker);

            for (String key : section.getConfigurationSection(uuid).getKeys(false)) {
                brewingTracker.brewingTracker.put(PotionData.fromString(key), section.getInt(uuid + "." + key));
            }
        }
    }

    @Override
    public void formatItem(SuperiorPlayer superiorPlayer, ItemStack itemStack) {
        BrewingTracker killsTracker = getOrCreate(superiorPlayer, s -> new BrewingTracker());

        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta.hasDisplayName())
            itemMeta.setDisplayName(parsePlaceholders(killsTracker, itemMeta.getDisplayName()));

        if (itemMeta.hasLore()) {
            List<String> lore = new ArrayList<>();
            for (String line : itemMeta.getLore())
                lore.add(parsePlaceholders(killsTracker, line));
            itemMeta.setLore(lore);
        }

        itemStack.setItemMeta(itemMeta);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        ItemStack[] originalResultItems = new ItemStack[3];
        for (int i = 0; i < 3; ++i) {
            ItemStack resultItem = e.getContents().getItem(i);
            if (resultItem != null) {
                originalResultItems[i] = resultItem.clone();
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < 3; ++i) {
                ItemStack resultItem = e.getContents().getItem(i);
                if (resultItem != null && !resultItem.isSimilar(originalResultItems[i]) && isMissionBrewing(resultItem)) {
                    trackedBrewItems.computeIfAbsent(e.getBlock().getLocation(), block -> new boolean[3])[i] = true;
                }
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(InventoryClickEvent e) {
        if (!(e.getClickedInventory() instanceof BrewerInventory) || e.getRawSlot() > 2)
            return;

        BrewerInventory inventory = (BrewerInventory) e.getClickedInventory();

        handleBrewing((Player) e.getWhoClicked(), inventory, slot -> slot == e.getRawSlot());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        BlockState blockState = e.getBlock().getState();

        if (!(blockState instanceof BrewingStand))
            return;

        BrewingStand brewingStand = (BrewingStand) blockState;

        handleBrewing(e.getPlayer(), brewingStand.getInventory(), slot -> true);
    }

    private void handleBrewing(Player player, BrewerInventory inventory, Predicate<Integer> checkSlot) {
        Block block = inventory.getHolder().getBlock();

        boolean[] brewItems = this.trackedBrewItems.get(block.getLocation());

        if (brewItems == null) {
            return;
        }

        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);

        for (int i = 0; i < 3; ++i) {
            if (checkSlot.test(i) && brewItems[i]) {
                brewItems[i] = false;

                ItemStack brewItem = inventory.getItem(i);

                BrewingTracker brewingTracker = getOrCreate(superiorPlayer, s -> new BrewingTracker());
                brewingTracker.track(brewItem, brewItem.getAmount());
            }
        }

        if (!brewItems[0] && !brewItems[1] && !brewItems[2])
            this.trackedBrewItems.remove(block.getLocation());

        if (!superiorSkyblock.getMissions().canCompleteNoProgress(superiorPlayer, this))
            return;

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> superiorPlayer.runIfOnline(_player -> {
            if (canComplete(superiorPlayer))
                SuperiorSkyblockAPI.getSuperiorSkyblock().getMissions().rewardMission(this, superiorPlayer, true);
        }), 2L);
    }

    private boolean isMissionBrewing(ItemStack itemStack) {
        PotionData potionData = PotionData.fromItemStack(itemStack);

        if (potionData == null)
            return false;

        for (List<PotionData> requiredPotion : requiredPotions.keySet()) {
            if (requiredPotion.contains(potionData))
                return true;
        }

        return false;
    }

    private String parsePlaceholders(BrewingTracker killsTracker, String line) {
        Matcher matcher = percentagePattern.matcher(line);

        if (matcher.matches()) {
            String requiredPotionType = matcher.group(2).toUpperCase();

            try {
                PotionData potionData = PotionData.fromString(requiredPotionType);

                Optional<Map.Entry<List<PotionData>, Integer>> entry = requiredPotions.entrySet().stream()
                        .filter(e -> e.getKey().contains(potionData)).findAny();

                if (entry.isPresent()) {
                    line = line.replace("{percentage_" + matcher.group(2) + "}",
                            "" + (killsTracker.getBrews(Collections.singletonList(potionData)) * 100) / entry.get().getValue());
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        if ((matcher = valuePattern.matcher(line)).matches()) {
            String requiredPotionType = matcher.group(2).toUpperCase();

            try {
                PotionData potionData = PotionData.fromString(requiredPotionType);

                Optional<Map.Entry<List<PotionData>, Integer>> entry = requiredPotions.entrySet().stream()
                        .filter(e -> e.getKey().contains(potionData)).findFirst();
                if (entry.isPresent()) {
                    line = line.replace("{value_" + matcher.group(2) + "}",
                            "" + killsTracker.getBrews(Collections.singletonList(potionData)));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        return ChatColor.translateAlternateColorCodes('&', line);
    }

    public static class BrewingTracker {

        private final Map<PotionData, Integer> brewingTracker = new HashMap<>();

        void track(ItemStack brewing, int amount) {
            PotionData potionData = PotionData.fromItemStack(brewing);
            int newAmount = amount + brewingTracker.getOrDefault(potionData, 0);
            brewingTracker.put(potionData, newAmount);
        }

        int getBrews(List<BrewingMissions.PotionData> potions) {
            int amount = 0;

            for (Map.Entry<PotionData, Integer> potionData : brewingTracker.entrySet()) {
                if (potions.contains(potionData.getKey()))
                    amount += potionData.getValue();
            }

            return amount;
        }

    }

    private static class PotionData {

        private final PotionType potionType;
        private final boolean upgraded;
        private final boolean extended;
        private final boolean splash;

        public PotionData(PotionType potionType, boolean upgraded, boolean extended, boolean splash) {
            this.potionType = potionType;
            this.upgraded = upgraded;
            this.extended = extended;
            this.splash = splash;
        }

        @Nullable
        public static PotionData fromItemStack(ItemStack itemStack) {
            return isUsing18 ? fromItemStack18(itemStack) : fromItemStack19(itemStack);
        }

        public static PotionData fromString(String line) {
            String[] sections = line.split(";");

            PotionType potionType;

            try {
                potionType = PotionType.valueOf(sections[0]);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("This is not a valid potion data.");
            }

            boolean upgraded = sections.length >= 2 && Boolean.parseBoolean(sections[1]);
            boolean extended = sections.length >= 3 && Boolean.parseBoolean(sections[2]);
            boolean splash = sections.length >= 4 && Boolean.parseBoolean(sections[3]);

            return new PotionData(potionType, upgraded, extended, splash);
        }

        private static PotionData fromItemStack19(ItemStack itemStack) {
            ItemMeta itemMeta = itemStack.getItemMeta();

            if (!(itemMeta instanceof PotionMeta)) {
                return null;
            }

            PotionMeta potionMeta = (PotionMeta) itemMeta;
            org.bukkit.potion.PotionData potionData = potionMeta.getBasePotionData();

            return new PotionData(potionData.getType(), potionData.isUpgraded(), potionData.isExtended(),
                    itemStack.getType() == Material.SPLASH_POTION);
        }

        @SuppressWarnings("deprecation")
        private static PotionData fromItemStack18(ItemStack itemStack) {
            Potion potion = Potion.fromItemStack(itemStack);
            return new PotionData(potion.getType(), potion.getLevel() > 1, potion.hasExtendedDuration(), potion.isSplash());
        }

        @Override
        public String toString() {
            return potionType.name() + ";" + upgraded + ";" + extended + ";" + splash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PotionData that = (PotionData) o;
            return upgraded == that.upgraded && extended == that.extended && splash == that.splash && potionType == that.potionType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(potionType, upgraded, extended, splash);
        }

    }

}

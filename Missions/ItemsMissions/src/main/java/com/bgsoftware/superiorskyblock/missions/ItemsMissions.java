package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.missions.common.Requirements;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings("unused")
public final class ItemsMissions extends Mission<ItemsMissions.ItemsTracker> implements Listener {

    private final Map<Requirements, Integer> requiredItems = new LinkedHashMap<>();

    private SuperiorSkyblock plugin;

    @Override
    public void load(JavaPlugin plugin, ConfigurationSection section) throws MissionLoadException {
        this.plugin = (SuperiorSkyblock) plugin;

        ConfigurationSection requiredItemsSection = section.getConfigurationSection("required-items");

        if (requiredItemsSection == null)
            throw new MissionLoadException("You must have the \"required-items\" section in the config.");

        for (String key : requiredItemsSection.getKeys(false)) {
            List<String> itemStacks = section.getStringList("required-items." + key + ".types");
            int requiredAmount = section.getInt("required-items." + key + ".amount");
            requiredItems.put(new Requirements(itemStacks), requiredAmount);
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);

        setClearMethod(itemsTracker -> itemsTracker.itemsTracker.clear());
    }


    public void unload() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public double getProgress(SuperiorPlayer superiorPlayer) {
        double progress = 0.0;

        Player player = superiorPlayer.asPlayer();

        if (player == null)
            return progress;

        Inventory inventory = player.getInventory();
        Map<ItemStack, Integer> countedItems = countItems(inventory);

        int totalRequiredAmount = 0;
        int totalItemAmount = 0;

        ItemsTracker itemsTracker = getOrCreate(superiorPlayer, s -> new ItemsTracker());

        if (itemsTracker == null)
            return 0.0;

        for (Map.Entry<Requirements, Integer> entry : requiredItems.entrySet()) {
            Requirements requirement = entry.getKey();
            int requiredAmount = entry.getValue();

            int itemAmount = 0;
            for (String item : requirement) {
                try {
                    //Get the amount of the item.
                    Material type = Material.valueOf(item.contains(":") ? item.split(":")[0] : item);
                    short data = Short.parseShort(item.contains(":") ? item.split(":")[1] : "0");
                    int currentItemAmount = countedItems.get(new ItemStack(type, 1, data));

                    //Making sure to not exceed the required item amount
                    if (itemAmount + currentItemAmount > requiredAmount)
                        currentItemAmount = requiredAmount - itemAmount;

                    //Summing the amount to a global variable
                    itemAmount += currentItemAmount;

                    //Adding the item to a list, so we can remove it later.
                    itemsTracker.track(new ItemStack(type, currentItemAmount, data));
                } catch (Exception ignored) {
                }
            }

            totalRequiredAmount += requiredAmount;
            totalItemAmount += itemAmount;
        }

        progress = Math.max(progress, (double) totalItemAmount / totalRequiredAmount);

        return progress;
    }

    @Override
    public int getProgressValue(SuperiorPlayer superiorPlayer) {
        Player player = superiorPlayer.asPlayer();

        if (player == null)
            return 0;

        Inventory inventory = player.getInventory();
        Map<ItemStack, Integer> countedItems = countItems(inventory);

        int totalItemAmount = 0;

        ItemsTracker itemsTracker = getOrCreate(superiorPlayer, s -> new ItemsTracker());

        if (itemsTracker == null)
            return 0;

        for (Map.Entry<Requirements, Integer> entry : requiredItems.entrySet()) {
            Requirements requirement = entry.getKey();
            int requiredAmount = entry.getValue();

            int itemAmount = 0;
            for (String item : requirement) {
                try {
                    //Get the amount of the item.
                    Material type = Material.valueOf(item.contains(":") ? item.split(":")[0] : item);
                    short data = Short.parseShort(item.contains(":") ? item.split(":")[1] : "0");
                    int currentItemAmount = countedItems.get(new ItemStack(type, 1, data));

                    //Making sure to not exceed the required item amount
                    if (itemAmount + currentItemAmount > requiredAmount)
                        currentItemAmount = requiredAmount - itemAmount;

                    //Summing the amount to a global variable
                    itemAmount += currentItemAmount;

                    //Adding the item to a list, so we can remove it later.
                    itemsTracker.track(new ItemStack(type, currentItemAmount, data));
                } catch (Exception ignored) {
                }
            }

            totalItemAmount += itemAmount;
        }

        return totalItemAmount;
    }

    @Override
    public void onComplete(SuperiorPlayer superiorPlayer) {
        clearData(superiorPlayer);
        getProgress(superiorPlayer);
        ItemsTracker itemsTracker = get(superiorPlayer);

        if (itemsTracker == null)
            return;

        Player player = superiorPlayer.asPlayer();
        assert player != null;

        removeItems(player.getInventory(), itemsTracker.getItems().toArray(new ItemStack[0]));

        onCompleteFail(superiorPlayer);
    }

    @Override
    public void onCompleteFail(SuperiorPlayer superiorPlayer) {
        clearData(superiorPlayer);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(PlayerPickupItemEvent e) {
        ItemStack itemStack = e.getItem().getItemStack();
        handleItemPickup(e.getPlayer(), e.getItem().getItemStack());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player) || e.getClickedInventory() == null ||
                e.getClickedInventory().getType() != InventoryType.CHEST || e.getClick() != ClickType.SHIFT_LEFT)
            return;

        handleItemPickup((Player) e.getWhoClicked(), e.getCurrentItem());
    }

    private void handleItemPickup(Player player, @Nullable ItemStack itemStack) {
        if (!isMissionItem(itemStack))
            return;

        SuperiorPlayer superiorPlayer = this.plugin.getPlayers().getSuperiorPlayer(player);

        if (!this.plugin.getMissions().canCompleteNoProgress(superiorPlayer, this))
            return;

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> superiorPlayer.runIfOnline(unused -> {
            if (canComplete(superiorPlayer))
                this.plugin.getMissions().rewardMission(this, superiorPlayer, true);
        }), 2L);
    }

    private boolean isMissionItem(@Nullable ItemStack itemStack) {
        if (itemStack == null)
            return false;

        for (Requirements requirement : requiredItems.keySet()) {
            if (requirement.contains(itemStack.getType().name()))
                return true;
        }

        return false;
    }

    private static Map<ItemStack, Integer> countItems(Inventory inventory) {
        Map<ItemStack, Integer> countedItems = new HashMap<>();

        Arrays.stream(inventory.getContents()).filter(Objects::nonNull).forEach(itemStack -> {
            ItemStack key = itemStack.clone();
            key.setAmount(1);
            countedItems.put(key, countedItems.getOrDefault(key, 0) + itemStack.getAmount());
        });

        return countedItems;
    }

    private static void removeItems(PlayerInventory inventory, ItemStack... itemStacks) {
        Collection<ItemStack> leftOvers = inventory.removeItem(itemStacks).values();

        if (leftOvers.isEmpty())
            return;

        // We try to remove the item from the offhand as well.

        ItemStack offHandItem;

        try {
            offHandItem = inventory.getItem(40);
        } catch (Exception ignored) {
            return;
        }

        if (offHandItem != null && offHandItem.getType() != Material.AIR) {
            for (ItemStack itemStack : leftOvers) {
                if (offHandItem.isSimilar(itemStack)) {
                    if (offHandItem.getAmount() > itemStack.getAmount()) {
                        offHandItem.setAmount(offHandItem.getAmount() - itemStack.getAmount());
                    } else {
                        itemStack.setAmount(itemStack.getAmount() - offHandItem.getAmount());
                        inventory.setItem(40, new ItemStack(Material.AIR));
                    }
                }
            }
        }
    }

    public static class ItemsTracker {

        private final Set<ItemStack> itemsTracker = new HashSet<>();

        void track(ItemStack itemStack) {
            itemsTracker.add(itemStack.clone());
        }

        Set<ItemStack> getItems() {
            return itemsTracker;
        }

    }

}

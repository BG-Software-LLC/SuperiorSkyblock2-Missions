package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings("unused")
public final class ItemsMissions extends Mission<ItemsMissions.ItemsTracker> implements Listener {

    private static final SuperiorSkyblock superiorSkyblock = SuperiorSkyblockAPI.getSuperiorSkyblock();

    private final Map<List<String>, Integer> requiredItems = new HashMap<>();

    private JavaPlugin plugin;

    @Override
    public void load(JavaPlugin plugin, ConfigurationSection section) throws MissionLoadException {
        this.plugin = plugin;

        if(!section.contains("required-items"))
            throw new MissionLoadException("You must have the \"required-items\" section in the config.");

        for(String key : section.getConfigurationSection("required-items").getKeys(false)){
            List<String> itemStacks = section.getStringList("required-items." + key + ".types");
            int requiredAmount = section.getInt("required-items." + key + ".amount");
            requiredItems.put(itemStacks, requiredAmount);
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);

        setClearMethod(itemsTracker -> itemsTracker.itemsTracker.clear());
    }

    @Override
    public double getProgress(SuperiorPlayer superiorPlayer) {
        double progress = 0.0;

        Inventory inventory = superiorPlayer.asPlayer().getInventory();
        Map<ItemStack, Integer> countedItems = countItems(inventory);

        int totalRequiredAmount = 0;
        int totalItemAmount = 0;

        ItemsTracker itemsTracker = getOrCreate(superiorPlayer, s -> new ItemsTracker());

        if(itemsTracker == null)
            return 0.0;

        for (List<String> requiredItem : requiredItems.keySet()) {
            int requiredAmount = requiredItems.get(requiredItem);
            int itemAmount = 0;
            for(String item : requiredItem){
                try {
                    //Get the amount of the item.
                    Material type = Material.valueOf(item.contains(":") ? item.split(":")[0] : item);
                    short data = Short.parseShort(item.contains(":") ? item.split(":")[1] : "0");
                    int currentItemAmount = countedItems.get(new ItemStack(type, 1, data));

                    //Making sure to not exceed the required item amount
                    if(itemAmount + currentItemAmount > requiredAmount)
                        currentItemAmount = requiredAmount - itemAmount;

                    //Summing the amount to a global variable
                    itemAmount += currentItemAmount;

                    //Adding the item to a list, so we can remove it later.
                    itemsTracker.track(new ItemStack(type, currentItemAmount, data));
                }catch(Exception ignored){}
            }

            totalRequiredAmount += requiredAmount;
            totalItemAmount += itemAmount;
        }

        progress = Math.max(progress, (double) totalItemAmount / totalRequiredAmount);

        return progress;
    }

    @Override
    public int getProgressValue(SuperiorPlayer superiorPlayer) {
        Inventory inventory = superiorPlayer.asPlayer().getInventory();
        Map<ItemStack, Integer> countedItems = countItems(inventory);

        int totalItemAmount = 0;

        ItemsTracker itemsTracker = getOrCreate(superiorPlayer, s -> new ItemsTracker());

        if(itemsTracker == null)
            return 0;

        for (List<String> requiredItem : requiredItems.keySet()) {
            int requiredAmount = requiredItems.get(requiredItem);
            int itemAmount = 0;
            for(String item : requiredItem){
                try {
                    //Get the amount of the item.
                    Material type = Material.valueOf(item.contains(":") ? item.split(":")[0] : item);
                    short data = Short.parseShort(item.contains(":") ? item.split(":")[1] : "0");
                    int currentItemAmount = countedItems.get(new ItemStack(type, 1, data));

                    //Making sure to not exceed the required item amount
                    if(itemAmount + currentItemAmount > requiredAmount)
                        currentItemAmount = requiredAmount - itemAmount;

                    //Summing the amount to a global variable
                    itemAmount += currentItemAmount;

                    //Adding the item to a list, so we can remove it later.
                    itemsTracker.track(new ItemStack(type, currentItemAmount, data));
                }catch(Exception ignored){}
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

        if(itemsTracker == null)
            return;

        removeItems(superiorPlayer.asPlayer().getInventory(), itemsTracker.getItems().toArray(new ItemStack[0]));

        onCompleteFail(superiorPlayer);
    }

    @Override
    public void onCompleteFail(SuperiorPlayer superiorPlayer) {
        clearData(superiorPlayer);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(PlayerPickupItemEvent e){
        ItemStack itemStack = e.getItem().getItemStack();
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(e.getPlayer());

        if(!isMissionItem(itemStack) || !superiorSkyblock.getMissions().canCompleteNoProgress(superiorPlayer, this))
            return;

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if(canComplete(superiorPlayer))
                SuperiorSkyblockAPI.getSuperiorSkyblock().getMissions().rewardMission(this, superiorPlayer, true);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player) || e.getClickedInventory() == null ||
                e.getClickedInventory().getType() != InventoryType.CHEST || e.getClick() != ClickType.SHIFT_LEFT)
            return;

        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer((Player) e.getWhoClicked());

        if(!isMissionItem(e.getCurrentItem()) || !superiorSkyblock.getMissions().canCompleteNoProgress(superiorPlayer, this))
            return;

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if(canComplete(superiorPlayer))
                SuperiorSkyblockAPI.getSuperiorSkyblock().getMissions().rewardMission(this, superiorPlayer, true);
        }, 2L);
    }

    private Map<ItemStack, Integer> countItems(Inventory inventory){
        Map<ItemStack, Integer> countedItems = new HashMap<>();

        Arrays.stream(inventory.getContents()).filter(Objects::nonNull).forEach(itemStack -> {
            ItemStack key = itemStack.clone();
            key.setAmount(1);
            countedItems.put(key, countedItems.getOrDefault(key, 0) + itemStack.getAmount());
        });

        return countedItems;
    }

    private boolean isMissionItem(ItemStack itemStack){
        if(itemStack == null)
            return false;

        for (List<String> requiredItem : requiredItems.keySet()) {
            if(requiredItem.contains("ALL") || requiredItem.contains("all") || requiredItem.contains(itemStack.getType().name()))
                return true;
        }

        return false;
    }

    private static void removeItems(PlayerInventory inventory, ItemStack... itemStacks){
        Collection<ItemStack> leftOvers = inventory.removeItem(itemStacks).values();
        ItemStack offHandItem = null;

        try{
            offHandItem = inventory.getItem(40);
        }catch (Exception ignored){}

        if(offHandItem != null && offHandItem.getType() != Material.AIR) {
            for (ItemStack itemStack : leftOvers) {
                if(offHandItem.isSimilar(itemStack)){
                    if(offHandItem.getAmount() > itemStack.getAmount()){
                        offHandItem.setAmount(offHandItem.getAmount() - itemStack.getAmount());
                    }
                    else{
                        itemStack.setAmount(itemStack.getAmount() - offHandItem.getAmount());
                        inventory.setItem(40, new ItemStack(Material.AIR));
                    }
                }
            }
        }
    }

    public static class ItemsTracker {

        private final Set<ItemStack> itemsTracker = new HashSet<>();

        void track(ItemStack itemStack){
            itemsTracker.add(itemStack.clone());
        }

        Set<ItemStack> getItems(){
            return itemsTracker;
        }

    }

}

package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.wildstacker.api.events.BarrelUnstackEvent;
import com.bgsoftware.wildtools.api.events.CuboidWandUseEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public final class BlocksMissions extends Mission<BlocksMissions.BlocksTracker> implements Listener {

    private static final SuperiorSkyblock superiorSkyblock = SuperiorSkyblockAPI.getSuperiorSkyblock();

    private static final Pattern percentagePattern = Pattern.compile("(.*)\\{percentage_(.+?)}(.*)"),
            valuePattern = Pattern.compile("(.*)\\{value_(.+?)}(.*)");

    private static final Map<Location, Material> placedBlocks = new HashMap<>();

    private final Map<List<String>, Integer> requiredBlocks = new HashMap<>();

    private boolean onlyNatural, blocksPlacement, replaceBlocks;
    private JavaPlugin plugin;

    @Override
    public void load(JavaPlugin plugin, ConfigurationSection section) throws MissionLoadException {
        this.plugin = plugin;

        if (!section.contains("required-blocks"))
            throw new MissionLoadException("You must have the \"required-blocks\" section in the config.");

        for (String key : section.getConfigurationSection("required-blocks").getKeys(false)) {
            List<String> blocks = section.getStringList("required-blocks." + key + ".types");
            int requiredAmount = section.getInt("required-blocks." + key + ".amount");
            requiredBlocks.put(blocks, requiredAmount);
        }

        //resetAfterFinish = section.getBoolean("reset-after-finish", false);
        onlyNatural = section.getBoolean("only-natural-blocks", false);
        blocksPlacement = section.getBoolean("blocks-placement", false);
        replaceBlocks = section.getBoolean("blocks-replace", false);

        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getPluginManager().isPluginEnabled("WildStacker"))
                Bukkit.getPluginManager().registerEvents(new WildStackerListener(), plugin);
            if (Bukkit.getPluginManager().isPluginEnabled("WildTools")) {
                Bukkit.getPluginManager().registerEvents(new WildToolsListener(), plugin);
            }
        }, 1L);

        setClearMethod(blocksTracker -> blocksTracker.blocksTracker.clear());
    }

    @Override
    public double getProgress(SuperiorPlayer superiorPlayer) {
        BlocksTracker blocksTracker = get(superiorPlayer);

        if (blocksTracker == null)
            return 0.0;

        int requiredBlocks = 0;
        int interactions = 0;

        for (Map.Entry<List<String>, Integer> requiredBlock : this.requiredBlocks.entrySet()) {
            requiredBlocks += requiredBlock.getValue();
            interactions += Math.min(blocksTracker.getBlocks(requiredBlock.getKey()), requiredBlock.getValue());
        }

        return (double) interactions / requiredBlocks;
    }

    @Override
    public int getProgressValue(SuperiorPlayer superiorPlayer) {
        BlocksTracker blocksTracker = get(superiorPlayer);

        if (blocksTracker == null)
            return 0;

        int interactions = 0;

        for (Map.Entry<List<String>, Integer> requiredBlock : this.requiredBlocks.entrySet())
            interactions += Math.min(blocksTracker.getBlocks(requiredBlock.getKey()), requiredBlock.getValue());

        return interactions;
    }

    @Override
    public void onComplete(SuperiorPlayer superiorPlayer) {
        clearData(superiorPlayer);
    }

    @Override
    public void onCompleteFail(SuperiorPlayer superiorPlayer) {

    }

    @Override
    public void saveProgress(ConfigurationSection section) {
        for (Map.Entry<SuperiorPlayer, BlocksTracker> entry : entrySet()) {
            String uuid = entry.getKey().getUniqueId().toString();
            for (Map.Entry<String, Integer> brokenEntry : entry.getValue().blocksTracker.entrySet()) {
                section.set(uuid + "." + brokenEntry.getKey(), brokenEntry.getValue());
            }
        }
    }

    @Override
    public void loadProgress(ConfigurationSection section) {
        for (String uuid : section.getKeys(false)) {
            BlocksTracker blocksTracker = new BlocksTracker();
            UUID playerUUID = UUID.fromString(uuid);
            SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(playerUUID);

            insertData(superiorPlayer, blocksTracker);

            for (String key : section.getConfigurationSection(uuid).getKeys(false)) {
                blocksTracker.blocksTracker.put(key, section.getInt(uuid + "." + key));
            }
        }
    }

    @Override
    public void formatItem(SuperiorPlayer superiorPlayer, ItemStack itemStack) {
        BlocksTracker blocksTracker = getOrCreate(superiorPlayer, s -> new BlocksTracker());

        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta.hasDisplayName())
            itemMeta.setDisplayName(parsePlaceholders(blocksTracker, itemMeta.getDisplayName()));

        if (itemMeta.hasLore()) {
            List<String> lore = new ArrayList<>();
            for (String line : itemMeta.getLore())
                lore.add(parsePlaceholders(blocksTracker, line));
            itemMeta.setLore(lore);
        }

        itemStack.setItemMeta(itemMeta);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!blocksPlacement)
            handleBlockBreak(e.getBlock(), e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        handleBlockPistonMove(e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        handleBlockPistonMove(e.getBlocks(), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        placedBlocks.put(e.getBlock().getLocation(), e.getBlock().getType());

        if (blocksPlacement)
            return;

        Material blockType = e.getBlock().getType();
        short blockData = 0;

        try {
            //noinspection deprecation
            blockData = e.getBlock().getData();
        } catch (Throwable ignored) {
        }

        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(e.getPlayer());

        boolean blockReplace = placedBlocks.get(e.getBlock().getLocation()) == e.getBlock().getType();

        if (isBarrel(e.getBlock()) || (!replaceBlocks && blockReplace) || !isMissionBlock(blockType, blockData) ||
                !superiorSkyblock.getMissions().hasAllRequiredMissions(superiorPlayer, this))
            return;

        handleBlockTrack(e.getPlayer(), blockType, blockData, getBlockAmount(e.getPlayer(), e.getBlock()));
    }

    private class WildStackerListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBarrelUnstack(BarrelUnstackEvent e) {
            if (onlyNatural || !(e.getUnstackSource() instanceof Player))
                return;

            Block block = e.getBarrel().getBlock();
            ItemStack barrelItem = e.getBarrel().getBarrelItem(1);
            Material blockType = barrelItem.getType();

            if (!isMissionBlock(blockType, barrelItem.getDurability()))
                return;

            handleBlockTrack((Player) e.getUnstackSource(), blockType, barrelItem.getDurability(), e.getAmount());
        }

    }

    private class WildToolsListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onCuboidUse(CuboidWandUseEvent e) {
            for (Location location : e.getBlocks())
                handleBlockBreak(location.getBlock(), e.getPlayer());
        }

    }

    private void handleBlockBreak(Block block, Player player) {
        Location location = block.getLocation();
        Material blockType = block.getType();
        short blockData = 0;

        try {
            //noinspection deprecation
            blockData = block.getData();
        } catch (Throwable ignored) {
        }

        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);

        Material placedBlockMaterial = placedBlocks.get(location);

        if (placedBlockMaterial != null)
            Bukkit.getScheduler().runTaskLater(plugin, () -> placedBlocks.remove(location), 2L);

        boolean placedByPlayer = placedBlockMaterial == blockType;

        if (isBarrel(block) || (onlyNatural && placedByPlayer) || !isMissionBlock(blockType, blockData) ||
                !superiorSkyblock.getMissions().hasAllRequiredMissions(superiorPlayer, this))
            return;

        handleBlockTrack(player, blockType, blockData, getBlockAmount(player, block));
    }

    private void handleBlockPistonMove(List<Block> blockList, BlockFace direction) {
        for (Block block : blockList) {
            Material blockMaterial = placedBlocks.remove(block.getLocation());
            if (blockMaterial != null) {
                placedBlocks.put(block.getRelative(direction).getLocation(), blockMaterial);
            }
        }
    }

    private void handleBlockTrack(Player player, Material blockType, short data, int amount) {
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);

        if (!isMissionBlock(blockType, data) || !superiorSkyblock.getMissions().canCompleteNoProgress(superiorPlayer, this))
            return;

        BlocksTracker blocksTracker = getOrCreate(superiorPlayer, s -> new BlocksTracker());
        blocksTracker.trackBlock(blockType, data, amount);

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> superiorPlayer.runIfOnline(_player -> {
            if (canComplete(superiorPlayer))
                superiorSkyblock.getMissions().rewardMission(this, superiorPlayer, true);
        }), 2L);
    }

    private int getBlockAmount(Player player, Block block) {
        int blockAmount = superiorSkyblock.getGrid().getBlockAmount(block);

        // When sneaking, you'll break 64 from the stack. Otherwise, 1.
        int amount = !player.isSneaking() ? 1 : 64;

        // Fix amount so it won't be more than the stack's amount
        amount = Math.min(amount, blockAmount);

        return amount;
    }

    private boolean isBarrel(Block block) {
        return Bukkit.getPluginManager().isPluginEnabled("WildStacker") &&
                com.bgsoftware.wildstacker.api.WildStackerAPI.getWildStacker().getSystemManager().isStackedBarrel(block);
    }

    private boolean isMissionBlock(Material blockType, short data) {
        if (blockType == null)
            return false;

        for (List<String> requiredBlock : requiredBlocks.keySet()) {
            if (requiredBlock.contains(blockType.name()) || requiredBlock.contains(blockType.name() + ":" + data) ||
                    requiredBlock.contains("all") || requiredBlock.contains("ALL"))
                return true;
        }

        return false;
    }

    private String parsePlaceholders(BlocksTracker blocksTracker, String line) {
        Matcher matcher = percentagePattern.matcher(line);

        if (matcher.matches()) {
            String requiredBlock = matcher.group(2).toUpperCase();
            Optional<Map.Entry<List<String>, Integer>> entry = requiredBlocks.entrySet().stream().filter(e -> e.getKey().contains(requiredBlock)).findAny();
            if (entry.isPresent()) {
                line = line.replace("{percentage_" + matcher.group(2) + "}",
                        "" + (blocksTracker.getBlocks(Collections.singletonList(requiredBlock)) * 100) / entry.get().getValue());
            }
        }

        if ((matcher = valuePattern.matcher(line)).matches()) {
            String requiredBlock = matcher.group(2).toUpperCase();
            Optional<Map.Entry<List<String>, Integer>> entry = requiredBlocks.entrySet().stream().filter(e -> e.getKey().contains(requiredBlock)).findFirst();
            if (entry.isPresent()) {
                line = line.replace("{value_" + matcher.group(2) + "}",
                        "" + blocksTracker.getBlocks(Collections.singletonList(requiredBlock)));
            }
        }

        return ChatColor.translateAlternateColorCodes('&', line);
    }

    public class BlocksTracker {

        private final Map<String, Integer> blocksTracker = new HashMap<>();

        void trackBlock(Material blockType, short data, int amount) {
            String material = blockType.name();

            if (requiredBlocks.entrySet().stream().anyMatch(entry -> entry.getKey().contains(blockType.name() + ":" + data)))
                material = blockType.name() + ":" + data;

            blocksTracker.put(material, amount + blocksTracker.getOrDefault(material, 0));
        }

        int getBlocks(List<String> blocks) {
            int amount = 0;

            for (String block : blocks) {
                if (block.equalsIgnoreCase("ALL")) {
                    for (int value : blocksTracker.values())
                        amount += value;
                } else {
                    amount += blocksTracker.getOrDefault(block, 0);
                }
            }

            return amount;
        }

    }

}

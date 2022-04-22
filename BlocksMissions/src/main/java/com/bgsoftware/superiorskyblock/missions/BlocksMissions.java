package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.missions.blocks.tracker.BlocksTracker;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public final class BlocksMissions extends Mission<BlocksTracker> implements Listener {

    private static final SuperiorSkyblock superiorSkyblock = SuperiorSkyblockAPI.getSuperiorSkyblock();

    private static final Pattern percentagePattern = Pattern.compile("(.*)\\{percentage_(.+?)}(.*)"),
            valuePattern = Pattern.compile("(.*)\\{value_(.+?)}(.*)");

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

        setClearMethod(BlocksTracker::clear);
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
            interactions += Math.min(blocksTracker.getBlocksCount(requiredBlock.getKey()), requiredBlock.getValue());
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
            interactions += Math.min(blocksTracker.getBlocksCount(requiredBlock.getKey()), requiredBlock.getValue());

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
            for (Map.Entry<String, Integer> blockCountEntry : entry.getValue().getBlockCounts().entrySet()) {
                section.set(uuid + ".counts." + blockCountEntry.getKey(), blockCountEntry.getValue());
            }
            for (Map.Entry<String, Map<Long, Set<Integer>>> trackedEntry : entry.getValue()
                    .getBlocks(BlocksTracker.TrackingType.PLACED_BLOCKS).entrySet()) {
                for (Map.Entry<Long, Set<Integer>> trackedBlocksEntry : trackedEntry.getValue().entrySet()) {
                    section.set(uuid + ".tracked.placed." + trackedEntry.getKey() + "." + trackedBlocksEntry.getKey(),
                            new ArrayList<>(trackedBlocksEntry.getValue()));
                }
            }
            for (Map.Entry<String, Map<Long, Set<Integer>>> trackedEntry : entry.getValue()
                    .getBlocks(BlocksTracker.TrackingType.BROKEN_BLOCKS).entrySet()) {
                for (Map.Entry<Long, Set<Integer>> trackedBlocksEntry : trackedEntry.getValue().entrySet()) {
                    section.set(uuid + ".tracked.broken." + trackedEntry.getKey() + "." + trackedBlocksEntry.getKey(),
                            new ArrayList<>(trackedBlocksEntry.getValue()));
                }
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

            if (section.contains(uuid + ".counts") || section.contains(uuid + ".tracked")) {
                ConfigurationSection countsSection = section.getConfigurationSection(uuid + ".counts");
                if (countsSection != null) {
                    for (String key : countsSection.getKeys(false)) {
                        blocksTracker.loadBlockCount(key, countsSection.getInt(key));
                    }
                }
                ConfigurationSection trackedPlacedSection = section.getConfigurationSection(uuid + ".tracked.placed");
                if (trackedPlacedSection != null) {
                    for (String worldName : trackedPlacedSection.getKeys(false)) {
                        for (String chunkKey : trackedPlacedSection.getConfigurationSection(worldName).getKeys(false)) {
                            List<Integer> trackedBlocks = trackedPlacedSection.getIntegerList(worldName + "." + chunkKey);
                            try {
                                blocksTracker.loadTrackedBlocks(BlocksTracker.TrackingType.PLACED_BLOCKS,
                                        Bukkit.getWorld(worldName), Long.parseLong(chunkKey), trackedBlocks);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
                ConfigurationSection trackedBrokenSection = section.getConfigurationSection(uuid + ".tracked.broken");
                if (trackedBrokenSection != null) {
                    for (String worldName : trackedBrokenSection.getKeys(false)) {
                        for (String chunkKey : trackedBrokenSection.getConfigurationSection(worldName).getKeys(false)) {
                            List<Integer> trackedBlocks = trackedBrokenSection.getIntegerList(worldName + "." + chunkKey);
                            try {
                                blocksTracker.loadTrackedBlocks(BlocksTracker.TrackingType.BROKEN_BLOCKS,
                                        Bukkit.getWorld(worldName), Long.parseLong(chunkKey), trackedBlocks);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            } else {
                for (String key : section.getConfigurationSection(uuid).getKeys(false)) {
                    blocksTracker.loadBlockCount(key, section.getInt(uuid + "." + key));
                }
            }
        }
    }

    @Override
    public void formatItem(SuperiorPlayer superiorPlayer, ItemStack itemStack) {
        BlocksTracker blocksTracker = getOrCreate(superiorPlayer, s -> new BlocksTracker());

        if (blocksTracker == null)
            return;

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
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(e.getPlayer());

        BlocksTracker blocksTracker = getOrCreate(superiorPlayer, s -> new BlocksTracker());

        if (blocksTracker == null)
            return;

        BlockInfo blockInfo = new BlockInfo(e.getBlock());

        if (blocksPlacement) {
            if (!replaceBlocks && isMissionBlock(blockInfo) &&
                    blocksTracker.isTracked(BlocksTracker.TrackingType.PLACED_BLOCKS, e.getBlock())) {
                blocksTracker.untrackBlock(BlocksTracker.TrackingType.PLACED_BLOCKS, e.getBlock());
                blocksTracker.countBlock(blockInfo.getBlockKey(), getBlockAmount(e.getPlayer(), e.getBlock()) * -1);
                blocksTracker.countBlock("ALL", getBlockAmount(e.getPlayer(), e.getBlock()) * -1);
            }
            return;
        }

        handleBlockBreak(e.getBlock(), superiorPlayer, blockInfo);

        blocksTracker.untrackBlock(BlocksTracker.TrackingType.PLACED_BLOCKS, e.getBlock());
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
        if (!onlyNatural && !blocksPlacement)
            return;

        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(e.getPlayer());

        BlocksTracker blocksTracker = getOrCreate(superiorPlayer, s -> new BlocksTracker());

        if (blocksTracker == null)
            return;

        blocksTracker.untrackBlock(BlocksTracker.TrackingType.BROKEN_BLOCKS, e.getBlock());

        BlockInfo blockInfo = new BlockInfo(e.getBlock());

        if (!blocksPlacement) {
            if (!replaceBlocks) {
                if (isMissionBlock(blockInfo))
                    blocksTracker.trackBlock(BlocksTracker.TrackingType.PLACED_BLOCKS, e.getBlock());
            }
            return;
        }

        if (isBarrel(e.getBlock()) || !isMissionBlock(blockInfo) ||
                !superiorSkyblock.getMissions().hasAllRequiredMissions(superiorPlayer, this))
            return;

        handleBlockTrack(BlocksTracker.TrackingType.PLACED_BLOCKS, e.getPlayer(), e.getBlock(),
                getBlockAmount(e.getPlayer(), e.getBlock()));
    }

    private class WildStackerListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBarrelUnstack(BarrelUnstackEvent e) {
            if (onlyNatural || !(e.getUnstackSource() instanceof Player))
                return;

            Block block = e.getBarrel().getBlock();
            ItemStack barrelItem = e.getBarrel().getBarrelItem(1);
            Material blockType = barrelItem.getType();

            BlockInfo blockInfo = new BlockInfo(blockType, barrelItem.getDurability());

            if (!isMissionBlock(blockInfo))
                return;

            handleBlockTrack(BlocksTracker.TrackingType.BROKEN_BLOCKS, (Player) e.getUnstackSource(), block, blockInfo, e.getAmount());
        }

    }

    private class WildToolsListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onCuboidUse(CuboidWandUseEvent e) {
            SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(e.getPlayer());

            BlocksTracker blocksTracker = getOrCreate(superiorPlayer, s -> new BlocksTracker());

            if (blocksTracker == null)
                return;

            for (Location location : e.getBlocks()) {
                Block block = location.getBlock();
                handleBlockBreak(block, e.getPlayer());
                blocksTracker.untrackBlock(BlocksTracker.TrackingType.PLACED_BLOCKS, block);
            }
        }

    }

    private void handleBlockBreak(Block block, Player player) {
        handleBlockBreak(block, SuperiorSkyblockAPI.getPlayer(player), null);
    }

    private void handleBlockBreak(Block block, SuperiorPlayer superiorPlayer, @Nullable BlockInfo blockInfo) {
        Location location = block.getLocation();

        if (blockInfo == null)
            blockInfo = new BlockInfo(block);

        boolean placedByPlayer = entrySet().stream().anyMatch(entry -> entry.getValue()
                .isTracked(BlocksTracker.TrackingType.PLACED_BLOCKS, block));

        if (isBarrel(block) || (onlyNatural && placedByPlayer) || !isMissionBlock(blockInfo) ||
                !superiorSkyblock.getMissions().hasAllRequiredMissions(superiorPlayer, this))
            return;

        handleBlockTrack(BlocksTracker.TrackingType.BROKEN_BLOCKS, superiorPlayer, block, blockInfo,
                getBlockAmount(superiorPlayer.asPlayer(), block));
    }

    private void handleBlockPistonMove(List<Block> blockList, BlockFace direction) {
        for (Block block : blockList) {
            entrySet().stream().filter(entry -> entry.getValue().isTracked(BlocksTracker.TrackingType.PLACED_BLOCKS, block))
                    .map(Map.Entry::getValue).findAny().ifPresent(blocksTracker -> {
                        if (blocksTracker.untrackBlock(BlocksTracker.TrackingType.PLACED_BLOCKS, block))
                            blocksTracker.trackBlock(BlocksTracker.TrackingType.PLACED_BLOCKS, block.getRelative(direction));
                    });
        }
    }

    private void handleBlockTrack(BlocksTracker.TrackingType trackingType, Player player, Block block, int amount) {
        handleBlockTrack(trackingType, SuperiorSkyblockAPI.getPlayer(player), block, new BlockInfo(block), amount);
    }

    private void handleBlockTrack(BlocksTracker.TrackingType trackingType, Player player, Block block,
                                  BlockInfo blockInfo, int amount) {
        handleBlockTrack(trackingType, SuperiorSkyblockAPI.getPlayer(player), block, blockInfo, amount);
    }

    private void handleBlockTrack(BlocksTracker.TrackingType trackingType, SuperiorPlayer superiorPlayer, Block block,
                                  BlockInfo blockInfo, int amount) {
        if (!isMissionBlock(blockInfo) || !superiorSkyblock.getMissions().canCompleteNoProgress(superiorPlayer, this))
            return;

        BlocksTracker blocksTracker = getOrCreate(superiorPlayer, s -> new BlocksTracker());

        if (blocksTracker == null)
            return;

        blocksTracker.trackBlock(trackingType, block);

        blocksTracker.countBlock(blockInfo.getBlockKey(), amount);
        blocksTracker.countBlock("ALL", amount);

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

    private boolean isMissionBlock(BlockInfo blockInfo) {
        for (List<String> requiredBlock : requiredBlocks.keySet()) {
            if (requiredBlock.contains(blockInfo.blockType.name()) ||
                    requiredBlock.contains(blockInfo.blockType.name() + ":" + blockInfo.blockData) ||
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
                        "" + (blocksTracker.getBlocksCount(requiredBlock) * 100) / entry.get().getValue());
            }
        }

        if ((matcher = valuePattern.matcher(line)).matches()) {
            String requiredBlock = matcher.group(2).toUpperCase();
            Optional<Map.Entry<List<String>, Integer>> entry = requiredBlocks.entrySet().stream().filter(e -> e.getKey().contains(requiredBlock)).findFirst();
            if (entry.isPresent()) {
                line = line.replace("{value_" + matcher.group(2) + "}",
                        "" + blocksTracker.getBlocksCount(requiredBlock));
            }
        }

        return ChatColor.translateAlternateColorCodes('&', line);
    }

    private class BlockInfo {

        private final Material blockType;
        private final short blockData;

        BlockInfo(Block block) {
            this.blockType = block.getType();
            short blockData = 0;

            try {
                //noinspection deprecation
                blockData = block.getData();
            } catch (Throwable ignored) {
            }

            this.blockData = blockData;
        }

        BlockInfo(Material blockType, short blockData) {
            this.blockType = blockType;
            this.blockData = blockData;
        }

        String getBlockKey() {
            String combinedKey = blockType.name() + ":" + blockData;
            return requiredBlocks.entrySet().stream().anyMatch(entry -> entry.getKey().contains(combinedKey)) ?
                    combinedKey : blockType.name();
        }

    }

}

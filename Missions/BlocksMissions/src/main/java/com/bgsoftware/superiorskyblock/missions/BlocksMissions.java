package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.missions.blocks.BlocksTracker;
import com.bgsoftware.superiorskyblock.missions.common.Counter;
import com.bgsoftware.superiorskyblock.missions.common.DataTracker;
import com.bgsoftware.superiorskyblock.missions.common.Placeholders;
import com.bgsoftware.superiorskyblock.missions.common.Requirements;
import com.bgsoftware.wildstacker.api.WildStackerAPI;
import com.bgsoftware.wildstacker.api.events.BarrelUnstackEvent;
import com.bgsoftware.wildtools.api.events.CuboidWandUseEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BlocksMissions extends Mission<DataTracker> implements Listener {

    private static final BlocksTracker BLOCKS_TRACKER = new BlocksTracker();

    private final Map<Requirements, Integer> requiredBlocks = new LinkedHashMap<>();
    private final List<Listener> registeredListeners = new LinkedList<>();

    private boolean onlyNatural, blocksPlacement, replaceBlocks;
    private SuperiorSkyblock plugin;

    private Predicate<Block> isBarrelCheck;

    @Override
    public void load(JavaPlugin plugin, ConfigurationSection section) throws MissionLoadException {
        this.plugin = (SuperiorSkyblock) plugin;

        if (!section.contains("required-blocks"))
            throw new MissionLoadException("You must have the \"required-blocks\" section in the config.");

        for (String key : section.getConfigurationSection("required-blocks").getKeys(false)) {
            List<String> blocks = section.getStringList("required-blocks." + key + ".types");
            int requiredAmount = section.getInt("required-blocks." + key + ".amount");
            requiredBlocks.put(new Requirements(blocks), requiredAmount);
        }

        //resetAfterFinish = section.getBoolean("reset-after-finish", false);
        onlyNatural = section.getBoolean("only-natural-blocks", false);
        blocksPlacement = section.getBoolean("blocks-placement", false);
        replaceBlocks = section.getBoolean("blocks-replace", false);

        registerListener(this);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getPluginManager().isPluginEnabled("WildStacker")) {
                registerListener(new WildStackerListener());
                this.isBarrelCheck = block -> WildStackerAPI.getWildStacker().getSystemManager().isStackedBarrel(block);
            } else {
                this.isBarrelCheck = block -> false;
            }

            if (Bukkit.getPluginManager().isPluginEnabled("WildTools")) {
                registerListener(new WildToolsListener());
            }
        }, 1L);

        setClearMethod(DataTracker::clear);
    }

    public void unload() {
        this.registeredListeners.forEach(HandlerList::unregisterAll);
        this.registeredListeners.clear();
    }

    @Override
    public double getProgress(SuperiorPlayer superiorPlayer) {
        DataTracker blocksCounter = get(superiorPlayer);

        if (blocksCounter == null)
            return 0.0;

        int requiredBlocks = 0;
        int interactions = 0;

        for (Map.Entry<Requirements, Integer> requiredBlock : this.requiredBlocks.entrySet()) {
            requiredBlocks += requiredBlock.getValue();
            interactions += Math.min(blocksCounter.getCounts(requiredBlock.getKey()), requiredBlock.getValue());
        }

        return (double) interactions / requiredBlocks;
    }

    @Override
    public int getProgressValue(SuperiorPlayer superiorPlayer) {
        DataTracker blocksCounter = get(superiorPlayer);

        if (blocksCounter == null)
            return 0;

        int interactions = 0;

        for (Map.Entry<Requirements, Integer> requiredBlock : this.requiredBlocks.entrySet())
            interactions += Math.min(blocksCounter.getCounts(requiredBlock.getKey()), requiredBlock.getValue());

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
        for (Map.Entry<SuperiorPlayer, DataTracker> entry : entrySet()) {
            String uuid = entry.getKey().getUniqueId().toString();
            for (Map.Entry<String, Counter> blockCountEntry : entry.getValue().getCounts().entrySet()) {
                section.set(uuid + ".counts." + blockCountEntry.getKey(), blockCountEntry.getValue().get());
            }
        }

        BLOCKS_TRACKER.getBlocks(BlocksTracker.TrackingType.PLACED_BLOCKS).forEach((worldName, trackedData) -> {
            trackedData.forEach((chunkKey, locations) -> {
                if (!locations.isEmpty())
                    section.set("tracked.placed." + worldName + "." + chunkKey, new ArrayList<>(locations));
            });
        });
        BLOCKS_TRACKER.getRawData(BlocksTracker.TrackingType.PLACED_BLOCKS).forEach((worldName, worldSection) -> {
            for (String chunkKey : worldSection.getKeys(false))
                section.set("tracked.placed." + worldName + "." + chunkKey, worldSection.getIntegerList(chunkKey));
        });
        BLOCKS_TRACKER.getBlocks(BlocksTracker.TrackingType.BROKEN_BLOCKS).forEach((worldName, trackedData) -> {
            trackedData.forEach((chunkKey, locations) -> {
                if (!locations.isEmpty())
                    section.set("tracked.broken." + worldName + "." + chunkKey, new ArrayList<>(locations));
            });
        });
        BLOCKS_TRACKER.getRawData(BlocksTracker.TrackingType.BROKEN_BLOCKS).forEach((worldName, worldSection) -> {
            for (String chunkKey : worldSection.getKeys(false))
                section.set("tracked.broken." + worldName + "." + chunkKey, worldSection.getIntegerList(chunkKey));
        });
    }

    @Override
    public void loadProgress(ConfigurationSection section) {
        for (String uuid : section.getKeys(false)) {
            DataTracker blocksCounter = new DataTracker();
            UUID playerUUID;

            try {
                playerUUID = UUID.fromString(uuid);
            } catch (Exception error) {
                // tracked section probably, skipping.
                continue;
            }

            SuperiorPlayer superiorPlayer = this.plugin.getPlayers().getSuperiorPlayer(playerUUID);

            insertData(superiorPlayer, blocksCounter);

            if (section.contains(uuid + ".counts")) {
                ConfigurationSection countsSection = section.getConfigurationSection(uuid + ".counts");
                if (countsSection != null) {
                    for (String key : countsSection.getKeys(false)) {
                        blocksCounter.load(key, countsSection.getInt(key));
                    }
                }
            } else {
                ConfigurationSection countsSection = section.getConfigurationSection(uuid);
                if (countsSection != null) {
                    for (String key : countsSection.getKeys(false)) {
                        blocksCounter.load(key, section.getInt(uuid + "." + key));
                    }
                }
            }

            loadTrackedBlocks(section.getConfigurationSection(uuid + ".tracked.placed"), section.getConfigurationSection(uuid + ".tracked.broken"));
        }

        loadTrackedBlocks(section.getConfigurationSection("tracked.placed"), section.getConfigurationSection("tracked.broken"));
    }

    private static void loadTrackedBlocks(@Nullable ConfigurationSection trackedPlacedSection,
                                          @Nullable ConfigurationSection trackedBrokenSection) {
        if (trackedPlacedSection != null) {
            for (String worldName : trackedPlacedSection.getKeys(false)) {
                ConfigurationSection worldSection = trackedPlacedSection.getConfigurationSection(worldName);
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    BLOCKS_TRACKER.loadTrackedBlocks(BlocksTracker.TrackingType.PLACED_BLOCKS, worldName, worldSection);
                } else {
                    BLOCKS_TRACKER.loadTrackedBlocks(BlocksTracker.TrackingType.PLACED_BLOCKS, world, worldSection);
                }
            }
        }

        if (trackedBrokenSection != null) {
            for (String worldName : trackedBrokenSection.getKeys(false)) {
                ConfigurationSection worldSection = trackedBrokenSection.getConfigurationSection(worldName);
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    BLOCKS_TRACKER.loadTrackedBlocks(BlocksTracker.TrackingType.BROKEN_BLOCKS, worldName, worldSection);
                } else {
                    BLOCKS_TRACKER.loadTrackedBlocks(BlocksTracker.TrackingType.BROKEN_BLOCKS, world, worldSection);
                }
            }
        }
    }

    @Override
    public void formatItem(SuperiorPlayer superiorPlayer, ItemStack itemStack) {
        DataTracker blocksCounter = getOrCreate(superiorPlayer, s -> new DataTracker());

        if (blocksCounter == null)
            return;

        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta == null)
            return;

        if (itemMeta.hasDisplayName())
            itemMeta.setDisplayName(Placeholders.parsePlaceholders(this.requiredBlocks, blocksCounter, itemMeta.getDisplayName()));

        if (itemMeta.hasLore()) {
            List<String> lore = new ArrayList<>();
            for (String line : Objects.requireNonNull(itemMeta.getLore()))
                lore.add(Placeholders.parsePlaceholders(this.requiredBlocks, blocksCounter, line));
            itemMeta.setLore(lore);
        }

        itemStack.setItemMeta(itemMeta);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        SuperiorPlayer superiorPlayer = this.plugin.getPlayers().getSuperiorPlayer(e.getPlayer());

        DataTracker blocksCounter = getOrCreate(superiorPlayer, s -> new DataTracker());

        if (blocksCounter == null)
            return;

        BlockInfo blockInfo = new BlockInfo(e.getBlock());

        if (blocksPlacement) {
            if (!replaceBlocks && isMissionBlock(blockInfo)) {
                if (BLOCKS_TRACKER.untrackBlock(BlocksTracker.TrackingType.PLACED_BLOCKS, e.getBlock()))
                    blocksCounter.track(blockInfo.getBlockKey(), getBlockAmount(e.getPlayer(), e.getBlock()) * -1);
            }
            return;
        }

        handleBlockBreak(e.getBlock(), superiorPlayer, blockInfo);

        BLOCKS_TRACKER.untrackBlock(BlocksTracker.TrackingType.PLACED_BLOCKS, e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!e.getBlocks().isEmpty())
            handleBlockPistonMove(new LinkedList<>(e.getBlocks()), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!e.getBlocks().isEmpty())
            handleBlockPistonMove(new LinkedList<>(e.getBlocks()), e.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!onlyNatural && !blocksPlacement)
            return;

        BLOCKS_TRACKER.untrackBlock(BlocksTracker.TrackingType.BROKEN_BLOCKS, e.getBlock());

        BlockInfo blockInfo = new BlockInfo(e.getBlock());

        if (this.isBarrelCheck.test(e.getBlock()) || !isMissionBlock(blockInfo))
            return;

        if (!blocksPlacement) {
            if (!replaceBlocks)
                BLOCKS_TRACKER.trackBlock(BlocksTracker.TrackingType.PLACED_BLOCKS, e.getBlock());
            return;
        }

        SuperiorPlayer superiorPlayer = this.plugin.getPlayers().getSuperiorPlayer(e.getPlayer());

        handleBlockTrack(BlocksTracker.TrackingType.PLACED_BLOCKS, superiorPlayer, e.getBlock(), blockInfo,
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

            SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer((Player) e.getUnstackSource());
            handleBlockTrack(BlocksTracker.TrackingType.BROKEN_BLOCKS, superiorPlayer, block, blockInfo, e.getAmount());
        }

    }

    private class WildToolsListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onCuboidUse(CuboidWandUseEvent e) {
            for (Location location : e.getBlocks()) {
                Block block = location.getBlock();
                handleBlockBreak(block, e.getPlayer());
                BLOCKS_TRACKER.untrackBlock(BlocksTracker.TrackingType.PLACED_BLOCKS, block);
            }
        }

    }

    private void handleBlockBreak(Block block, Player player) {
        handleBlockBreak(block, this.plugin.getPlayers().getSuperiorPlayer(player), null);
    }

    private void handleBlockBreak(Block block, SuperiorPlayer superiorPlayer, @Nullable BlockInfo blockInfo) {
        if (blockInfo == null)
            blockInfo = new BlockInfo(block);

        if (this.isBarrelCheck.test(block) || !isMissionBlock(blockInfo))
            return;

        if (onlyNatural && BLOCKS_TRACKER.isTracked(BlocksTracker.TrackingType.PLACED_BLOCKS, block))
            return;

        handleBlockTrack(BlocksTracker.TrackingType.BROKEN_BLOCKS, superiorPlayer, block, blockInfo,
                getBlockAmount(superiorPlayer.asPlayer(), block));
    }

    private void handleBlockPistonMove(LinkedList<Block> blockList, BlockFace direction) {
        blockList.removeIf(block -> !isMissionBlock(new BlockInfo(block)) ||
                !BLOCKS_TRACKER.isTracked(BlocksTracker.TrackingType.PLACED_BLOCKS, block));

        if (blockList.isEmpty())
            return;

        List<Block> movedBlocks = blockList.stream()
                .map(block -> block.getRelative(direction))
                .collect(Collectors.toList());

        List<Block> addedBlocks = new LinkedList<>(movedBlocks);
        addedBlocks.removeAll(blockList);

        List<Block> removedBlocks = new LinkedList<>(blockList);
        removedBlocks.removeAll(movedBlocks);

        removedBlocks.forEach(block -> BLOCKS_TRACKER.untrackBlock(BlocksTracker.TrackingType.PLACED_BLOCKS, block));
        addedBlocks.forEach(block -> BLOCKS_TRACKER.trackBlock(BlocksTracker.TrackingType.PLACED_BLOCKS, block));
    }

    private void handleBlockTrack(BlocksTracker.TrackingType trackingType, SuperiorPlayer superiorPlayer, Block block,
                                  BlockInfo blockInfo, int amount) {
        if (!this.plugin.getMissions().canCompleteNoProgress(superiorPlayer, this))
            return;

        DataTracker blocksCounter = getOrCreate(superiorPlayer, s -> new DataTracker());

        BLOCKS_TRACKER.trackBlock(trackingType, block);

        blocksCounter.track(blockInfo.getBlockKey(), amount);

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> superiorPlayer.runIfOnline(_player -> {
            if (canComplete(superiorPlayer))
                this.plugin.getMissions().rewardMission(this, superiorPlayer, true);
        }), 2L);
    }

    private int getBlockAmount(Player player, Block block) {
        int blockAmount = this.plugin.getStackedBlocks().getStackedBlockAmount(block);

        // When sneaking, you'll break 64 from the stack. Otherwise, 1.
        int amount = !player.isSneaking() ? 1 : 64;

        // Fix amount so it won't be more than the stack's amount
        amount = Math.min(amount, blockAmount);

        return amount;
    }

    private boolean isMissionBlock(BlockInfo blockInfo) {
        for (Requirements requirementsList : requiredBlocks.keySet()) {
            if (requirementsList.contains(blockInfo.blockType.name()) ||
                    requirementsList.contains(blockInfo.blockType.name() + ":" + blockInfo.blockData))
                return true;
        }

        return false;
    }

    private void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        this.registeredListeners.add(listener);
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

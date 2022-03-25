package com.bgsoftware.superiorskyblock.missions.blocks.tracker;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BlocksTracker {

    private final EnumMap<TrackingType, Map<UUID, BlocksTrackingComponent>> trackingComponentMap = new EnumMap<>(TrackingType.class);
    private final Map<String, Integer> trackedBlockCounts = new HashMap<>();

    public void trackBlock(TrackingType trackingType, Block block) {
        this.trackBlock(trackingType, block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    private boolean trackBlock(TrackingType trackingType, World world, int blockX, int blockY, int blockZ) {
        return getComponent(trackingType, world).add(blockX, blockY, blockZ);
    }

    public boolean untrackBlock(TrackingType trackingType, Block block) {
        return this.untrackBlock(trackingType, block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    private boolean untrackBlock(TrackingType trackingType, World world, int blockX, int blockY, int blockZ) {
        return getComponent(trackingType, world).remove(blockX, blockY, blockZ);
    }

    public boolean isTracked(TrackingType trackingType, Block block) {
        return this.isTracked(trackingType, block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    public boolean isTracked(TrackingType trackingType, World world, int blockX, int blockY, int blockZ) {
        return getComponent(trackingType, world).contains(blockX, blockY, blockZ);
    }

    public void countBlock(String blockKey, int amount) {
        blockKey = blockKey.toUpperCase();
        int blockCount = getBlocksCount(blockKey);
        this.trackedBlockCounts.put(blockKey, blockCount + amount);
    }

    public void clear() {
        this.trackingComponentMap.clear();
    }

    public void loadBlockCount(String blockKey, int amount) {
        this.trackedBlockCounts.put(blockKey, amount);
    }

    public void loadTrackedBlocks(TrackingType trackingType, World world, long chunkKey,
                                  Collection<Integer> trackedBlocks) {
        getComponent(trackingType, world).loadBlocks(chunkKey, trackedBlocks);
    }

    public int getBlocksCount(String blockKey) {
        return this.trackedBlockCounts.getOrDefault(blockKey, 0);
    }

    public int getBlocksCount(List<String> blocks) {
        int amount = 0;

        for (String block : blocks) {
            if (block.equalsIgnoreCase("ALL")) {
                return getBlocksCount(block);
            } else {
                amount += getBlocksCount(block);
            }
        }

        return amount;
    }

    public Map<String, Map<Long, Set<Integer>>> getBlocks(TrackingType trackingType) {
        Map<String, Map<Long, Set<Integer>>> blocks = new HashMap<>();

        Map<UUID, BlocksTrackingComponent> trackingComponentMap = this.trackingComponentMap.get(trackingType);

        if(trackingComponentMap != null) {
            for (BlocksTrackingComponent trackingComponent : trackingComponentMap.values()) {
                Map<Long, Set<Integer>> componentBlocks = trackingComponent.getBlocks();
                blocks.put(trackingComponent.getWorld().getName(), componentBlocks);
            }
        }

        return Collections.unmodifiableMap(blocks);
    }

    public Map<String, Integer> getBlockCounts() {
        return Collections.unmodifiableMap(this.trackedBlockCounts);
    }

    private BlocksTrackingComponent getComponent(TrackingType trackingType, World world) {
        return trackingComponentMap
                .computeIfAbsent(trackingType, t -> new HashMap<>())
                .computeIfAbsent(world.getUID(), uuid -> new BlocksTrackingComponent(world));
    }

    public enum TrackingType {

        PLACED_BLOCKS,
        BROKEN_BLOCKS

    }

}

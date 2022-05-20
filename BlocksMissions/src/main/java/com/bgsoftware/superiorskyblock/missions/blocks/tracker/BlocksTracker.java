package com.bgsoftware.superiorskyblock.missions.blocks.tracker;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public final class BlocksTracker {

    private final EnumMap<TrackingType, Map<UUID, BlocksTrackingComponent>> trackingComponentMap = new EnumMap<>(TrackingType.class);

    public void trackBlock(TrackingType trackingType, Block block) {
        getComponent(trackingType, block.getWorld()).add(block.getX(), block.getY(), block.getZ());
    }

    public void untrackBlock(TrackingType trackingType, Block block) {
        ifComponentExists(trackingType, block.getWorld(), component -> component.remove(block.getX(), block.getY(), block.getZ()));
    }

    public boolean isTracked(TrackingType trackingType, Block block) {
        return ifComponentExists(trackingType, block.getWorld(), false,
                component -> component.contains(block.getX(), block.getY(), block.getZ()));
    }

    public void loadTrackedBlocks(TrackingType trackingType, World world, long chunkKey,
                                  Collection<Integer> trackedBlocks) {
        getComponent(trackingType, world).loadBlocks(chunkKey, trackedBlocks);
    }

    public Map<String, Map<Long, Set<Integer>>> getBlocks(TrackingType trackingType) {
        Map<String, Map<Long, Set<Integer>>> blocks = new HashMap<>();

        Map<UUID, BlocksTrackingComponent> trackingComponentMap = this.trackingComponentMap.get(trackingType);

        if (trackingComponentMap != null) {
            for (BlocksTrackingComponent trackingComponent : trackingComponentMap.values()) {
                Map<Long, Set<Integer>> componentBlocks = trackingComponent.getBlocks();
                if (!componentBlocks.isEmpty())
                    blocks.put(trackingComponent.getWorld().getName(), componentBlocks);
            }
        }

        return Collections.unmodifiableMap(blocks);
    }

    private BlocksTrackingComponent getComponent(TrackingType trackingType, World world) {
        return trackingComponentMap
                .computeIfAbsent(trackingType, t -> new HashMap<>())
                .computeIfAbsent(world.getUID(), uuid -> new BlocksTrackingComponent(world));
    }

    private <R> R ifComponentExists(TrackingType trackingType, World world, R def, Function<BlocksTrackingComponent, R> function) {
        Map<UUID, BlocksTrackingComponent> trackingTypeComponents = trackingComponentMap.get(trackingType);
        if (trackingTypeComponents != null) {
            BlocksTrackingComponent trackingComponent = trackingTypeComponents.get(world.getUID());
            if (trackingComponent != null)
                return function.apply(trackingComponent);
        }

        return def;
    }

    private void ifComponentExists(TrackingType trackingType, World world, Consumer<BlocksTrackingComponent> consumer) {
        Map<UUID, BlocksTrackingComponent> trackingTypeComponents = trackingComponentMap.get(trackingType);
        if (trackingTypeComponents != null) {
            BlocksTrackingComponent trackingComponent = trackingTypeComponents.get(world.getUID());
            if (trackingComponent != null)
                consumer.accept(trackingComponent);
        }
    }

    public enum TrackingType {

        PLACED_BLOCKS,
        BROKEN_BLOCKS

    }

}

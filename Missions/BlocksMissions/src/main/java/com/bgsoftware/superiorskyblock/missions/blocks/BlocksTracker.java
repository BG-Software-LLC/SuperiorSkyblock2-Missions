package com.bgsoftware.superiorskyblock.missions.blocks;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class BlocksTracker {

    private final EnumMap<TrackingType, Map<String, BlocksTrackingComponent>> trackingComponentMap = new EnumMap<>(TrackingType.class);
    private final EnumMap<TrackingType, Map<String, TrackedBlocksData>> rawData = new EnumMap<>(TrackingType.class);

    public void trackBlock(TrackingType trackingType, Block block) {
        getComponent(trackingType, block.getWorld()).add(block.getX(), block.getY(), block.getZ());
    }

    public boolean untrackBlock(TrackingType trackingType, Block block) {
        return ifComponentExists(trackingType, block.getWorld(), false,
                component -> component.remove(block.getX(), block.getY(), block.getZ()));
    }

    public boolean isTracked(TrackingType trackingType, Block block) {
        return ifComponentExists(trackingType, block.getWorld(), false,
                component -> component.contains(block.getX(), block.getY(), block.getZ()));
    }

    public void loadTrackedBlocks(TrackingType trackingType, String worldName, ConfigurationSection section) {
        this.rawData.computeIfAbsent(trackingType, t -> new HashMap<>()).put(worldName, new TrackedBlocksData(section));
    }

    public Map<String, Map<Long, ChunkBitSet>> getBlocks(TrackingType trackingType) {
        Map<String, Map<Long, ChunkBitSet>> blocks = new HashMap<>();

        Map<String, BlocksTrackingComponent> trackingComponentMap = this.trackingComponentMap.get(trackingType);

        if (trackingComponentMap != null) {
            trackingComponentMap.forEach((worldName, trackingComponent) -> {
                Map<Long, ChunkBitSet> componentBlocks = trackingComponent.getBlocks();
                if (!componentBlocks.isEmpty())
                    blocks.put(worldName, componentBlocks);
            });
        }

        return Collections.unmodifiableMap(blocks);
    }

    public Map<String, TrackedBlocksData> getRawData(TrackingType trackingType) {
        Map<String, TrackedBlocksData> rawData = this.rawData.get(trackingType);
        return rawData == null ? Collections.emptyMap() : rawData;
    }

    private BlocksTrackingComponent getComponent(TrackingType trackingType, World world) {
        return trackingComponentMap.computeIfAbsent(trackingType, t -> new HashMap<>())
                .computeIfAbsent(world.getName(), uuid -> {
                    BlocksTrackingComponent trackingComponent = new BlocksTrackingComponent(world);

                    if (!this.rawData.isEmpty()) {
                        Map<String, TrackedBlocksData> rawData = this.rawData.get(trackingType);

                        if (rawData != null) {
                            TrackedBlocksData trackedBlocksData = rawData.remove(world.getName());
                            if (trackedBlocksData != null) {
                                trackingComponent.loadBlocks(trackedBlocksData);
                                if (rawData.isEmpty())
                                    this.rawData.remove(trackingType);
                            }
                        }
                    }

                    return trackingComponent;
                });
    }

    private <R> R ifComponentExists(TrackingType trackingType, World world, R def, Function<BlocksTrackingComponent, R> function) {
        String worldName = world.getName();

        Map<String, BlocksTrackingComponent> trackingTypeComponents = trackingComponentMap.get(trackingType);
        if (trackingTypeComponents != null) {
            BlocksTrackingComponent trackingComponent = trackingTypeComponents.get(worldName);
            if (trackingComponent != null)
                return function.apply(trackingComponent);
        }

        if (!this.rawData.isEmpty()) {
            Map<String, TrackedBlocksData> rawData = this.rawData.get(trackingType);
            if (rawData != null) {
                TrackedBlocksData trackedBlocksData = rawData.remove(worldName);
                if (trackedBlocksData != null) {
                    BlocksTrackingComponent trackingComponent = new BlocksTrackingComponent(world);
                    trackingComponent.loadBlocks(trackedBlocksData);
                    this.trackingComponentMap.computeIfAbsent(trackingType, i -> new HashMap<>())
                            .put(worldName, trackingComponent);
                    if (rawData.isEmpty())
                        this.rawData.remove(trackingType);
                    return function.apply(trackingComponent);
                }
            }
        }

        return def;
    }

    public enum TrackingType {

        PLACED_BLOCKS,
        BROKEN_BLOCKS

    }

}

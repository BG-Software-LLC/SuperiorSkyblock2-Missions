package com.bgsoftware.superiorskyblock.missions.blocks;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class BlocksTracker {

    private final EnumMap<TrackingType, Map<String, BlocksTrackingComponent>> trackingComponentMap = new EnumMap<>(TrackingType.class);
    private final EnumMap<TrackingType, Map<String, ConfigurationSection>> rawData = new EnumMap<>(TrackingType.class);

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
        Map<String, ConfigurationSection> rawData = this.rawData.computeIfAbsent(trackingType, t -> new HashMap<>());
        rawData.put(worldName, section);
    }

    public void loadTrackedBlocks(TrackingType trackingType, World world, ConfigurationSection worldSection) {
        getComponent(trackingType, world).loadBlocks(worldSection);
    }

    public Map<String, Map<Long, Set<Integer>>> getBlocks(TrackingType trackingType) {
        Map<String, Map<Long, Set<Integer>>> blocks = new HashMap<>();

        Map<String, BlocksTrackingComponent> trackingComponentMap = this.trackingComponentMap.get(trackingType);

        if (trackingComponentMap != null) {
            trackingComponentMap.forEach((worldName, trackingComponent) -> {
                Map<Long, Set<Integer>> componentBlocks = trackingComponent.getBlocks();
                if (!componentBlocks.isEmpty())
                    blocks.put(worldName, componentBlocks);
            });
        }

        return Collections.unmodifiableMap(blocks);
    }

    public Map<String, ConfigurationSection> getRawData(TrackingType trackingType) {
        Map<String, ConfigurationSection> rawData = this.rawData.get(trackingType);
        return rawData == null ? Collections.emptyMap() : rawData;
    }

    private BlocksTrackingComponent getComponent(TrackingType trackingType, World world) {
        BlocksTrackingComponent blocksTrackingComponent = trackingComponentMap
                .computeIfAbsent(trackingType, t -> new HashMap<>())
                .computeIfAbsent(world.getName(), uuid -> new BlocksTrackingComponent(world));

        if (!this.rawData.isEmpty()) {
            Map<String, ConfigurationSection> rawData = this.rawData.get(trackingType);

            if (rawData != null) {
                ConfigurationSection worldsSection = rawData.remove(world.getName());
                if (worldsSection != null) {
                    blocksTrackingComponent.loadBlocks(worldsSection);
                    if (rawData.isEmpty())
                        this.rawData.remove(trackingType);
                }
            }
        }

        return blocksTrackingComponent;
    }

    private <R> R ifComponentExists(TrackingType trackingType, World world, R def, Function<BlocksTrackingComponent, R> function) {
        Map<String, BlocksTrackingComponent> trackingTypeComponents = trackingComponentMap.get(trackingType);
        if (trackingTypeComponents != null) {
            BlocksTrackingComponent trackingComponent = trackingTypeComponents.get(world.getName());
            if (trackingComponent != null)
                return function.apply(trackingComponent);
        }

        return def;
    }

    private void ifComponentExists(TrackingType trackingType, World world, Consumer<BlocksTrackingComponent> consumer) {
        Map<String, BlocksTrackingComponent> trackingTypeComponents = trackingComponentMap.get(trackingType);
        if (trackingTypeComponents != null) {
            BlocksTrackingComponent trackingComponent = trackingTypeComponents.get(world.getName());
            if (trackingComponent != null)
                consumer.accept(trackingComponent);
        }
    }

    public enum TrackingType {

        PLACED_BLOCKS,
        BROKEN_BLOCKS

    }

}

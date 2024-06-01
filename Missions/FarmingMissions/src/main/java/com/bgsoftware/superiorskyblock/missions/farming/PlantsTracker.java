package com.bgsoftware.superiorskyblock.missions.farming;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlantsTracker {

    private final Map<String, PlantsTrackingComponent> plantsTracker = new HashMap<>();
    private Map<String, TrackedPlantsData> rawData = null;
    private Map<String, Map<UUID, List<PlantPosition>>> legacyRawData = null;

    public void track(Block block, UUID placer) {
        track(block.getWorld(), block.getX(), block.getY(), block.getZ(), placer);
    }

    public void track(World world, int x, int y, int z, UUID placer) {
        loadRawData(world);
        plantsTracker.computeIfAbsent(world.getName(), s -> new PlantsTrackingComponent(world))
                .track(x, y, z, placer);
    }

    public void untrack(Block block) {
        untrack(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    public void untrack(World world, int x, int y, int z) {
        loadRawData(world);
        PlantsTrackingComponent trackingComponent = this.plantsTracker.get(world.getName());
        if (trackingComponent != null)
            trackingComponent.untrack(x, y, z);
    }

    @Nullable
    public UUID getPlacer(Location location) {
        World world = location.getWorld();
        return world == null ? null : getPlacer(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Nullable
    public UUID getPlacer(World world, int x, int y, int z) {
        loadRawData(world);
        PlantsTrackingComponent trackingComponent = this.plantsTracker.get(world.getName());
        return trackingComponent == null ? null : trackingComponent.getPlacer(x, y, z);
    }

    public void load(String worldName, long chunkKey, List<Integer> blocks, UUID placer) {
        if (this.rawData == null)
            this.rawData = new HashMap<>();
        TrackedPlantsData trackedPlantsData = this.rawData.computeIfAbsent(worldName, w -> new TrackedPlantsData());
        blocks.forEach(block -> trackedPlantsData.track(chunkKey, block, placer));
    }

    public void loadLegacy(String worldName, PlantPosition plant, UUID placer) {
        if (this.legacyRawData == null)
            this.legacyRawData = new HashMap<>();
        this.legacyRawData.computeIfAbsent(worldName, w -> new LinkedHashMap<>())
                .computeIfAbsent(placer, i -> new LinkedList<>()).add(plant);
    }

    public Map<String, Map<Long, Map<Integer, UUID>>> getPlants() {
        Map<String, Map<Long, Map<Integer, UUID>>> plants = new LinkedHashMap<>();

        plantsTracker.forEach((worldName, component) ->
                plants.put(worldName, component.getPlants()));

        if (this.rawData != null) {
            this.rawData.forEach((worldName, trackingData) ->
                    plants.computeIfAbsent(worldName, n -> new HashMap<>()).putAll(trackingData.getPlants()));
        }

        return Collections.unmodifiableMap(plants);
    }

    public Map<String, Map<UUID, List<PlantPosition>>> getLegacyPlants() {
        return this.legacyRawData == null ? Collections.emptyMap() : Collections.unmodifiableMap(this.legacyRawData);
    }

    private void loadRawData(World world) {
        String worldName = world.getName();

        if (this.rawData != null) {
            TrackedPlantsData rawDataForWorld = this.rawData.remove(worldName);
            if (rawDataForWorld != null) {
                plantsTracker.put(worldName, new PlantsTrackingComponent(world, rawDataForWorld));

                if (this.rawData.isEmpty())
                    this.rawData = null;
            }
        }

        if (this.legacyRawData != null) {
            Map<UUID, List<PlantPosition>> legacyRawDataForWorld = this.legacyRawData.remove(worldName);
            if (legacyRawDataForWorld != null) {
                PlantsTrackingComponent plantsTrackingComponent = new PlantsTrackingComponent(world);
                legacyRawDataForWorld.forEach((placer, plants) -> {
                    plants.forEach(plant -> plantsTrackingComponent.track(plant.getX(), plant.getY(), plant.getZ(), placer));
                });
                this.plantsTracker.put(worldName, plantsTrackingComponent);

                if (this.legacyRawData.isEmpty())
                    this.legacyRawData = null;
            }
        }
    }

}

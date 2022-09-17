package com.bgsoftware.superiorskyblock.missions.blocks.tracker;

import com.bgsoftware.common.reflection.ReflectMethod;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BlocksTrackingComponent {

    private static final ReflectMethod<Integer> WORLD_GET_MIN_HEIGHT = new ReflectMethod<>(World.class, "getMinHeight");

    // Key represents chunk's coords
    // Value represents all blocks broken in that chunk
    private final Map<Long, Set<Integer>> TRACKED_BLOCKS = new HashMap<>();

    private final int worldMinHeight;

    public BlocksTrackingComponent(World world) {
        this(!WORLD_GET_MIN_HEIGHT.isValid() ? 0 : WORLD_GET_MIN_HEIGHT.invoke(world));
    }

    public BlocksTrackingComponent(int worldMinHeight) {
        this.worldMinHeight = worldMinHeight;
    }

    public int getWorldMinHeight() {
        return worldMinHeight;
    }

    public boolean add(int blockX, int blockY, int blockZ) {
        long chunkKey = serializeChunk(blockX >> 4, blockZ >> 4);
        return TRACKED_BLOCKS.computeIfAbsent(chunkKey, key -> new HashSet<>())
                .add(serializeLocation(blockX, blockY, blockZ));
    }

    public boolean remove(int blockX, int blockY, int blockZ) {
        long chunkKey = serializeChunk(blockX >> 4, blockZ >> 4);
        return Optional.ofNullable(TRACKED_BLOCKS.get(chunkKey))
                .map(set -> set.remove(serializeLocation(blockX, blockY, blockZ)))
                .orElse(false);
    }

    public boolean contains(int blockX, int blockY, int blockZ) {
        long chunkKey = serializeChunk(blockX >> 4, blockZ >> 4);
        return Optional.ofNullable(TRACKED_BLOCKS.get(chunkKey))
                .map(set -> set.contains(serializeLocation(blockX, blockY, blockZ)))
                .orElse(false);
    }

    public void loadBlocks(ConfigurationSection worldSection) {
        for (String chunkKey : worldSection.getKeys(false)) {
            List<Integer> trackedBlocks = worldSection.getIntegerList(chunkKey);
            try {
                TRACKED_BLOCKS.put(Long.parseLong(chunkKey), new HashSet<>(trackedBlocks));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public Map<Long, Set<Integer>> getBlocks() {
        return Collections.unmodifiableMap(TRACKED_BLOCKS);
    }

    private int serializeLocation(int blockX, int blockY, int blockZ) {
        int chunkMinX = blockX >> 4 << 4;
        int chunkMinZ = blockZ >> 4 << 4;


        byte relativeX = (byte) (blockX - chunkMinX);
        short relativeY = (short) (blockY - this.worldMinHeight);
        byte relativeZ = (byte) (blockZ - chunkMinZ);

        return (relativeY << 16) | (relativeX << 8) | relativeZ;
    }

    private static long serializeChunk(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ;
    }

}

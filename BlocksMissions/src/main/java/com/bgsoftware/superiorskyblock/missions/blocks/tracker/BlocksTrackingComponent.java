package com.bgsoftware.superiorskyblock.missions.blocks.tracker;

import com.bgsoftware.common.reflection.ReflectMethod;
import org.bukkit.World;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BlocksTrackingComponent {

    private static final ReflectMethod<Integer> WORLD_GET_MIN_HEIGHT = new ReflectMethod<>(World.class, "getMinHeight");

    // Key represents chunk's coords
    // Value represents all blocks broken in that chunk
    private final Map<Long, Set<Integer>> TRACKED_BLOCKS = new HashMap<>();

    private final World world;
    private final int worldMinHeight;

    public BlocksTrackingComponent(World world) {
        this.world = world;
        this.worldMinHeight = !WORLD_GET_MIN_HEIGHT.isValid() ? 0 : WORLD_GET_MIN_HEIGHT.invoke(world);
    }

    public World getWorld() {
        return world;
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

    public void loadBlocks(long chunkKey, Collection<Integer> trackedBlocks) {
        TRACKED_BLOCKS.put(chunkKey, new HashSet<>(trackedBlocks));
    }

    public Map<Long, Set<Integer>> getBlocks() {
        return Collections.unmodifiableMap(TRACKED_BLOCKS);
    }

    private int serializeLocation(int blockX, int blockY, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;


        byte relativeX = (byte) (blockX - chunkX);
        short relativeY = (short) (blockY - this.worldMinHeight);
        byte relativeZ = (byte) (blockZ - chunkZ);

        return (relativeY << 16) | (relativeX << 8) | relativeZ;
    }

    private static long serializeChunk(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ;
    }

    private static long deserializeChunk(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ;
    }

}

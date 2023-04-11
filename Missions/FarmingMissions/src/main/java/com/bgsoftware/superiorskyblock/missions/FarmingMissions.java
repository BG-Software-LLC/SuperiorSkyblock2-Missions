package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.missions.common.DataTracker;
import com.bgsoftware.superiorskyblock.missions.common.Placeholders;
import com.bgsoftware.superiorskyblock.missions.common.Requirements;
import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FarmingMissions extends Mission<DataTracker> implements Listener {

    private static final BlockFace[] NEARBY_BLOCKS = new BlockFace[]{
            BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH
    };

    private static final Map<String, Integer> MAXIMUM_AGES = new ImmutableMap.Builder<String, Integer>()
            .put("CARROTS", 7)
            .put("CARROT", 7)
            .put("CROPS", 7)
            .put("WHEAT_SEEDS", 7)
            .put("WHEAT", 7)
            .put("POTATO", 7)
            .put("POTATOES", 7)
            .put("BEETROOT_SEEDS", 3)
            .put("BEETROOTS", 3)
            .put("COCOA", 2)
            .put("COCOA_BEANS", 2)
            .put("SWEET_BERRY_BUSH", 3)
            .build();

    private final Map<Requirements, Integer> requiredPlants = new LinkedHashMap<>();
    private final Map<BlockPosition, UUID> playerPlacedPlants = new ConcurrentHashMap<>();
    private boolean resetAfterFinish;

    private SuperiorSkyblock plugin;

    @Override
    public void load(JavaPlugin plugin, ConfigurationSection section) throws MissionLoadException {
        this.plugin = (SuperiorSkyblock) plugin;

        ConfigurationSection requiredPlantsSection = section.getConfigurationSection("required-plants");

        if (requiredPlantsSection == null)
            throw new MissionLoadException("You must have the \"required-plants\" section in the config.");

        for (String key : requiredPlantsSection.getKeys(false)) {
            List<String> requiredPlants = section.getStringList("required-plants." + key + ".types");
            int requiredAmount = section.getInt("required-plants." + key + ".amount");
            this.requiredPlants.put(new Requirements(requiredPlants), requiredAmount);
        }

        resetAfterFinish = section.getBoolean("reset-after-finish", false);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public double getProgress(SuperiorPlayer superiorPlayer) {
        DataTracker farmingTracker = get(superiorPlayer);

        if (farmingTracker == null)
            return 0.0;

        int requiredPlants = 0;
        int grewPlants = 0;

        for (Map.Entry<Requirements, Integer> requiredPlant : this.requiredPlants.entrySet()) {
            requiredPlants += requiredPlant.getValue();
            grewPlants += Math.min(farmingTracker.getCounts(requiredPlant.getKey()), requiredPlant.getValue());
        }

        return (double) grewPlants / requiredPlants;
    }

    @Override
    public int getProgressValue(SuperiorPlayer superiorPlayer) {
        DataTracker farmingTracker = get(superiorPlayer);

        if (farmingTracker == null)
            return 0;

        int kills = 0;

        for (Map.Entry<Requirements, Integer> requiredPlant : this.requiredPlants.entrySet())
            kills += Math.min(farmingTracker.getCounts(requiredPlant.getKey()), requiredPlant.getValue());

        return kills;
    }

    @Override
    public void onComplete(SuperiorPlayer superiorPlayer) {
        if (resetAfterFinish)
            clearData(superiorPlayer);
    }

    @Override
    public void onCompleteFail(SuperiorPlayer superiorPlayer) {

    }

    @Override
    public void saveProgress(ConfigurationSection section) {
        for (Map.Entry<SuperiorPlayer, DataTracker> entry : entrySet()) {
            String uuid = entry.getKey().getUniqueId().toString();
            entry.getValue().getCounts().forEach((plant, count) ->
                    section.set("grown-plants." + uuid + "." + plant, count.get()));
        }
        for (Map.Entry<BlockPosition, UUID> placedBlock : playerPlacedPlants.entrySet()) {
            section.set("placed-plants." + placedBlock.getKey().serialize(), placedBlock.getValue().toString());
        }
    }

    @Override
    public void loadProgress(ConfigurationSection section) {
        ConfigurationSection grownPlants = section.getConfigurationSection("grown-plants");
        if (grownPlants != null) {
            for (String uuid : grownPlants.getKeys(false)) {
                DataTracker farmingTracker = new DataTracker();
                UUID playerUUID = UUID.fromString(uuid);
                SuperiorPlayer superiorPlayer = this.plugin.getPlayers().getSuperiorPlayer(playerUUID);

                insertData(superiorPlayer, farmingTracker);

                for (String key : grownPlants.getConfigurationSection(uuid).getKeys(false)) {
                    farmingTracker.load(key, grownPlants.getInt(uuid + "." + key));
                }
            }
        }

        ConfigurationSection placedPlants = section.getConfigurationSection("placed-plants");
        if (placedPlants != null) {
            for (String locationKey : placedPlants.getKeys(false)) {
                BlockPosition blockPosition = BlockPosition.deserialize(locationKey);
                try {
                    if (blockPosition != null)
                        playerPlacedPlants.put(blockPosition, UUID.fromString(placedPlants.getString(locationKey)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    @Override
    public void formatItem(SuperiorPlayer superiorPlayer, ItemStack itemStack) {
        DataTracker farmingTracker = getOrCreate(superiorPlayer, s -> new DataTracker());

        if (farmingTracker == null)
            return;

        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta == null)
            return;

        if (itemMeta.hasDisplayName())
            itemMeta.setDisplayName(Placeholders.parsePlaceholders(this.requiredPlants, farmingTracker, itemMeta.getDisplayName()));

        if (itemMeta.hasLore()) {
            List<String> lore = new ArrayList<>();
            for (String line : Objects.requireNonNull(itemMeta.getLore()))
                lore.add(Placeholders.parsePlaceholders(this.requiredPlants, farmingTracker, line));
            itemMeta.setLore(lore);
        }

        itemStack.setItemMeta(itemMeta);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        String blockTypeName = e.getBlock().getType().name();

        switch (blockTypeName) {
            case "PUMPKIN_STEM":
                blockTypeName = "PUMPKIN";
                break;
            case "MELON_STEM":
                blockTypeName = "MELON";
                break;
            case "BAMBOO_SAPLING":
                blockTypeName = "BAMBOO";
                break;
        }

        if (!isMissionPlant(blockTypeName))
            return;

        UUID placerUUID = getPlacerUUID(e.getPlayer());

        if (placerUUID == null)
            return;

        playerPlacedPlants.put(BlockPosition.fromBlock(e.getBlock()), placerUUID);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        playerPlacedPlants.remove(BlockPosition.fromBlock(e.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(EntityExplodeEvent e) {
        for (Block block : e.blockList())
            playerPlacedPlants.remove(BlockPosition.fromBlock(block));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block block : e.getBlocks())
            playerPlacedPlants.remove(BlockPosition.fromBlock(block));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonExtendEvent e) {
        for (Block block : e.getBlocks())
            playerPlacedPlants.remove(BlockPosition.fromBlock(block));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBambooGrow(BlockSpreadEvent e) {
        handlePlantGrow(e.getBlock(), e.getNewState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlantGrow(StructureGrowEvent e) {
        Block block = e.getLocation().getBlock();
        handlePlantGrow(block, block.getState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlantGrow(BlockGrowEvent e) {
        handlePlantGrow(e.getBlock(), e.getNewState());
    }

    private void handlePlantGrow(Block plantBlock, BlockState newState) {
        String blockTypeName = newState.getType().name();

        if (!isMissionPlant(blockTypeName))
            return;

        int age = getBlockAge(newState);

        if (age < MAXIMUM_AGES.getOrDefault(blockTypeName, 0))
            return;

        Location placedBlockLocation = plantBlock.getLocation();

        switch (blockTypeName) {
            case "CACTUS":
            case "SUGAR_CANE":
            case "BAMBOO":
                placedBlockLocation = getLowestBlock(plantBlock);
                break;
            case "MELON":
            case "PUMPKIN":
                Material stemType = blockTypeName.equals("PUMPKIN") ? Material.PUMPKIN_STEM : Material.MELON_STEM;

                for (BlockFace blockFace : NEARBY_BLOCKS) {
                    Block nearbyBlock = plantBlock.getRelative(blockFace);
                    if (nearbyBlock.getType() == stemType) {
                        placedBlockLocation = nearbyBlock.getLocation();
                        break;
                    }
                }

                break;
        }

        UUID placerUUID = playerPlacedPlants.get(BlockPosition.fromLocation(placedBlockLocation));

        if (placerUUID == null)
            return;

        SuperiorPlayer superiorPlayer;

        if (getIslandMission()) {
            Island island = this.plugin.getGrid().getIslandByUUID(placerUUID);

            if (island == null)
                return;

            superiorPlayer = island.getOwner();
        } else {
            superiorPlayer = this.plugin.getPlayers().getSuperiorPlayer(placerUUID);
        }

        if (!this.plugin.getMissions().canCompleteNoProgress(superiorPlayer, this))
            return;

        DataTracker farmingTracker = getOrCreate(superiorPlayer, s -> new DataTracker());

        if (farmingTracker == null)
            return;

        farmingTracker.track(blockTypeName, 1);

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> superiorPlayer.runIfOnline(player -> {
            if (canComplete(superiorPlayer))
                this.plugin.getMissions().rewardMission(this, superiorPlayer, true);
        }), 2L);
    }

    private int getBlockAge(BlockState newState) {
        try {
            BlockData blockData = newState.getBlockData();
            return blockData instanceof Ageable ? ((Ageable) blockData).getAge() : 0;
        } catch (Throwable error) {
            // noinspection deprecation
            return newState.getRawData();
        }
    }

    private static Location getLowestBlock(Block original) {
        Block lastSimilarBlock = original.getRelative(BlockFace.DOWN);

        Material originalType = lastSimilarBlock.getType();

        while (lastSimilarBlock.getType() == originalType) {
            lastSimilarBlock = lastSimilarBlock.getRelative(BlockFace.DOWN);
        }

        return lastSimilarBlock.getLocation().add(0, 1, 0);
    }

    private UUID getPlacerUUID(Player player) {
        SuperiorPlayer superiorPlayer = this.plugin.getPlayers().getSuperiorPlayer(player);

        if (getIslandMission()) {
            Island island = superiorPlayer.getIsland();

            if (island == null)
                return null;

            return island.getUniqueId();
        } else {
            return superiorPlayer.getUniqueId();
        }
    }

    private boolean isMissionPlant(@Nullable String blockTypeName) {
        if (blockTypeName == null)
            return false;

        for (Requirements requirement : requiredPlants.keySet()) {
            if (requirement.contains(blockTypeName))
                return true;
        }

        return false;
    }

    private static final class BlockPosition {

        private final String worldName;
        private final int x;
        private final int y;
        private final int z;

        static BlockPosition fromLocation(Location location) {
            return new BlockPosition(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        static BlockPosition fromBlock(Block block) {
            return new BlockPosition(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }

        @Nullable
        static BlockPosition deserialize(String serialized) {
            String[] sections = serialized.split(";");
            if (sections.length != 4)
                return null;

            try {
                return new BlockPosition(sections[0], Integer.parseInt(sections[1]),
                        Integer.parseInt(sections[2]), Integer.parseInt(sections[3]));
            } catch (Exception ex) {
                return null;
            }
        }

        BlockPosition(String worldName, int x, int y, int z) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        String serialize() {
            return this.worldName + ";" + this.x + ";" + this.y + ";" + this.z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlockPosition that = (BlockPosition) o;
            return x == that.x && y == that.y && z == that.z && worldName.equals(that.worldName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldName, x, y, z);
        }

    }

}

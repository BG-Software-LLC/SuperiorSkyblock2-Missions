package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public final class FarmingMissions extends Mission<FarmingMissions.FarmingTracker> implements Listener {

    private static final SuperiorSkyblock superiorSkyblock = SuperiorSkyblockAPI.getSuperiorSkyblock();

    private static final Pattern percentagePattern = Pattern.compile("(.*)\\{percentage_(.+?)}(.*)"),
            valuePattern = Pattern.compile("(.*)\\{value_(.+?)}(.*)");

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
            .build();

    private JavaPlugin plugin;
    private final Map<List<String>, Integer> requiredPlants = new HashMap<>();
    private final Map<BlockPosition, UUID> playerPlacedPlants = new HashMap<>();
    private boolean resetAfterFinish;

    @Override
    public void load(JavaPlugin plugin, ConfigurationSection section) throws MissionLoadException {
        this.plugin = plugin;

        if (!section.contains("required-plants"))
            throw new MissionLoadException("You must have the \"required-plants\" section in the config.");

        for (String key : section.getConfigurationSection("required-plants").getKeys(false)) {
            List<String> requiredPlants = section.getStringList("required-plants." + key + ".types");
            int requiredAmount = section.getInt("required-plants." + key + ".amount");
            this.requiredPlants.put(requiredPlants, requiredAmount);
        }

        resetAfterFinish = section.getBoolean("reset-after-finish", false);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public double getProgress(SuperiorPlayer superiorPlayer) {
        FarmingTracker farmingTracker = get(superiorPlayer);

        if (farmingTracker == null)
            return 0.0;

        int requiredEntities = 0;
        int kills = 0;

        for (Map.Entry<List<String>, Integer> requiredPlant : this.requiredPlants.entrySet()) {
            requiredEntities += requiredPlant.getValue();
            kills += Math.min(farmingTracker.getPlants(requiredPlant.getKey()), requiredPlant.getValue());
        }

        return (double) kills / requiredEntities;
    }

    @Override
    public int getProgressValue(SuperiorPlayer superiorPlayer) {
        FarmingTracker farmingTracker = get(superiorPlayer);

        if (farmingTracker == null)
            return 0;

        int kills = 0;

        for (Map.Entry<List<String>, Integer> requiredPlant : this.requiredPlants.entrySet())
            kills += Math.min(farmingTracker.getPlants(requiredPlant.getKey()), requiredPlant.getValue());

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
        for (Map.Entry<SuperiorPlayer, FarmingTracker> entry : entrySet()) {
            String uuid = entry.getKey().getUniqueId().toString();
            for (Map.Entry<String, Integer> brokenEntry : entry.getValue().farmingTracker.entrySet()) {
                section.set("grown-plants." + uuid + "." + brokenEntry.getKey(), brokenEntry.getValue());
            }
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
                FarmingTracker farmingTracker = new FarmingTracker();
                UUID playerUUID = UUID.fromString(uuid);
                SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(playerUUID);

                insertData(superiorPlayer, farmingTracker);

                for (String key : grownPlants.getConfigurationSection(uuid).getKeys(false)) {
                    farmingTracker.farmingTracker.put(key, grownPlants.getInt(uuid + "." + key));
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
        FarmingTracker farmingTracker = getOrCreate(superiorPlayer, s -> new FarmingTracker());

        if (farmingTracker == null)
            return;

        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta.hasDisplayName())
            itemMeta.setDisplayName(parsePlaceholders(farmingTracker, itemMeta.getDisplayName()));

        if (itemMeta.hasLore()) {
            List<String> lore = new ArrayList<>();
            for (String line : itemMeta.getLore())
                lore.add(parsePlaceholders(farmingTracker, line));
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
        int age = newState.getRawData();

        if (!isMissionPlant(blockTypeName))
            return;

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
            Island island = SuperiorSkyblockAPI.getIslandByUUID(placerUUID);

            if (island == null)
                return;

            superiorPlayer = island.getOwner();
        } else {
            superiorPlayer = SuperiorSkyblockAPI.getPlayer(placerUUID);
        }

        if (!superiorSkyblock.getMissions().canCompleteNoProgress(superiorPlayer, this))
            return;

        FarmingTracker farmingTracker = getOrCreate(superiorPlayer, s -> new FarmingTracker());

        if (farmingTracker == null)
            return;

        farmingTracker.track(blockTypeName);

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> superiorPlayer.runIfOnline(player -> {
            if (canComplete(superiorPlayer))
                SuperiorSkyblockAPI.getSuperiorSkyblock().getMissions().rewardMission(this, superiorPlayer, true);
        }), 2L);
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
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);

        if (getIslandMission()) {
            Island island = superiorPlayer.getIsland();

            if (island == null)
                return null;

            return island.getUniqueId();
        } else {
            return superiorPlayer.getUniqueId();
        }
    }

    private boolean isMissionPlant(String blockTypeName) {
        if (blockTypeName == null)
            return false;

        for (List<String> requiredPlant : requiredPlants.keySet()) {
            if (requiredPlant.contains(blockTypeName) || requiredPlant.contains("all") || requiredPlant.contains("ALL"))
                return true;
        }

        return false;
    }

    private String parsePlaceholders(FarmingTracker farmingTracker, String line) {
        Matcher matcher = percentagePattern.matcher(line);

        if (matcher.matches()) {
            String requiredBlock = matcher.group(2).toUpperCase();
            Optional<Map.Entry<List<String>, Integer>> entry = requiredPlants.entrySet().stream().filter(e -> e.getKey().contains(requiredBlock)).findAny();
            if (entry.isPresent()) {
                line = line.replace("{percentage_" + matcher.group(2) + "}",
                        "" + (farmingTracker.getPlants(Collections.singletonList(requiredBlock)) * 100) / entry.get().getValue());
            }
        }

        if ((matcher = valuePattern.matcher(line)).matches()) {
            String requiredBlock = matcher.group(2).toUpperCase();
            Optional<Map.Entry<List<String>, Integer>> entry = requiredPlants.entrySet().stream().filter(e -> e.getKey().contains(requiredBlock)).findFirst();
            if (entry.isPresent()) {
                line = line.replace("{value_" + matcher.group(2) + "}",
                        "" + farmingTracker.getPlants(Collections.singletonList(requiredBlock)));
            }
        }

        return ChatColor.translateAlternateColorCodes('&', line);
    }

    public static class FarmingTracker {

        private final Map<String, Integer> farmingTracker = new HashMap<>();

        void track(String blockType) {
            int newAmount = 1 + farmingTracker.getOrDefault(blockType, 0);
            farmingTracker.put(blockType, newAmount);
        }

        int getPlants(List<String> entities) {
            int amount = 0;
            boolean all = entities.contains("ALL") || entities.contains("all");

            for (String entity : farmingTracker.keySet()) {
                if (all || entities.contains(entity))
                    amount += farmingTracker.get(entity);
            }

            return amount;
        }

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

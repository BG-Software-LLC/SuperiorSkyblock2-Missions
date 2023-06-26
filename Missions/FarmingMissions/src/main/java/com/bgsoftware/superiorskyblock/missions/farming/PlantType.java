package com.bgsoftware.superiorskyblock.missions.farming;

import org.bukkit.Material;

import java.util.EnumMap;

public enum PlantType {

    BAMBOO(0, "BAMBOO"),
    BEETROOT(3, "BEETROOT", "BEETROOTS", "BEETROOT_SEEDS"),
    CACTUS(0, "CACTUS"),
    CARROT(7, "CARROT", "CARROTS"),
    CHORUS_FLOWER(0, "CHORUS_FLOWER"),
    CHORUS_PLANT(0, "CHORUS_PLANT"),
    COCOA(2, "COCOA", "COCOA_BEANS"),
    MELON(0, "MELON", "MELON_STEM", "MELON_BLOCK"),
    POTATO(7, "POTATO", "POTATOES"),
    PUMPKIN(0, "PUMPKIN", "PUMPKIN_STEM"),
    SUGAR_CANE(0, "SUGAR_CANE", "SUGAR_CANE_BLOCK"),
    SWEET_BERRY_BUSH(3, "SWEET_BERRY_BUSH"),
    WHEAT(7, "WHEAT", "CROPS", "WHEAT_SEEDS"),

    UNKNOWN(-1);

    private static final EnumMap<Material, PlantType> MATERIALS_TO_PLANT_TYPE = new EnumMap<>(Material.class);
    private static boolean hasRegisteredPlantTypes = false;

    private final String[] materials;
    private final int maxAge;

    PlantType(int maxAge, String... listedMaterials) {
        this.maxAge = maxAge;
        this.materials = listedMaterials;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public static PlantType getByMaterial(Material material) {
        if(!hasRegisteredPlantTypes) {
            for(PlantType plantType : PlantType.values())
                registerPlantType(plantType);
            hasRegisteredPlantTypes = true;
        }

        return MATERIALS_TO_PLANT_TYPE.getOrDefault(material, UNKNOWN);
    }

    private static void registerPlantType(PlantType plantType) {
        for (String material : plantType.materials) {
            try {
                MATERIALS_TO_PLANT_TYPE.put(Material.valueOf(material), plantType);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

}

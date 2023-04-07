package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.missions.common.DataTracker;
import com.bgsoftware.superiorskyblock.missions.common.Placeholders;
import com.bgsoftware.superiorskyblock.missions.common.RequirementsList;
import com.bgsoftware.wildstacker.api.WildStackerAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class KillsMissions extends Mission<DataTracker> implements Listener {

    private final Map<RequirementsList, Integer> requiredEntities = new HashMap<>();
    private boolean resetAfterFinish;

    private SuperiorSkyblock plugin;

    private Function<LivingEntity, Integer> getEntityCount;

    @Override
    public void load(JavaPlugin plugin, ConfigurationSection section) throws MissionLoadException {
        this.plugin = (SuperiorSkyblock) plugin;

        ConfigurationSection requiredEntitiesSection = section.getConfigurationSection("required-entities");

        if (requiredEntitiesSection == null)
            throw new MissionLoadException("You must have the \"required-entities\" section in the config.");

        for (String key : requiredEntitiesSection.getKeys(false)) {
            List<String> entityTypes = section.getStringList("required-entities." + key + ".types");
            int requiredAmount = section.getInt("required-entities." + key + ".amount");
            requiredEntities.put(new RequirementsList(entityTypes), requiredAmount);
        }

        resetAfterFinish = section.getBoolean("reset-after-finish", false);

        Bukkit.getPluginManager().registerEvents(this, plugin);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getServer().getPluginManager().isPluginEnabled("WildStacker")) {
                this.getEntityCount = WildStackerAPI::getEntityAmount;
            } else {
                this.getEntityCount = entity -> 1;
            }
        }, 1L);
    }

    @Override
    public double getProgress(SuperiorPlayer superiorPlayer) {
        DataTracker killsTracker = get(superiorPlayer);

        if (killsTracker == null)
            return 0.0;

        int requiredEntities = 0;
        int kills = 0;

        for (Map.Entry<RequirementsList, Integer> requiredEntity : this.requiredEntities.entrySet()) {
            requiredEntities += requiredEntity.getValue();
            kills += Math.min(killsTracker.getCounts(requiredEntity.getKey()), requiredEntity.getValue());
        }

        return (double) kills / requiredEntities;
    }

    @Override
    public int getProgressValue(SuperiorPlayer superiorPlayer) {
        DataTracker killsTracker = get(superiorPlayer);

        if (killsTracker == null)
            return 0;

        int kills = 0;

        for (Map.Entry<RequirementsList, Integer> requiredEntity : this.requiredEntities.entrySet())
            kills += Math.min(killsTracker.getCounts(requiredEntity.getKey()), requiredEntity.getValue());

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
            entry.getValue().getCounts().forEach((killedType, count) ->
                    section.set(uuid + "." + killedType, count.get()));
        }
    }

    @Override
    public void loadProgress(ConfigurationSection section) {
        for (String uuid : section.getKeys(false)) {
            DataTracker killsTracker = new DataTracker();
            UUID playerUUID = UUID.fromString(uuid);
            SuperiorPlayer superiorPlayer = this.plugin.getPlayers().getSuperiorPlayer(playerUUID);

            insertData(superiorPlayer, killsTracker);

            for (String key : section.getConfigurationSection(uuid).getKeys(false)) {
                killsTracker.load(key, section.getInt(uuid + "." + key));
            }
        }
    }

    @Override
    public void formatItem(SuperiorPlayer superiorPlayer, ItemStack itemStack) {
        DataTracker killsTracker = getOrCreate(superiorPlayer, s -> new DataTracker());

        if (killsTracker == null)
            return;

        ItemMeta itemMeta = itemStack.getItemMeta();

        if (itemMeta == null)
            return;

        if (itemMeta.hasDisplayName())
            itemMeta.setDisplayName(Placeholders.parsePlaceholders(this.requiredEntities, killsTracker, itemMeta.getDisplayName()));

        if (itemMeta.hasLore()) {
            List<String> lore = new ArrayList<>();
            for (String line : Objects.requireNonNull(itemMeta.getLore()))
                lore.add(Placeholders.parsePlaceholders(this.requiredEntities, killsTracker, line));
            itemMeta.setLore(lore);
        }

        itemStack.setItemMeta(itemMeta);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityKill(EntityDeathEvent e) {
        if (!isMissionEntity(e.getEntity()))
            return;

        EntityDamageEvent damageCause = e.getEntity().getLastDamageCause();

        if (!(damageCause instanceof EntityDamageByEntityEvent))
            return;

        EntityDamageByEntityEvent entityDamageByEntityEvent = (EntityDamageByEntityEvent) damageCause;

        Player damager = null;

        if (entityDamageByEntityEvent.getDamager() instanceof Player) {
            damager = (Player) entityDamageByEntityEvent.getDamager();
        } else if (entityDamageByEntityEvent.getDamager() instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) entityDamageByEntityEvent.getDamager()).getShooter();
            if (shooter instanceof Player)
                damager = (Player) shooter;
        }

        if (damager == null)
            return;

        SuperiorPlayer superiorPlayer = this.plugin.getPlayers().getSuperiorPlayer(damager);

        if (!this.plugin.getMissions().canCompleteNoProgress(superiorPlayer, this))
            return;

        DataTracker killsTracker = getOrCreate(superiorPlayer, s -> new DataTracker());

        if (killsTracker == null)
            return;

        killsTracker.track(e.getEntity().getType().name(), this.getEntityCount.apply(e.getEntity()));

        Bukkit.getScheduler().runTaskLaterAsynchronously(this.plugin, () -> superiorPlayer.runIfOnline(player -> {
            if (canComplete(superiorPlayer))
                this.plugin.getMissions().rewardMission(this, superiorPlayer, true);
        }), 2L);
    }

    private boolean isMissionEntity(@Nullable Entity entity) {
        if (entity == null || entity instanceof ArmorStand)
            return false;

        for (RequirementsList requirement : requiredEntities.keySet()) {
            if (requirement.contains(entity.getType().name()))
                return true;
        }

        return false;
    }

}

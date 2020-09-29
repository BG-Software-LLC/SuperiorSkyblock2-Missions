package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.events.IslandBankDepositEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandBankWithdrawEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandBiomeChangeEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandCoopPlayerEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandCreateEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandDisbandEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandEnterEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandEnterProtectedEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandInviteEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandJoinEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandKickEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandLeaveEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandLeaveProtectedEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandQuitEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandTransferEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandUncoopPlayerEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandWorthCalculatedEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandWorthUpdateEvent;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public final class IslandMissions extends Mission<Boolean> implements Listener {

    private static final ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");

    private final Map<String, Boolean> missionEvents = new HashMap<>();

    private Placeholders placeholders = new Placeholders_None();
    private String successCheck;
    private JavaPlugin plugin;

    @Override
    public void load(JavaPlugin plugin, ConfigurationSection section) throws MissionLoadException {
        this.plugin = plugin;

        if(!section.contains("events"))
            throw new MissionLoadException("You must have the \"events\" section in the config.");

        for(String event : section.getStringList("events")){
            if(event.toLowerCase().endsWith("-target"))
                missionEvents.put(event.split("-")[0], true);
            else
                missionEvents.put(event, false);
        }

        successCheck = section.getString("success-check", "true");

        Bukkit.getPluginManager().registerEvents(this, plugin);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if(Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
                placeholders = new Placeholders_PAPI();
        }, 1L);

    }

    @Override
    public double getProgress(SuperiorPlayer superiorPlayer) {
        return get(superiorPlayer) == null ? 0.0 : 1.0;
    }

    @Override
    public int getProgressValue(SuperiorPlayer superiorPlayer) {
        return 0;
    }

    @Override
    public void onComplete(SuperiorPlayer superiorPlayer) {
        onCompleteFail(superiorPlayer);
    }

    @Override
    public void onCompleteFail(SuperiorPlayer superiorPlayer) {
        clearData(superiorPlayer);
    }

    /*
     *  Events
     */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDeposit(IslandBankDepositEvent e){
        tryComplete(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandWithdraw(IslandBankWithdrawEvent e){
        tryComplete(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandBiomeChange(IslandBiomeChangeEvent e){
        tryComplete(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandCoop(IslandCoopPlayerEvent e){
        tryComplete(e, e.getPlayer(), e.getTarget());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandCreate(IslandCreateEvent e){
        tryComplete(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandDisband(IslandDisbandEvent e){
        tryComplete(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandEnter(IslandEnterEvent e){
        tryComplete(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandEnterProtected(IslandEnterProtectedEvent e){
        tryComplete(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandInvite(IslandInviteEvent e){
        tryComplete(e, e.getPlayer(), e.getTarget());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandJoin(IslandJoinEvent e){
        tryComplete(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandKick(IslandKickEvent e){
        tryComplete(e, e.getPlayer(), e.getTarget());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandLeave(IslandLeaveEvent e){
        tryComplete(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandLeaveProtected(IslandLeaveProtectedEvent e){
        tryComplete(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandQuit(IslandQuitEvent e){
        tryComplete(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandTransfer(IslandTransferEvent e){
        tryComplete(e, e.getOldOwner(), e.getNewOwner());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandUncoop(IslandUncoopPlayerEvent e){
        tryComplete(e, e.getPlayer(), e.getTarget());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandCalculate(IslandWorthCalculatedEvent e){
        tryComplete(e, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIslandWorthUpdate(IslandWorthUpdateEvent e){
        tryComplete(e, e.getIsland().getOwner());
    }

    private void tryComplete(IslandEvent event, SuperiorPlayer superiorPlayer){
        tryComplete(event, superiorPlayer, null);
    }

    private void tryComplete(IslandEvent event, SuperiorPlayer superiorPlayer, SuperiorPlayer targetPlayer){
        String eventName = event.getClass().getSimpleName();
        if(missionEvents.containsKey(eventName)) {
            boolean success = false;

            SimpleBindings bindings = new SimpleBindings();
            bindings.put("event", event);

            try{
                String result = placeholders.parse(engine.eval(successCheck, bindings) + "", superiorPlayer.asOfflinePlayer());
                success = Boolean.parseBoolean(result);
            }catch(Exception ex){
                System.out.println("Engine: " + engine);
                System.out.println("Placeholders: " + placeholders);
                ex.printStackTrace();
            }

            if(success) {
                SuperiorPlayer rewardedPlayer = !missionEvents.get(eventName) ? superiorPlayer : targetPlayer;
                if (rewardedPlayer != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        insertData(rewardedPlayer, true);
                        SuperiorSkyblockAPI.getSuperiorSkyblock().getMissions().rewardMission(this, rewardedPlayer, true);
                    }, 5L);
                }
            }
        }
    }

    private interface Placeholders{

        String parse(String string, OfflinePlayer offlinePlayer);

    }

    private static final class Placeholders_PAPI implements Placeholders{

        @Override
        public String parse(String string, OfflinePlayer offlinePlayer) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(offlinePlayer, string);
        }

    }

    private static final class Placeholders_None implements Placeholders{

        @Override
        public String parse(String string, OfflinePlayer offlinePlayer) {
            return string;
        }

    }

}

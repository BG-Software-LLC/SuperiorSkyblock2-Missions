package com.bgsoftware.superiorskyblock.missions;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.events.IslandEvent;
import com.bgsoftware.superiorskyblock.api.events.IslandTransferEvent;
import com.bgsoftware.superiorskyblock.api.missions.Mission;
import com.bgsoftware.superiorskyblock.api.missions.MissionLoadException;
import com.bgsoftware.superiorskyblock.api.scripts.IScriptEngine;
import com.bgsoftware.superiorskyblock.api.service.placeholders.PlaceholdersService;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.missions.island.DynamicRegisteredListener;
import com.bgsoftware.superiorskyblock.missions.island.EventsHelper;
import com.bgsoftware.superiorskyblock.missions.island.timings.ITimings;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import javax.script.SimpleBindings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class IslandMissions extends Mission<Boolean> implements Listener {

    private SuperiorSkyblock plugin;

    private String successCheck;
    private PlaceholdersService placeholdersService;


    @Override
    public void load(JavaPlugin plugin, ConfigurationSection section) throws MissionLoadException {
        this.plugin = (SuperiorSkyblock) plugin;

        if (!section.contains("events"))
            throw new MissionLoadException("You must have the \"events\" section in the config.");

        for (String event : section.getStringList("events")) {
            boolean targetPlayer = false;
            String eventName = event;

            if (event.toLowerCase().endsWith("-target")) {
                targetPlayer = true;
                eventName = event.split("-")[0];
            }

            try {
                registerEventListener(plugin, eventName, targetPlayer);
            } catch (ClassNotFoundException error) {
                plugin.getLogger().warning("The event " + eventName + " is not valid, skipping");
            } catch (Exception error) {
                plugin.getLogger().warning("Cannot register IslandMission for " + eventName + ":");
                error.printStackTrace();
            }
        }

        successCheck = section.getString("success-check", "true");

        RegisteredServiceProvider<PlaceholdersService> registeredServiceProvider = plugin.getServer()
                .getServicesManager().getRegistration(PlaceholdersService.class);

        if (registeredServiceProvider != null) {
            this.placeholdersService = registeredServiceProvider.getProvider();
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
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

    private void registerEventListener(JavaPlugin plugin, String eventName, boolean isTarget) throws Exception {
        Class<?> eventClass = Class.forName("com.bgsoftware.superiorskyblock.api.events." + eventName);

        if (!Event.class.isAssignableFrom(eventClass))
            return;

        HandlerList handlerList = EventsHelper.getEventListeners(eventClass.asSubclass(Event.class));
        ITimings timings = ITimings.of(plugin, "Plugin: " + plugin.getDescription().getFullName() +
                " Event: DynamicRegisteredListener::" + eventName + "(" + eventClass.getSimpleName() + ")");

        Method getPlayerMethod = getMethodSilently(eventClass, "getPlayer");
        Method getTargetMethod = getMethodSilently(eventClass, "getTarget");

        handlerList.register(new DynamicRegisteredListener(plugin, (listener, event) -> {
            try {
                if (eventClass.isAssignableFrom(event.getClass()) && event instanceof IslandEvent) {
                    boolean isAsync = event.isAsynchronous();

                    if (!isAsync) {
                        timings.startTiming();
                    }

                    SuperiorPlayer superiorPlayer;
                    SuperiorPlayer targetPlayer;

                    if (event instanceof IslandTransferEvent) {
                        superiorPlayer = ((IslandTransferEvent) event).getOldOwner();
                        targetPlayer = ((IslandTransferEvent) event).getNewOwner();
                    } else {
                        superiorPlayer = getPlayerMethod == null ? ((IslandEvent) event).getIsland().getOwner() :
                                (SuperiorPlayer) getPlayerMethod.invoke(event);
                        targetPlayer = getTargetMethod == null ? null : (SuperiorPlayer) getTargetMethod.invoke(event);
                    }

                    tryComplete((IslandEvent) event, superiorPlayer, targetPlayer, isTarget);

                    if (!isAsync) {
                        timings.stopTiming();
                    }
                }
            } catch (InvocationTargetException var4) {
                throw new EventException(var4.getCause());
            } catch (Throwable var5) {
                throw new EventException(var5);
            }
        }));
    }

    private void tryComplete(IslandEvent event, SuperiorPlayer superiorPlayer, SuperiorPlayer targetPlayer, boolean isTarget) {
        boolean success = false;

        SimpleBindings bindings = new SimpleBindings();
        bindings.put("event", event);

        IScriptEngine scriptEngine = this.plugin.getScriptEngine();

        try {
            String result = scriptEngine.eval(successCheck, bindings) + "";
            if (this.placeholdersService != null) {
                result = placeholdersService.parsePlaceholders(superiorPlayer.asOfflinePlayer(), result);
            }
            success = Boolean.parseBoolean(result);
        } catch (Throwable error) {
            plugin.getLogger().warning("Error occurred while checking for success condition for IslandMission.");
            plugin.getLogger().warning("Current Script Engine: " + scriptEngine);
            error.printStackTrace();
        }

        if (success) {
            SuperiorPlayer rewardedPlayer = isTarget ? targetPlayer : superiorPlayer;
            if (rewardedPlayer != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    insertData(rewardedPlayer, true);
                    this.plugin.getMissions().rewardMission(this, rewardedPlayer, true);
                }, 5L);
            }
        }
    }

    private static Method getMethodSilently(Class<?> clazz, String methodName) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (Exception error) {
            return null;
        }
    }

}

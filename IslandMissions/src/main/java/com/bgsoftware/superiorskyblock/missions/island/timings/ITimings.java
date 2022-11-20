package com.bgsoftware.superiorskyblock.missions.island.timings;

import org.bukkit.plugin.Plugin;

public interface ITimings {

    void startTiming();

    void stopTiming();

    static ITimings of(Plugin plugin, String name) {
        try {
            return new TimingsWrapper(plugin, name);
        } catch (Throwable error) {
            return new LegacyTimingsWrapper(name);
        }
    }

}

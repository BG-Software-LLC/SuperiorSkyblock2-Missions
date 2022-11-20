package com.bgsoftware.superiorskyblock.missions.island.timings;

import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import org.bukkit.plugin.Plugin;

public class TimingsWrapper implements ITimings {

    private final Timing handle;

    public TimingsWrapper(Plugin plugin, String name) {
        this.handle = Timings.of(plugin, name);
    }

    @Override
    public void startTiming() {
        this.handle.startTiming();
    }

    @Override
    public void stopTiming() {
        this.handle.stopTiming();
    }

}

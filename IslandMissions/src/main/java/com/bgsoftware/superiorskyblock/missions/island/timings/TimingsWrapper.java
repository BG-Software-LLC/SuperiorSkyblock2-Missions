package com.bgsoftware.superiorskyblock.missions.island.timings;

import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import com.bgsoftware.common.reflection.ReflectMethod;
import org.bukkit.plugin.Plugin;

public class TimingsWrapper implements ITimings {

    private static final ReflectMethod<Timing> START_TIMING = new ReflectMethod<>(Timing.class, "startTiming");

    private final Timing handle;

    public TimingsWrapper(Plugin plugin, String name) {
        this.handle = Timings.of(plugin, name);
    }

    @Override
    public void startTiming() {
        try {
            this.handle.startTiming();
        } catch (Throwable error) {
            START_TIMING.invoke(this.handle);
        }
    }

    @Override
    public void stopTiming() {
        this.handle.stopTiming();
    }

}

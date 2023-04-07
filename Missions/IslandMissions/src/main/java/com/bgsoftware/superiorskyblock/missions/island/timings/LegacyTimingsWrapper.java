package com.bgsoftware.superiorskyblock.missions.island.timings;

import org.spigotmc.CustomTimingsHandler;

public class LegacyTimingsWrapper implements ITimings {

    private final CustomTimingsHandler handle;

    public LegacyTimingsWrapper(String name) {
        this.handle = new CustomTimingsHandler(name);
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

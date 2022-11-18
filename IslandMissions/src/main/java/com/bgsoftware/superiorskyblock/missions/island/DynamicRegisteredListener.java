package com.bgsoftware.superiorskyblock.missions.island;

import org.bukkit.event.EventPriority;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.TimedRegisteredListener;

public class DynamicRegisteredListener extends TimedRegisteredListener {

    public DynamicRegisteredListener(Plugin plugin, EventExecutor executor) {
        super(null, executor, EventPriority.MONITOR, plugin, true);
    }

}

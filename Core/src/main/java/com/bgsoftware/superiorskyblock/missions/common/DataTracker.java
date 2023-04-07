package com.bgsoftware.superiorskyblock.missions.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class DataTracker {

    private final Map<String, Counter> trackedData = new HashMap<>();
    private final Counter globalCounter = new Counter();

    public void track(String key, int amount) {
        this.trackedData.computeIfAbsent(key.toUpperCase(Locale.ENGLISH), k -> new Counter()).increase(amount);
        globalCounter.increase(amount);
    }

    public void load(String blockKey, int amount) {
        this.trackedData.put(blockKey, new Counter(amount));
        globalCounter.increase(amount);
    }

    public int getCount(String blockKey) {
        return Optional.ofNullable(this.trackedData.get(blockKey)).map(Counter::get).orElse(0);
    }

    public int getGlobalCounter() {
        return this.globalCounter.get();
    }

    public int getCounts(RequirementsList blocks) {
        if (blocks.isContainsAll())
            return getGlobalCounter();

        Counter blocksCount = new Counter();
        blocks.forEach(block -> blocksCount.increase(getCount(block)));
        return blocksCount.get();
    }

    public void clear() {
        this.trackedData.clear();
    }

    public Map<String, Counter> getCounts() {
        return Collections.unmodifiableMap(this.trackedData);
    }

}

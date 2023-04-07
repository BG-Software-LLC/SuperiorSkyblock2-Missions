package com.bgsoftware.superiorskyblock.missions.common;

public class Counter {

    private int count;

    public Counter() {
        this(0);
    }

    public Counter(int initialCount) {
        this.count = initialCount;
    }

    public int get() {
        return this.count;
    }

    public void increase(int delta) {
        this.count += delta;
    }

    public void increase(Counter other) {
        increase(other.count);
    }

}

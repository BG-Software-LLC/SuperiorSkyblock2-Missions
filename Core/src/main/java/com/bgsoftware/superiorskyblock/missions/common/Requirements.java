package com.bgsoftware.superiorskyblock.missions.common;

import com.google.common.collect.ForwardingSet;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class Requirements extends ForwardingSet<String> {

    private final Set<String> handle = new LinkedHashSet<>();
    private boolean containsAll = false;

    public Requirements(Collection<String> elements) {
        addAll(elements);
    }

    @Override
    public boolean add(String element) {
        if (containsAll)
            return true;

        if (element.equalsIgnoreCase("all")) {
            this.containsAll = true;
            return true;
        }

        return super.add(element);
    }

    @Override
    public boolean contains(Object object) {
        return isContainsAll() || super.contains(object);
    }

    public boolean isContainsAll() {
        return this.containsAll;
    }

    @Override
    protected Set<String> delegate() {
        return this.handle;
    }

}

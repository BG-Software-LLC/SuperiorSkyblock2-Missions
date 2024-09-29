package com.bgsoftware.superiorskyblock.missions.common;

import org.bukkit.ChatColor;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Placeholders {

    private static final Pattern PERCENTAGE_PATTERN = Pattern.compile("\\{percentage_(.+?)}");
    private static final Pattern VALUES_PATTERN = Pattern.compile("\\{value_(.+?)}");

    private Placeholders() {

    }

    public static String parsePlaceholders(Map<Requirements, Integer> requirements, DataTracker dataTracker, String line) {
        return parsePlaceholders(line, new PlaceholdersFunctions<String>() {
            @Override
            public String getRequirementFromKey(String key) {
                return key;
            }

            @Override
            public Optional<Integer> lookupRequirement(String requirement) {
                return requirements.entrySet().stream()
                        .filter(e -> e.getKey().contains(requirement))
                        .findFirst()
                        .map(Map.Entry::getValue);
            }

            @Override
            public int getCountForRequirement(String requirement) {
                return dataTracker.getCount(requirement);
            }
        });
    }

    public static <E> String parsePlaceholders(String line, PlaceholdersFunctions<E> functions) {
        Matcher matcher = PERCENTAGE_PATTERN.matcher(line);
        if (matcher.find()) {
            String requirementKey = matcher.group(1).toUpperCase(Locale.ENGLISH);
            E requirement = functions.getRequirementFromKey(requirementKey);
            Optional<Integer> entry = functions.lookupRequirement(requirement);

            if (entry.isPresent()) {
                line = matcher.replaceAll("" + (functions.getCountForRequirement(requirement) * 100) / entry.get());
            }
        }

        if ((matcher = VALUES_PATTERN.matcher(line)).find()) {
            String requirementKey = matcher.group(1).toUpperCase(Locale.ENGLISH);
            E requirement = functions.getRequirementFromKey(requirementKey);
            Optional<Integer> entry = functions.lookupRequirement(requirement);

            if (entry.isPresent()) {
                line = matcher.replaceAll("" + functions.getCountForRequirement(requirement));
            }
        }

        return ChatColor.translateAlternateColorCodes('&', line);
    }

    public static abstract class PlaceholdersFunctions<E> {

        public abstract E getRequirementFromKey(String key);

        public abstract Optional<Integer> lookupRequirement(E requirement);

        public abstract int getCountForRequirement(E requirement);

    }

}

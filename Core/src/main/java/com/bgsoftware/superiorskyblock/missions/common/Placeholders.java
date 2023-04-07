package com.bgsoftware.superiorskyblock.missions.common;

import org.bukkit.ChatColor;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Placeholders {

    public static final Pattern PERCENTAGE_PATTERN = Pattern.compile("\\{percentage_(.+?)}");
    public static final Pattern VALUES_PATTERN = Pattern.compile("\\{value_(.+?)}");

    private Placeholders() {

    }

    public static String parsePlaceholders(Map<RequirementsList, Integer> requirements, DataTracker dataTracker, String line) {
        return parsePlaceholders(requirements, dataTracker::getCount, line);
    }

    public static String parsePlaceholders(Map<RequirementsList, Integer> requirements, Function<String, Integer> getCountFunction, String line) {
        Matcher matcher = PERCENTAGE_PATTERN.matcher(line);
        if (matcher.find()) {
            String requirement = matcher.group(1).toUpperCase();
            Optional<Map.Entry<RequirementsList, Integer>> entry = requirements.entrySet().stream()
                    .filter(e -> e.getKey().contains(requirement)).findFirst();

            if (entry.isPresent()) {
                line = matcher.replaceAll("" + (getCountFunction.apply(requirement) * 100) / entry.get().getValue());
            }
        }

        if ((matcher = VALUES_PATTERN.matcher(line)).find()) {
            String requiredBlock = matcher.group(1).toUpperCase();
            Optional<Map.Entry<RequirementsList, Integer>> entry = requirements.entrySet().stream()
                    .filter(e -> e.getKey().contains(requiredBlock)).findFirst();

            if (entry.isPresent()) {
                line = matcher.replaceAll("" + getCountFunction.apply(requiredBlock));
            }
        }

        return ChatColor.translateAlternateColorCodes('&', line);
    }

}

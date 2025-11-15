package mofo.com.pestscout.farm.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standardised severity levels used for heat map visualisation. Each level defines a colour and the
 * inclusive range of counts that map to the level.
 */
public enum SeverityLevel {

    ZERO(0, 0, "#2ecc71"),
    LOW(1, 5, "#f1c40f"),
    MODERATE(6, 10, "#e67e22"),
    HIGH(11, 20, "#e74c3c"),
    VERY_HIGH(21, 30, "#c0392b"),
    EMERGENCY(31, Integer.MAX_VALUE, "#7f0000");

    private final int minInclusive;
    private final int maxInclusive;
    private final String colorHex;

    SeverityLevel(int minInclusive, int maxInclusive, String colorHex) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
        this.colorHex = colorHex;
    }

    public String getColorHex() {
        return colorHex;
    }

    public boolean matches(int count) {
        return count >= minInclusive && count <= maxInclusive;
    }

    public static SeverityLevel fromCount(int count) {
        return Arrays.stream(values())
                .filter(level -> level.matches(count))
                .findFirst()
                .orElse(EMERGENCY);
    }

    public static Map<String, String> legend() {
        Map<String, String> legend = new LinkedHashMap<>();
        for (SeverityLevel level : values()) {
            legend.put(level.name(), level.getColorHex());
        }
        return legend;
    }
}

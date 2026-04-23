package mofo.com.pestscout.analytics.dto;

import mofo.com.pestscout.common.exception.BadRequestException;

import java.util.Locale;

/**
 * Selects which harmful-pressure layer a heatmap should render.
 */
public enum HeatmapLayerMode {
    ALL("all"),
    PESTS("pests"),
    DISEASES("diseases");

    private final String apiValue;

    HeatmapLayerMode(String apiValue) {
        this.apiValue = apiValue;
    }

    /**
     * Parses the public `mode` parameter into a supported layer mode.
     */
    public static HeatmapLayerMode fromValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ALL;
        }

        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "all" -> ALL;
            case "pest", "pests" -> PESTS;
            case "disease", "diseases" -> DISEASES;
            default -> throw new BadRequestException(
                    "Unsupported heatmap mode '" + rawValue + "'. Expected all, pests, or diseases."
            );
        };
    }

    /**
     * Returns the serialized API value used in requests and responses.
     */
    public String apiValue() {
        return apiValue;
    }

    /**
     * Computes the count that should drive totals and colors for the active layer.
     */
    public int selectCount(int pestCount, int diseaseCount) {
        return switch (this) {
            case ALL -> pestCount + diseaseCount;
            case PESTS -> pestCount;
            case DISEASES -> diseaseCount;
        };
    }

    /**
     * Determines whether a cell should be included in the response for this layer.
     */
    public boolean includeCell(int selectedCount) {
        return this == ALL || selectedCount > 0;
    }
}

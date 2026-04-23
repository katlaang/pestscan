package mofo.com.pestscout.scouting.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Explicit observation intent captured in the field, separate from any later
 * species identification or AI analysis.
 */
@Schema(description = "Explicit field observation intent, independent of later species identification.")
public enum ObservationType {
    SUSPECTED_PEST(ObservationCategory.PEST, "Suspected pest activity"),
    DISEASE_SYMPTOM(ObservationCategory.DISEASE, "Visible disease symptoms"),
    CROP_DAMAGE(null, "Unexplained crop damage"),
    OTHER(null, "Other suspicious plant-health condition");

    private final ObservationCategory defaultCategory;
    private final String defaultDisplayName;

    ObservationType(ObservationCategory defaultCategory, String defaultDisplayName) {
        this.defaultCategory = defaultCategory;
        this.defaultDisplayName = defaultDisplayName;
    }

    public static ObservationType fromCategory(ObservationCategory category) {
        if (category == null) {
            return OTHER;
        }
        return switch (category) {
            case PEST -> SUSPECTED_PEST;
            case DISEASE -> DISEASE_SYMPTOM;
            case BENEFICIAL -> OTHER;
        };
    }

    public ObservationCategory getDefaultCategory() {
        return defaultCategory;
    }

    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }
}

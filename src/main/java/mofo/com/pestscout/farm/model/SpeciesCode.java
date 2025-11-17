package mofo.com.pestscout.farm.model;

import lombok.Getter;

/**
 * Species or factor recorded at a grid cell.
 * Wraps both pests and diseases (and later beneficials).
 */
@Getter
public enum SpeciesCode {

    // Pests (from PestType)
    THRIPS(ObservationCategory.PEST, "Thrips"),
    RED_SPIDER_MITE(ObservationCategory.PEST, "Red spider mite"),
    WHITEFLIES(ObservationCategory.PEST, "Whiteflies"),
    MEALYBUGS(ObservationCategory.PEST, "Mealybugs"),
    CATERPILLARS(ObservationCategory.PEST, "Caterpillars"),
    FALSE_CODLING_MOTH(ObservationCategory.PEST, "False codling moth"),
    PEST_OTHER(ObservationCategory.PEST, "Other pest"),

    // Diseases (from DiseaseType)
    DOWNY_MILDEW(ObservationCategory.DISEASE, "Downy mildew"),
    POWDERY_MILDEW(ObservationCategory.DISEASE, "Powdery mildew"),
    BOTRYTIS(ObservationCategory.DISEASE, "Botrytis"),
    VERTICILLIUM(ObservationCategory.DISEASE, "Verticillium"),
    BACTERIAL_WILT(ObservationCategory.DISEASE, "Bacterial wilt"),
    DISEASE_OTHER(ObservationCategory.DISEASE, "Other disease"),

    // You can add beneficial species here as you define them

    BENEFICIAL_PP(ObservationCategory.BENEFICIAL, "PP"),
    ;
    private final ObservationCategory category;
    private final String displayName;

    SpeciesCode(ObservationCategory category, String displayName) {
        this.category = category;
        this.displayName = displayName;
    }
}


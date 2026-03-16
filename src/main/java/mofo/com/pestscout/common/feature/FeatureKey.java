package mofo.com.pestscout.common.feature;

import lombok.Getter;
import mofo.com.pestscout.common.exception.BadRequestException;

/**
 * Canonical identifiers for optional product capabilities that can be globally gated and overridden per farm.
 */
@Getter
public enum FeatureKey {

    AI_PEST_IDENTIFICATION("ai-pest-identification", "AI pest identification"),
    DRONE_IMAGE_PROCESSING("drone-image-processing", "Drone or aerial image processing"),
    PREDICTIVE_MODELING("predictive-modeling", "Predictive modeling using machine learning"),
    AUTOMATED_PDF_REPORTS("automated-pdf-reports", "Automated PDF reports"),
    GIS_HEATMAPS("gis-heatmaps", "GIS heatmaps or advanced mapping layers"),
    AUTOMATED_TREATMENT_RECOMMENDATIONS(
            "automated-treatment-recommendations",
            "Automated treatment recommendations"
    ),
    SUPPLY_ORDERING("supply-ordering", "Integrated purchasing or supply ordering");

    private final String propertyKey;
    private final String displayName;

    FeatureKey(String propertyKey, String displayName) {
        this.propertyKey = propertyKey;
        this.displayName = displayName;
    }

    public static FeatureKey fromValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new BadRequestException("Feature key is required.");
        }

        String normalizedEnumName = rawValue.trim().replace('-', '_');
        for (FeatureKey featureKey : values()) {
            if (featureKey.propertyKey.equalsIgnoreCase(rawValue.trim())
                    || featureKey.name().equalsIgnoreCase(normalizedEnumName)) {
                return featureKey;
            }
        }

        throw new BadRequestException("Unknown feature key: " + rawValue);
    }
}

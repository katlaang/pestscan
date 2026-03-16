package mofo.com.pestscout.common.feature;

import lombok.Getter;
import lombok.Setter;
import mofo.com.pestscout.farm.model.SubscriptionTier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Deployment-level defaults for optional capabilities. Global enablement is controlled here, while farm-specific
 * overrides are stored in the database.
 */
@Component
@ConfigurationProperties(prefix = "app.features")
@Getter
@Setter
public class FeatureProperties {

    private FeatureSetting aiPestIdentification = new FeatureSetting(Set.of(SubscriptionTier.PREMIUM));
    private FeatureSetting droneImageProcessing = new FeatureSetting(Set.of(SubscriptionTier.PREMIUM));
    private FeatureSetting predictiveModeling = new FeatureSetting(Set.of(SubscriptionTier.PREMIUM));
    private FeatureSetting automatedPdfReports = new FeatureSetting(EnumSet.allOf(SubscriptionTier.class), true);
    private FeatureSetting gisHeatmaps = new FeatureSetting(Set.of(SubscriptionTier.PREMIUM));
    private FeatureSetting automatedTreatmentRecommendations =
            new FeatureSetting(Set.of(SubscriptionTier.STANDARD, SubscriptionTier.PREMIUM));
    private FeatureSetting supplyOrdering = new FeatureSetting(Set.of(SubscriptionTier.PREMIUM));

    public FeatureSetting settingFor(FeatureKey featureKey) {
        return switch (featureKey) {
            case AI_PEST_IDENTIFICATION -> aiPestIdentification;
            case DRONE_IMAGE_PROCESSING -> droneImageProcessing;
            case PREDICTIVE_MODELING -> predictiveModeling;
            case AUTOMATED_PDF_REPORTS -> automatedPdfReports;
            case GIS_HEATMAPS -> gisHeatmaps;
            case AUTOMATED_TREATMENT_RECOMMENDATIONS -> automatedTreatmentRecommendations;
            case SUPPLY_ORDERING -> supplyOrdering;
        };
    }

    @Getter
    @Setter
    public static class FeatureSetting {

        private boolean enabled;
        private Set<SubscriptionTier> allowedTiers = EnumSet.noneOf(SubscriptionTier.class);

        public FeatureSetting() {
        }

        public FeatureSetting(Set<SubscriptionTier> allowedTiers) {
            setAllowedTiers(allowedTiers);
        }

        public FeatureSetting(Set<SubscriptionTier> allowedTiers, boolean enabled) {
            setAllowedTiers(allowedTiers);
            this.enabled = enabled;
        }

        public void setAllowedTiers(Set<SubscriptionTier> allowedTiers) {
            if (allowedTiers == null || allowedTiers.isEmpty()) {
                this.allowedTiers = EnumSet.noneOf(SubscriptionTier.class);
                return;
            }

            this.allowedTiers = EnumSet.copyOf(allowedTiers);
        }

        public boolean allows(SubscriptionTier subscriptionTier) {
            return subscriptionTier != null && allowedTiers.contains(subscriptionTier);
        }
    }
}

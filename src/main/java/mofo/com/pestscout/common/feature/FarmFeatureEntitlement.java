package mofo.com.pestscout.common.feature;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;
import mofo.com.pestscout.farm.model.Farm;

/**
 * Farm-specific override for a feature. Rows here only override the deployment default; they do not bypass the
 * global master switch.
 */
@Entity
@Table(
        name = "farm_feature_entitlements",
        uniqueConstraints = @UniqueConstraint(name = "uk_farm_feature_entitlements_farm_feature", columnNames = {"farm_id", "feature_key"}),
        indexes = {
                @Index(name = "idx_farm_feature_entitlements_farm", columnList = "farm_id"),
                @Index(name = "idx_farm_feature_entitlements_feature", columnList = "feature_key")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FarmFeatureEntitlement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false, length = 64)
    private FeatureKey featureKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}

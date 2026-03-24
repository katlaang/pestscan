package mofo.com.pestscout.common.feature;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for farm-level feature overrides.
 */
public interface FarmFeatureEntitlementRepository extends JpaRepository<FarmFeatureEntitlement, UUID> {

    List<FarmFeatureEntitlement> findByFarmId(UUID farmId);

    Optional<FarmFeatureEntitlement> findByFarmIdAndFeatureKey(UUID farmId, FeatureKey featureKey);

    void deleteByFarmId(UUID farmId);
}

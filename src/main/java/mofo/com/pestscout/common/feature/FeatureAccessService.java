package mofo.com.pestscout.common.feature;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.feature.dto.FarmFeatureStatusResponse;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves whether an optional capability is effectively available for a farm by combining global deployment config,
 * subscription tier defaults, and explicit farm overrides.
 */
@Service
@RequiredArgsConstructor
public class FeatureAccessService {

    private final FeatureProperties featureProperties;
    private final FarmRepository farmRepository;
    private final FarmFeatureEntitlementRepository entitlementRepository;

    @Transactional(readOnly = true)
    public boolean isEnabled(FeatureKey featureKey, UUID farmId) {
        Farm farm = loadFarm(farmId);
        return resolveEffectiveEnabled(featureKey, farm, findOverride(farmId, featureKey));
    }

    @Transactional(readOnly = true)
    public void assertEnabled(FeatureKey featureKey, UUID farmId) {
        Farm farm = loadFarm(farmId);
        if (!resolveEffectiveEnabled(featureKey, farm, findOverride(farmId, featureKey))) {
            throw new ForbiddenException(featureKey.getDisplayName() + " is not enabled for farm '" + farm.getName() + "'.");
        }
    }

    @Transactional(readOnly = true)
    public List<FarmFeatureStatusResponse> getFeatureStatuses(UUID farmId) {
        Farm farm = loadFarm(farmId);
        Map<FeatureKey, FarmFeatureEntitlement> overrides = entitlementRepository.findByFarmId(farmId).stream()
                .filter(entitlement -> !entitlement.isDeleted())
                .collect(Collectors.toMap(
                        FarmFeatureEntitlement::getFeatureKey,
                        Function.identity(),
                        (left, right) -> right,
                        () -> new EnumMap<>(FeatureKey.class)
                ));

        return Arrays.stream(FeatureKey.values())
                .map(featureKey -> toResponse(featureKey, farm, Optional.ofNullable(overrides.get(featureKey))))
                .toList();
    }

    @Transactional
    public FarmFeatureStatusResponse setFarmOverride(UUID farmId, FeatureKey featureKey, boolean enabled) {
        Farm farm = loadFarm(farmId);

        FarmFeatureEntitlement entitlement = entitlementRepository.findByFarmIdAndFeatureKey(farmId, featureKey)
                .orElseGet(() -> FarmFeatureEntitlement.builder()
                        .farm(farm)
                        .featureKey(featureKey)
                        .build());

        entitlement.setFarm(farm);
        entitlement.setFeatureKey(featureKey);
        entitlement.setEnabled(enabled);
        entitlement.restore();

        FarmFeatureEntitlement saved = entitlementRepository.save(entitlement);
        return toResponse(featureKey, farm, Optional.of(saved));
    }

    private Farm loadFarm(UUID farmId) {
        return farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));
    }

    private Optional<FarmFeatureEntitlement> findOverride(UUID farmId, FeatureKey featureKey) {
        return entitlementRepository.findByFarmIdAndFeatureKey(farmId, featureKey)
                .filter(entitlement -> !entitlement.isDeleted());
    }

    private FarmFeatureStatusResponse toResponse(
            FeatureKey featureKey,
            Farm farm,
            Optional<FarmFeatureEntitlement> override
    ) {
        FeatureProperties.FeatureSetting featureSetting = featureProperties.settingFor(featureKey);
        boolean tierAllowed = featureSetting.allows(farm.getSubscriptionTier());
        Boolean overrideEnabled = override.map(FarmFeatureEntitlement::isEnabled).orElse(null);
        boolean effectiveEnabled = resolveEffectiveEnabled(featureKey, farm, override);

        return new FarmFeatureStatusResponse(
                featureKey.getPropertyKey(),
                featureKey.getDisplayName(),
                featureSetting.isEnabled(),
                tierAllowed,
                overrideEnabled,
                effectiveEnabled
        );
    }

    private boolean resolveEffectiveEnabled(
            FeatureKey featureKey,
            Farm farm,
            Optional<FarmFeatureEntitlement> override
    ) {
        FeatureProperties.FeatureSetting featureSetting = featureProperties.settingFor(featureKey);
        if (!featureSetting.isEnabled()) {
            return false;
        }

        return override.map(FarmFeatureEntitlement::isEnabled)
                .orElseGet(() -> featureSetting.allows(farm.getSubscriptionTier()));
    }
}

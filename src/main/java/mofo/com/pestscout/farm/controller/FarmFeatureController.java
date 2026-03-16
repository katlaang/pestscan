package mofo.com.pestscout.farm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.feature.FeatureAccessService;
import mofo.com.pestscout.common.feature.FeatureKey;
import mofo.com.pestscout.common.feature.dto.FarmFeatureStatusResponse;
import mofo.com.pestscout.common.feature.dto.UpdateFarmFeatureRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/farms/{farmId}/features")
@RequiredArgsConstructor
public class FarmFeatureController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmFeatureController.class);

    private final FeatureAccessService featureAccessService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<FarmFeatureStatusResponse>> listFeatures(@PathVariable UUID farmId) {
        LOGGER.info("GET /api/farms/{}/features - listing feature flags", farmId);
        return ResponseEntity.ok(featureAccessService.getFeatureStatuses(farmId));
    }

    @PutMapping("/{featureKey}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<FarmFeatureStatusResponse> updateFeature(
            @PathVariable UUID farmId,
            @PathVariable String featureKey,
            @Valid @RequestBody UpdateFarmFeatureRequest request
    ) {
        FeatureKey parsedFeatureKey = FeatureKey.fromValue(featureKey);
        LOGGER.info("PUT /api/farms/{}/features/{} - setting override to {}", farmId, parsedFeatureKey.getPropertyKey(), request.enabled());
        return ResponseEntity.ok(featureAccessService.setFarmOverride(farmId, parsedFeatureKey, request.enabled()));
    }
}

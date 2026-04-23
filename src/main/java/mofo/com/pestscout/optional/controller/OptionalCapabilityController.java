package mofo.com.pestscout.optional.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.HeatmapLayerMode;
import mofo.com.pestscout.optional.dto.OptionalCapabilityDtos.*;
import mofo.com.pestscout.optional.dto.SupplyOrderingDtos.CreateSupplyOrderFromDraftRequest;
import mofo.com.pestscout.optional.dto.SupplyOrderingDtos.SupplyOrderDraftResponse;
import mofo.com.pestscout.optional.dto.SupplyOrderingDtos.SupplyOrderResponse;
import mofo.com.pestscout.optional.service.OptionalCapabilityService;
import mofo.com.pestscout.optional.service.SupplyOrderingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/optional-capabilities")
@RequiredArgsConstructor
public class OptionalCapabilityController {

    private final OptionalCapabilityService optionalCapabilityService;
    private final SupplyOrderingService supplyOrderingService;

    @GetMapping("/ai-pest-identification/photos/{photoId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    public ResponseEntity<AiPestIdentificationResponse> identifyPhoto(
            @RequestParam UUID farmId,
            @PathVariable UUID photoId
    ) {
        return ResponseEntity.ok(optionalCapabilityService.identifyFromPhoto(farmId, photoId));
    }

    @PostMapping("/drone-image-processing/analyze")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<DroneImageProcessingResponse> processDroneImagery(
            @Valid @RequestBody DroneImageProcessingRequest request
    ) {
        return ResponseEntity.ok(optionalCapabilityService.processDroneImagery(request));
    }

    @GetMapping("/predictive-modeling/forecast")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<PredictiveModelResponse> getForecast(@RequestParam UUID farmId) {
        return ResponseEntity.ok(optionalCapabilityService.getPredictiveForecast(farmId));
    }

    @GetMapping("/gis-heatmaps/layers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<GisHeatmapResponse> getGisLayers(
            @RequestParam UUID farmId,
            @RequestParam(required = false) Integer week,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "all") String mode
    ) {
        return ResponseEntity.ok(optionalCapabilityService.getGisHeatmapLayers(
                farmId,
                week,
                year,
                HeatmapLayerMode.fromValue(mode)
        ));
    }

    @GetMapping("/automated-treatment-recommendations")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<TreatmentRecommendationResponse> getTreatmentRecommendations(@RequestParam UUID farmId) {
        return ResponseEntity.ok(optionalCapabilityService.getTreatmentRecommendations(farmId));
    }

    @GetMapping("/supply-ordering/draft")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<SupplyOrderDraftResponse> getSupplyOrderDraft(@RequestParam UUID farmId) {
        return ResponseEntity.ok(supplyOrderingService.buildDraft(farmId));
    }

    @PostMapping("/supply-ordering/orders")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<SupplyOrderResponse> submitSupplyOrder(
            @Valid @RequestBody CreateSupplyOrderFromDraftRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplyOrderingService.submitOrderFromDraft(request));
    }

    @GetMapping("/supply-ordering/orders")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<List<SupplyOrderResponse>> listSupplyOrders(@RequestParam UUID farmId) {
        return ResponseEntity.ok(supplyOrderingService.listOrders(farmId));
    }
}

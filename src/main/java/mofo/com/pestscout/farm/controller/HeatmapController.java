package mofo.com.pestscout.farm.controller;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.HeatmapLayerMode;
import mofo.com.pestscout.analytics.dto.HeatmapResponse;
import mofo.com.pestscout.analytics.service.HeatmapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/farms/{farmId}/heatmap")
@RequiredArgsConstructor
public class HeatmapController {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeatmapController.class);

    private final HeatmapService heatmapService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    public ResponseEntity<HeatmapResponse> getHeatmap(@PathVariable UUID farmId,
                                                       @RequestParam int week,
                                                      @RequestParam int year,
                                                      @RequestParam(defaultValue = "all") String mode) {
        HeatmapLayerMode layerMode = HeatmapLayerMode.fromValue(mode);
        LOGGER.info("GET /api/farms/{}/heatmap - week {}, year {}, mode {}", farmId, week, year, layerMode.apiValue());
        return ResponseEntity.ok(heatmapService.generateHeatmap(farmId, week, year, layerMode));
    }
}

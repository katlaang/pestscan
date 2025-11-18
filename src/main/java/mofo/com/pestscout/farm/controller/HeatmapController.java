package mofo.com.pestscout.farm.controller;

import lombok.RequiredArgsConstructor;
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<HeatmapResponse> getHeatmap(@PathVariable UUID farmId,
                                                       @RequestParam int week,
                                                       @RequestParam int year) {
        // Farm access and licence read-only behaviour are enforced inside HeatmapService
        // so the controller only validates authentication and logs the request.
        LOGGER.info("GET /api/farms/{}/heatmap — week {}, year {}", farmId, week, year);
        return ResponseEntity.ok(heatmapService.generateHeatmap(farmId, week, year));
    }
}

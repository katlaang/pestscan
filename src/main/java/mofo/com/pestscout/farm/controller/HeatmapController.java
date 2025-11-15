package mofo.com.pestscout.farm.controller;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.farm.dto.HeatmapResponse;
import mofo.com.pestscout.farm.service.HeatmapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/farms/{farmId}/heatmap")
@RequiredArgsConstructor
public class HeatmapController {

    private final HeatmapService heatmapService;

    @GetMapping
    public ResponseEntity<HeatmapResponse> getHeatmap(@PathVariable UUID farmId,
                                                       @RequestParam int week,
                                                       @RequestParam int year) {
        return ResponseEntity.ok(heatmapService.generateHeatmap(farmId, week, year));
    }
}

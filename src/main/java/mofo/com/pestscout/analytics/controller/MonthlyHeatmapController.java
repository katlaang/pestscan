package mofo.com.pestscout.analytics.controller;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.MonthlyHeatmapResponse;
import mofo.com.pestscout.analytics.service.MonthlyHeatmapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/heatmap")
@RequiredArgsConstructor
public class MonthlyHeatmapController {

    private final MonthlyHeatmapService heatmapService;

    @GetMapping("/monthly")
    public MonthlyHeatmapResponse getMonthly(
            @RequestParam UUID farmId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return heatmapService.getMonthlyHeatmap(farmId, year, month);
    }
}


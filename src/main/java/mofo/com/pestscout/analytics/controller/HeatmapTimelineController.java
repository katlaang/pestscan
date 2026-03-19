package mofo.com.pestscout.analytics.controller;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.HeatmapRangeUnit;
import mofo.com.pestscout.analytics.dto.HeatmapTimelineResponse;
import mofo.com.pestscout.analytics.service.HeatmapTimelineService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/heatmap")
@RequiredArgsConstructor
public class HeatmapTimelineController {

    private final HeatmapTimelineService heatmapTimelineService;

    @GetMapping("/timeline")
    public HeatmapTimelineResponse getTimeline(
            @RequestParam UUID farmId,
            @RequestParam(defaultValue = "MONTHS") HeatmapRangeUnit rangeUnit,
            @RequestParam(defaultValue = "1") int rangeSize,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return heatmapTimelineService.getWeeklyTimeline(farmId, rangeUnit, rangeSize, endDate);
    }
}

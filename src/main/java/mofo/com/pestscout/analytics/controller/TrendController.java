package mofo.com.pestscout.analytics.controller;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.PestTrendResponse;
import mofo.com.pestscout.analytics.dto.SeverityTrendPointDto;
import mofo.com.pestscout.analytics.dto.WeeklyPestTrendDto;
import mofo.com.pestscout.analytics.service.TrendAnalysisService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/trend")
@RequiredArgsConstructor
public class TrendController {

    private final TrendAnalysisService trendAnalysisService;

    @GetMapping("/pest")
    public ResponseEntity<PestTrendResponse> getPestTrend(
            @RequestParam UUID farmId,
            @RequestParam("species") String speciesCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        PestTrendResponse response =
                trendAnalysisService.getPestTrend(farmId, speciesCode, from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/weekly")
    public ResponseEntity<List<WeeklyPestTrendDto>> getWeeklyPestTrends(
            @RequestParam UUID farmId
    ) {
        List<WeeklyPestTrendDto> weekly =
                trendAnalysisService.getWeeklyPestTrends(farmId);
        return ResponseEntity.ok(weekly);
    }

    @GetMapping("/severity")
    public ResponseEntity<List<SeverityTrendPointDto>> getSeverityTrend(
            @RequestParam UUID farmId
    ) {
        List<SeverityTrendPointDto> trend =
                trendAnalysisService.getSeverityTrend(farmId);
        return ResponseEntity.ok(trend);
    }
}



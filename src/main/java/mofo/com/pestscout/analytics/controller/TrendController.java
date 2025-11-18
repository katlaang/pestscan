package mofo.com.pestscout.analytics.controller;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.PestTrendResponse;
import mofo.com.pestscout.analytics.service.TrendAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/trend")
@RequiredArgsConstructor
public class TrendController {

    private final TrendAnalysisService trendService;

    @GetMapping("/pest")
    public PestTrendResponse getTrend(
            @RequestParam UUID farmId,
            @RequestParam String species,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return trendService.getPestTrend(farmId, species, from, to);
    }
}


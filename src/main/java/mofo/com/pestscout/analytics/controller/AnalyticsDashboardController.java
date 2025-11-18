package mofo.com.pestscout.analytics.controller;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.DashboardDto;
import mofo.com.pestscout.analytics.service.DashboardAggregatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/dashboard/full")
@RequiredArgsConstructor
public class AnalyticsDashboardController {

    private final DashboardAggregatorService dashboardAggregatorService;

    @GetMapping
    public DashboardDto getFullDashboard(@RequestParam UUID farmId) {
        return dashboardAggregatorService.getFullDashboard(farmId);
    }
}

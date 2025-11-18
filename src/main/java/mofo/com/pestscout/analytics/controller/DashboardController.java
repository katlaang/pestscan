package mofo.com.pestscout.analytics.controller;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.DashboardSummaryDto;
import mofo.com.pestscout.analytics.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardSummaryDto getDashboard(@RequestParam UUID farmId) {
        return dashboardService.getDashboard(farmId);
    }
}

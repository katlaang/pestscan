package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardAggregatorService {

    private final DashboardService dashboardService;       // your existing summary service
    private final HeatmapService heatmapService;
    private final TrendAnalysisService trendService;
    private final ReportingService reportingService;

    public DashboardDto getFullDashboard(UUID farmId) {

        // 1. Summary
        DashboardSummaryDto summary = dashboardService.getDashboard(farmId);

        // 2. Weekly heatmap
        LocalDate today = LocalDate.now();
        int week = today.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        int year = today.getYear();
        var heatmap = heatmapService.generateHeatmap(farmId, week, year);

        // 3. Weekly pest trends (from trend service)
        List<WeeklyPestTrendDto> weeklyTrends = trendService.getWeeklyPestTrends(farmId);

        // 4. Severity trends
        List<SeverityTrendPointDto> severityTrend = trendService.getSeverityTrend(farmId);

        // 5. Distribution (reporting service)
        List<PestDistributionItemDto> pestDist = reportingService.getPestDistribution(farmId);
        List<DiseaseDistributionItemDto> diseaseDist = reportingService.getDiseaseDistribution(farmId);

        // 6. Alerts
        List<AlertDto> alerts = reportingService.getAlerts(farmId);

        // 7. Recommendations
        List<RecommendationDto> recs = reportingService.getRecommendations(farmId);

        // 8. Farm comparison
        List<FarmComparisonDto> farmComparison = reportingService.getFarmComparison();

        // 9. Scouts
        List<ScoutPerformanceDto> scoutPerf = reportingService.getScoutPerformance(farmId);

        return new DashboardDto(
                summary,
                pestDist,
                diseaseDist,
                weeklyTrends,
                severityTrend,
                heatmap.cells(),  // reuse existing DTO
                alerts,
                recs,
                farmComparison,
                scoutPerf
        );
    }
}


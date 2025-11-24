package mofo.com.pestscout.analytics.service;

import mofo.com.pestscout.analytics.dto.*;
import mofo.com.pestscout.scouting.model.SeverityLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DashboardAggregatorService} to ensure the composed dashboard pulls data from
 * every collaborating service.
 */
@ExtendWith(MockitoExtension.class)
class DashboardAggregatorServiceTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private HeatmapService heatmapService;

    @Mock
    private TrendAnalysisService trendAnalysisService;

    @Mock
    private ReportingService reportingService;

    @InjectMocks
    private DashboardAggregatorService aggregatorService;

    /**
     * Aggregates every section of the dashboard and asserts each collaborator contributes to the
     * final DTO.
     */
    @Test
    void getFullDashboard_collectsAllSectionsFromServices() {
        UUID farmId = UUID.randomUUID();
        DashboardSummaryDto summaryDto = new DashboardSummaryDto(
                farmId,
                2,
                1,
                1.5,
                0.5,
                3,
                1,
                List.of(),
                List.of()
        );

        LocalDate today = LocalDate.now();
        int week = today.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = today.getYear();

        HeatmapResponse heatmap = HeatmapResponse.builder()
                .farmId(farmId)
                .farmName("Farm")
                .week(week)
                .year(year)
                .bayCount(1)
                .benchesPerBay(1)
                .cells(List.of(HeatmapCellResponse.builder()
                        .bayIndex(0)
                        .benchIndex(0)
                        .pestCount(1)
                        .diseaseCount(0)
                        .beneficialCount(0)
                        .totalCount(1)
                        .severityLevel(SeverityLevel.LOW)
                        .colorHex("#fff")
                        .build()))
                .sections(List.of())
                .severityLegend(List.of())
                .build();

        WeeklyPestTrendDto weeklyTrend = new WeeklyPestTrendDto("W1", 1, 0, 0, 0, 0, 0, 0);
        SeverityTrendPointDto severityTrendPoint = new SeverityTrendPointDto("2024-W1", 1, 0, 0, 1, 0);
        PestDistributionItemDto pestDistribution = new PestDistributionItemDto("thrips", 10, 100.0, "moderate");
        DiseaseDistributionItemDto diseaseDistribution = new DiseaseDistributionItemDto("powdery", 5, 50.0, "low");
        AlertDto alert = new AlertDto("Edmonton", "whiteflies", "LOW", 1, LocalDate.now().toString());
        RecommendationDto recommendation = new RecommendationDto("Scout", "Farm", "apply", "critical", "completed", LocalDate.now().toString());
        FarmComparisonDto farmComparison = new FarmComparisonDto("Farm", 2.0, 1, 3);
        ScoutPerformanceDto scoutPerformance = new ScoutPerformanceDto("Scout", 3, 75, "2m");

        when(dashboardService.getDashboard(farmId)).thenReturn(summaryDto);
        when(heatmapService.generateHeatmap(farmId, week, year)).thenReturn(heatmap);
        when(trendAnalysisService.getWeeklyPestTrends(farmId)).thenReturn(List.of(weeklyTrend));
        when(trendAnalysisService.getSeverityTrend(farmId)).thenReturn(List.of(severityTrendPoint));
        when(reportingService.getPestDistribution(farmId)).thenReturn(List.of(pestDistribution));
        when(reportingService.getDiseaseDistribution(farmId)).thenReturn(List.of(diseaseDistribution));
        when(reportingService.getAlerts(farmId)).thenReturn(List.of(alert));
        when(reportingService.getRecommendations(farmId)).thenReturn(List.of(recommendation));
        when(reportingService.getFarmComparison()).thenReturn(List.of(farmComparison));
        when(reportingService.getScoutPerformance(farmId)).thenReturn(List.of(scoutPerformance));

        DashboardDto dashboard = aggregatorService.getFullDashboard(farmId);

        assertThat(dashboard.summary()).isEqualTo(summaryDto);
        assertThat(dashboard.heatmap()).containsExactlyElementsOf(heatmap.cells());
        assertThat(dashboard.weeklyTrends()).containsExactly(weeklyTrend);
        assertThat(dashboard.severityTrend()).containsExactly(severityTrendPoint);
        assertThat(dashboard.pestDistribution()).containsExactly(pestDistribution);
        assertThat(dashboard.diseaseDistribution()).containsExactly(diseaseDistribution);
        assertThat(dashboard.alerts()).containsExactly(alert);
        assertThat(dashboard.recommendations()).containsExactly(recommendation);
        assertThat(dashboard.farmComparison()).containsExactly(farmComparison);
        assertThat(dashboard.scoutPerformance()).containsExactly(scoutPerformance);
    }
}

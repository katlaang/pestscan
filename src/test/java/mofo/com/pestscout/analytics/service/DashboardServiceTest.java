package mofo.com.pestscout.analytics.service;

import mofo.com.pestscout.analytics.dto.DashboardSummaryDto;
import mofo.com.pestscout.analytics.dto.HeatmapResponse;
import mofo.com.pestscout.analytics.dto.PestTrendResponse;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.scouting.model.ScoutingSession;
import mofo.com.pestscout.scouting.model.SessionStatus;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DashboardService} that exercise the public dashboard summary workflow
 * and its dependent collaborators.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ScoutingSessionRepository sessionRepository;

    @Mock
    private AnalyticsAccessService analyticsAccessService;

    @Mock
    private HeatmapService heatmapService;

    @Mock
    private TrendAnalysisService trendAnalysisService;

    @InjectMocks
    private DashboardService dashboardService;

    /**
     * Happy-path coverage that verifies a summary payload is assembled when all dependencies return
     * empty data sets.
     */
    @Test
    void getDashboard_returnsSummaryWithHeatmapAndTrend() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setId(farmId);
        LocalDate today = LocalDate.now();
        int week = today.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = today.getYear();

        HeatmapResponse heatmap = HeatmapResponse.builder()
                .farmId(farmId)
                .farmName("Farm")
                .week(week)
                .year(year)
                .bayCount(0)
                .benchesPerBay(0)
                .cells(List.of())
                .sections(List.of())
                .severityLegend(List.of())
                .build();

        PestTrendResponse pestTrend = new PestTrendResponse(farmId, "thrips", List.of());

        when(analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId)).thenReturn(farm);
        when(sessionRepository.findByFarmId(farmId)).thenReturn(List.of());
        when(sessionRepository.findByFarmIdAndSessionDateBetween(farmId, today.minusDays(6), today))
                .thenReturn(List.of());
        when(heatmapService.generateHeatmap(farmId, week, year)).thenReturn(heatmap);
        when(trendAnalysisService.getPestTrend(farmId, "thrips", today.minusDays(30), today))
                .thenReturn(pestTrend);

        DashboardSummaryDto summary = dashboardService.getDashboard(farmId);

        assertThat(summary.farmId()).isEqualTo(farmId);
        assertThat(summary.totalSessions()).isZero();
        assertThat(summary.activeScouts()).isZero();
        assertThat(summary.treatmentsApplied()).isZero();
        assertThat(summary.currentWeekHeatmap()).hasSize(1);
        assertThat(summary.currentWeekHeatmap().getFirst().weekNumber()).isEqualTo(week);
        assertThat(summary.severityTrend()).containsExactlyElementsOf(pestTrend.points());
    }

    @Test
    void getDashboard_fallsBackToLatestWeekWithData() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setId(farmId);

        LocalDate today = LocalDate.now();
        LocalDate priorSessionDate = today.minusWeeks(1);
        int currentWeek = today.get(WeekFields.ISO.weekOfWeekBasedYear());
        int currentYear = today.get(WeekFields.ISO.weekBasedYear());
        int fallbackWeek = priorSessionDate.get(WeekFields.ISO.weekOfWeekBasedYear());
        int fallbackYear = priorSessionDate.get(WeekFields.ISO.weekBasedYear());

        ScoutingSession priorSession = ScoutingSession.builder()
                .id(UUID.randomUUID())
                .farm(farm)
                .sessionDate(priorSessionDate)
                .status(SessionStatus.COMPLETED)
                .build();

        HeatmapResponse emptyCurrent = HeatmapResponse.builder()
                .farmId(farmId)
                .farmName("Farm")
                .week(currentWeek)
                .year(currentYear)
                .bayCount(0)
                .benchesPerBay(0)
                .cells(List.of())
                .sections(List.of())
                .severityLegend(List.of())
                .build();

        HeatmapResponse fallback = HeatmapResponse.builder()
                .farmId(farmId)
                .farmName("Farm")
                .week(fallbackWeek)
                .year(fallbackYear)
                .bayCount(1)
                .benchesPerBay(1)
                .cells(List.of(mofo.com.pestscout.analytics.dto.HeatmapCellResponse.builder()
                        .bayIndex(1)
                        .benchIndex(1)
                        .pestCount(1)
                        .diseaseCount(0)
                        .beneficialCount(0)
                        .totalCount(1)
                        .severityLevel(mofo.com.pestscout.scouting.model.SeverityLevel.LOW)
                        .colorHex("#a7f3d0")
                        .build()))
                .sections(List.of())
                .severityLegend(List.of())
                .build();

        when(analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId)).thenReturn(farm);
        when(sessionRepository.findByFarmId(farmId)).thenReturn(List.of(priorSession));
        when(sessionRepository.findByFarmIdAndSessionDateBetween(farmId, today.minusDays(6), today))
                .thenReturn(List.of());
        when(heatmapService.generateHeatmap(farmId, currentWeek, currentYear)).thenReturn(emptyCurrent);
        when(heatmapService.generateHeatmap(eq(farmId), eq(fallbackWeek), eq(fallbackYear))).thenReturn(fallback);
        when(trendAnalysisService.getPestTrend(farmId, "thrips", today.minusDays(30), today))
                .thenReturn(new PestTrendResponse(farmId, "thrips", List.of()));

        DashboardSummaryDto summary = dashboardService.getDashboard(farmId);

        assertThat(summary.currentWeekHeatmap()).hasSize(1);
        assertThat(summary.currentWeekHeatmap().getFirst().weekNumber()).isEqualTo(fallbackWeek);
    }
}

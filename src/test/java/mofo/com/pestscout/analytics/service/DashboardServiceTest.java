package mofo.com.pestscout.analytics.service;

import mofo.com.pestscout.analytics.dto.DashboardSummaryDto;
import mofo.com.pestscout.analytics.dto.HeatmapResponse;
import mofo.com.pestscout.analytics.dto.PestTrendResponse;
import mofo.com.pestscout.analytics.dto.TrendPointDto;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
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
    private FarmRepository farmRepository;

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

        HeatmapResponse heatmap = HeatmapResponse.builder()
                .farmId(farmId)
                .farmName("Farm")
                .week(2)
                .year(2024)
                .bayCount(0)
                .benchesPerBay(0)
                .cells(List.of())
                .sections(List.of())
                .severityLegend(List.of())
                .build();

        PestTrendResponse pestTrend = new PestTrendResponse(farmId, "thrips", List.of());

        try (MockedStatic<LocalDate> localDateMock = mockStatic(LocalDate.class)) {
            LocalDate today = LocalDate.of(2024, 1, 10);
            localDateMock.when(LocalDate::now).thenReturn(today);

            when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
            when(sessionRepository.findByFarmId(farmId)).thenReturn(List.of());
            when(sessionRepository.findByFarmIdAndSessionDateBetween(farmId, today.minusDays(6), today))
                    .thenReturn(List.of());
            when(heatmapService.generateHeatmap(farmId, 2, 2024)).thenReturn(heatmap);
            when(trendAnalysisService.getPestTrend(farmId, "thrips", today.minusDays(30), today))
                    .thenReturn(pestTrend);

            DashboardSummaryDto summary = dashboardService.getDashboard(farmId);

            assertThat(summary.farmId()).isEqualTo(farmId);
            assertThat(summary.totalSessions()).isZero();
            assertThat(summary.activeScouts()).isZero();
            assertThat(summary.treatmentsApplied()).isZero();
            assertThat(summary.currentWeekHeatmap()).hasSize(1);
            assertThat(summary.currentWeekHeatmap().getFirst().weekNumber()).isEqualTo(2);
            assertThat(summary.severityTrend()).containsExactlyElementsOf(pestTrend.points());
        }
    }
}

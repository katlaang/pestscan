package mofo.com.pestscout.analytics.service;

import mofo.com.pestscout.analytics.dto.FarmMonthlyReportDto;
import mofo.com.pestscout.analytics.dto.HeatmapResponse;
import mofo.com.pestscout.analytics.dto.WeeklyHeatmapResponse;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.scouting.dto.ScoutingSessionDetailDto;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private ScoutingSessionRepository sessionRepository;

    @Mock
    private ScoutingObservationRepository observationRepository;

    @Mock
    private HeatmapService heatmapService;

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private ScoutingSessionService scoutingSessionService;

    @Mock
    private TrendAnalysisService trendAnalysisService;

    @InjectMocks
    private ReportingService reportingService;

    @Test
    void getSessionReport_returnsDetailsFromService() {
        UUID sessionId = UUID.randomUUID();
        ScoutingSessionDetailDto detailDto = new ScoutingSessionDetailDto(sessionId, List.of());

        when(scoutingSessionService.getSession(sessionId)).thenReturn(detailDto);

        assertThat(reportingService.getSessionReport(sessionId)).isEqualTo(detailDto);
    }

    @Test
    void getMonthlyReport_includesGeneratedHeatmaps() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setId(farmId);

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(heatmapService.generateHeatmap(farmId, 1, 2024)).thenReturn(HeatmapResponse.builder()
                .farmId(farmId)
                .farmName("Farm")
                .week(1)
                .year(2024)
                .bayCount(0)
                .benchesPerBay(0)
                .cells(List.of())
                .sections(List.of())
                .severityLegend(List.of())
                .build());
        when(trendAnalysisService.getSeverityTrend(farmId)).thenReturn(List.of());
        when(trendAnalysisService.getPestTrend(farmId, "thrips", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(new mofo.com.pestscout.analytics.dto.PestTrendResponse(farmId, "thrips", List.of()));
        when(trendAnalysisService.getPestTrend(farmId, "redSpider", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(new mofo.com.pestscout.analytics.dto.PestTrendResponse(farmId, "redSpider", List.of()));
        when(sessionRepository.findByFarmIdAndSessionDateBetween(farmId, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(List.of());
        when(observationRepository.findByFarmIdAndSessionDateBetween(farmId, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(List.of());

        FarmMonthlyReportDto report = reportingService.getMonthlyReport(farmId, 2024, 1);

        assertThat(report.farmId()).isEqualTo(farmId);
        assertThat(report.weeklyHeatmaps()).hasSize(4);
    }
}

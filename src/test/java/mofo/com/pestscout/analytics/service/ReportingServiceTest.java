package mofo.com.pestscout.analytics.service;

import mofo.com.pestscout.analytics.dto.FarmMonthlyReportDto;
import mofo.com.pestscout.analytics.dto.FarmWeeklyAnalyticsDto;
import mofo.com.pestscout.analytics.dto.HeatmapResponse;
import mofo.com.pestscout.analytics.dto.PestTrendResponse;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.service.AnalyticsService;
import mofo.com.pestscout.scouting.dto.RecommendationEntryDto;
import mofo.com.pestscout.scouting.dto.ScoutingSessionDetailDto;
import mofo.com.pestscout.scouting.dto.ScoutingSessionSectionDto;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import mofo.com.pestscout.scouting.service.ScoutingSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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

    /**
     * Ensures the service simply delegates to {@link ScoutingSessionService} for the single-session report path.
     */
    @Test
    void getSessionReport_returnsDetailsFromService() {
        UUID sessionId = UUID.randomUUID();
        ScoutingSessionDetailDto detailDto = minimalSessionDetail(sessionId, UUID.randomUUID());

        when(scoutingSessionService.getSession(sessionId)).thenReturn(detailDto);

        assertThat(reportingService.getSessionReport(sessionId)).isEqualTo(detailDto);
    }

    /**
     * Verifies the monthly aggregation stitches together generated weekly heatmaps and trend placeholders.
     */
    @Test
    void getMonthlyReport_includesGeneratedHeatmaps() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setId(farmId);

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(heatmapService.generateHeatmap(eq(farmId), anyInt(), eq(2024))).thenAnswer(invocation -> {
            int weekNumber = invocation.getArgument(1, Integer.class);
            return HeatmapResponse.builder()
                    .farmId(farmId)
                    .farmName("Farm")
                    .week(weekNumber)
                    .year(2024)
                    .bayCount(0)
                    .benchesPerBay(0)
                    .cells(List.of())
                    .sections(List.of())
                    .severityLegend(List.of())
                    .build();
        });
        when(trendAnalysisService.getSeverityTrend(farmId)).thenReturn(List.of());
        when(trendAnalysisService.getPestTrend(farmId, "thrips", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(new PestTrendResponse(farmId, "thrips", List.of()));
        when(trendAnalysisService.getPestTrend(farmId, "redSpider", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(new PestTrendResponse(farmId, "redSpider", List.of()));
        when(sessionRepository.findByFarmIdAndSessionDateBetween(farmId, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(List.of());

        FarmMonthlyReportDto report = reportingService.getMonthlyReport(farmId, 2024, 1);

        assertThat(report.farmId()).isEqualTo(farmId);
        assertThat(report.weeklyHeatmaps()).hasSize(4);
    }

    /**
     * Confirms weekly report includes sessions, a heatmap, and analytics payloads from collaborators.
     */
    @Test
    void getWeeklyFarmReport_collectsSessionsHeatmapAndAnalytics() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setId(farmId);

        ScoutingSession session = ScoutingSession.builder()
                .id(UUID.randomUUID())
                .farm(farm)
                .sessionDate(LocalDate.of(2024, 3, 5))
                .status(SessionStatus.COMPLETED)
                .build();

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(sessionRepository.findByFarmIdAndSessionDateBetween(farmId, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 7)))
                .thenReturn(List.of(session));
        when(scoutingSessionService.getSession(session.getId())).thenReturn(minimalSessionDetail(session.getId(), farmId));

        HeatmapResponse heatmap = HeatmapResponse.builder().farmId(farmId).farmName("Farm").week(1).year(2024)
                .bayCount(0).benchesPerBay(0).cells(List.of()).sections(List.of()).severityLegend(List.of()).build();
        when(heatmapService.generateHeatmap(farmId, 1, 2024)).thenReturn(heatmap);

        FarmWeeklyAnalyticsDto analyticsDto = new FarmWeeklyAnalyticsDto(
                farmId,
                farm.getName(),
                1,
                2024,
                0,
                0,
                1,
                1,
                3,
                2,
                1,
                0,
                Map.of("LOW", 1L)
        );
        when(analyticsService.computeWeeklyAnalytics(farmId, 1, 2024)).thenReturn(analyticsDto);

        ReportingService.WeeklyFarmReportDto report = reportingService.getWeeklyFarmReport(farmId, 1, 2024);

        assertThat(report.sessions()).hasSize(1);
        assertThat(report.heatmap()).isEqualTo(heatmap);
        assertThat(report.analytics()).isEqualTo(analyticsDto);
    }

    /**
     * Validates that pest distribution aggregates counts, percentages, and severity labels in descending order.
     */
    @Test
    void getPestDistribution_ordersByCountAndCalculatesSeverity() {
        UUID farmId = UUID.randomUUID();
        ScoutingSession session = buildSession(farmId, UUID.randomUUID());

        ScoutingObservation thrips = buildObservation(session, SpeciesCode.THRIPS, 10);
        ScoutingObservation whiteflies = buildObservation(session, SpeciesCode.WHITEFLIES, 5);

        when(sessionRepository.findByFarmId(farmId)).thenReturn(List.of(session));
        when(observationRepository.findBySessionIdIn(List.of(session.getId()))).thenReturn(List.of(whiteflies, thrips));

        var distribution = reportingService.getPestDistribution(farmId);

        assertThat(distribution).hasSize(2);
        assertThat(distribution.getFirst().name()).isEqualTo("Thrips");
        assertThat(distribution.getFirst().percentage()).isEqualTo(66.7);
        assertThat(distribution.getFirst().severity()).isEqualTo("medium");
    }

    /**
     * Validates that disease distribution mirrors pest calculations but uses the disease subset.
     */
    @Test
    void getDiseaseDistribution_returnsPercentagesForDiseaseSpecies() {
        UUID farmId = UUID.randomUUID();
        ScoutingSession session = buildSession(farmId, UUID.randomUUID());
        ScoutingObservation powdery = buildObservation(session, SpeciesCode.POWDERY_MILDEW, 3);

        when(sessionRepository.findByFarmId(farmId)).thenReturn(List.of(session));
        when(observationRepository.findBySessionIdIn(List.of(session.getId()))).thenReturn(List.of(powdery));

        var distribution = reportingService.getDiseaseDistribution(farmId);

        assertThat(distribution).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Powdery mildew");
            assertThat(item.percentage()).isEqualTo(100.0);
            assertThat(item.severity()).isEqualTo("low");
        });
    }

    /**
     * Ensures recommendations are flattened into DTOs with derived priority, location, and scout names.
     */
    @Test
    void getRecommendations_flattensSessionRecommendations() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setName("Farm");
        ScoutingSession session = ScoutingSession.builder()
                .id(UUID.randomUUID())
                .farm(farm)
                .sessionDate(LocalDate.of(2024, 2, 1))
                .status(SessionStatus.COMPLETED)
                .recommendations(new EnumMap<>(RecommendationType.class))
                .build();
        session.getRecommendations().put(RecommendationType.CHEMICAL_SPRAYS, "Spray");

        when(sessionRepository.findByFarmId(farmId)).thenReturn(List.of(session));

        var recommendations = reportingService.getRecommendations(farmId);

        assertThat(recommendations).singleElement().satisfies(rec -> {
            assertThat(rec.priority()).isEqualTo("critical");
            assertThat(rec.date()).isEqualTo("2024-02-01");
            assertThat(rec.location()).isEqualTo("Farm");
        });
    }

    /**
     * Asserts alerts are emitted only for high-severity non-beneficial observations.
     */
    @Test
    void getAlerts_filtersNonCriticalObservations() {
        UUID farmId = UUID.randomUUID();
        ScoutingSession session = buildSession(farmId, UUID.randomUUID());
        ScoutingObservation benign = buildObservation(session, SpeciesCode.BENEFICIAL_PP, 50);
        ScoutingObservation alertObs = buildObservation(session, SpeciesCode.THRIPS, SeverityLevel.HIGH.minThreshold());

        when(sessionRepository.findByFarmId(farmId)).thenReturn(List.of(session));
        when(observationRepository.findBySessionIdIn(List.of(session.getId()))).thenReturn(List.of(alertObs, benign));

        var alerts = reportingService.getAlerts(farmId);

        assertThat(alerts).singleElement().satisfies(alert -> {
            assertThat(alert.severity()).isEqualTo("high");
            assertThat(alert.count()).isEqualTo(SeverityLevel.HIGH.minThreshold());
        });
    }

    /**
     * Exercises the farm comparison pipeline including severity averages and alert counts.
     */
    @Test
    void getFarmComparison_ranksByAverageSeverity() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setId(farmId);
        farm.setName("Alpha");

        ScoutingSession session = buildSession(farmId, UUID.randomUUID());
        ScoutingObservation observation = buildObservation(session, SpeciesCode.THRIPS, SeverityLevel.HIGH.minThreshold());

        when(farmRepository.findAll()).thenReturn(List.of(farm));
        when(sessionRepository.findByFarmId(farmId)).thenReturn(List.of(session));
        when(observationRepository.findBySessionIdIn(List.of(session.getId()))).thenReturn(List.of(observation));

        var comparisons = reportingService.getFarmComparison();

        assertThat(comparisons).singleElement().satisfies(dto -> {
            assertThat(dto.farm()).isEqualTo("Alpha");
            assertThat(dto.avgSeverity()).isEqualTo((double) SeverityLevel.HIGH.minThreshold());
            assertThat(dto.alerts()).isEqualTo(1);
        });
    }

    /**
     * Confirms scout performance aggregates observation counts, accuracy percentage, and duration formatting.
     */
    @Test
    void getScoutPerformance_summarizesSessionsPerScout() {
        UUID farmId = UUID.randomUUID();
        ScoutingSession session = buildSession(farmId, UUID.randomUUID());
        session.setStatus(SessionStatus.COMPLETED);
        session.setStartedAt(LocalDateTime.now().minusMinutes(5));
        session.setCompletedAt(session.getStartedAt().plus(Duration.ofMinutes(5)));

        ScoutingObservation observation = buildObservation(session, SpeciesCode.WHITEFLIES, 2);

        when(sessionRepository.findByFarmId(farmId)).thenReturn(List.of(session));
        when(observationRepository.findBySessionIdIn(List.of(session.getId()))).thenReturn(List.of(observation));

        var performance = reportingService.getScoutPerformance(farmId);

        assertThat(performance).singleElement().satisfies(dto -> {
            assertThat(dto.observations()).isEqualTo(2);
            assertThat(dto.accuracy()).isEqualTo(100);
            assertThat(dto.avgTime()).endsWith("m");
        });
    }

    private ScoutingSessionDetailDto minimalSessionDetail(UUID sessionId, UUID farmId) {
        return new ScoutingSessionDetailDto(
                sessionId,
                1L,
                farmId,
                LocalDate.now(),
                1,
                SessionStatus.COMPLETED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "",
                "",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalTime.NOON,
                "",
                "",
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                false,
                List.of(),
                List.of()
        );
    }

    private ScoutingSession buildSession(UUID farmId, UUID sessionId) {
        Farm farm = new Farm();
        farm.setId(farmId);

        return ScoutingSession.builder()
                .id(sessionId)
                .farm(farm)
                .scout(buildScout())
                .sessionDate(LocalDate.of(2024, 1, 1))
                .status(SessionStatus.IN_PROGRESS)
                .build();
    }

    private ScoutingObservation buildObservation(ScoutingSession session, SpeciesCode code, int count) {
        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .id(UUID.randomUUID())
                .build();

        return ScoutingObservation.builder()
                .id(UUID.randomUUID())
                .session(session)
                .sessionTarget(target)
                .speciesCode(code)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .bayLabel("B1")
                .benchLabel("Bench1")
                .count(count)
                .build();
    }

    private mofo.com.pestscout.auth.model.User buildScout() {
        return mofo.com.pestscout.auth.model.User.builder()
                .id(UUID.randomUUID())
                .firstName("Scout")
                .lastName("User")
                .email("scout@example.test")
                .phoneNumber("123")
                .password("pwd")
                .role(mofo.com.pestscout.auth.model.Role.ADMIN)
                .build();
    }
}

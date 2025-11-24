package mofo.com.pestscout.analytics.service;

import mofo.com.pestscout.analytics.dto.FarmMonthlyReportDto;
import mofo.com.pestscout.analytics.dto.FarmWeeklyAnalyticsDto;
import mofo.com.pestscout.analytics.dto.HeatmapResponse;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.scouting.dto.ScoutingSessionAnalyticsDto;
import mofo.com.pestscout.scouting.dto.ScoutingSessionDetailDto;
import mofo.com.pestscout.scouting.model.RecommendationType;
import mofo.com.pestscout.scouting.model.ScoutingObservation;
import mofo.com.pestscout.scouting.model.ScoutingSession;
import mofo.com.pestscout.scouting.model.ScoutingSessionTarget;
import mofo.com.pestscout.scouting.model.SessionStatus;
import mofo.com.pestscout.scouting.model.SeverityLevel;
import mofo.com.pestscout.scouting.model.SpeciesCode;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
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

    /**
     * Ensures the service simply delegates to {@link ScoutingSessionService} for the single-session report path.
     */
    @Test
    void getSessionReport_returnsDetailsFromService() {
        UUID sessionId = UUID.randomUUID();
        ScoutingSessionDetailDto detailDto = new ScoutingSessionDetailDto(sessionId, List.of());

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
        when(scoutingSessionService.getSession(session.getId())).thenReturn(new ScoutingSessionDetailDto(session.getId(), List.of()));

        HeatmapResponse heatmap = HeatmapResponse.builder().farmId(farmId).farmName("Farm").week(1).year(2024)
                .bayCount(0).benchesPerBay(0).cells(List.of()).sections(List.of()).severityLegend(List.of()).build();
        when(heatmapService.generateHeatmap(farmId, 1, 2024)).thenReturn(heatmap);

        FarmWeeklyAnalyticsDto analyticsDto = new FarmWeeklyAnalyticsDto(1, 2, List.of(), 0, 0, List.of(), List.of(), 0, 0,
                new ScoutingSessionAnalyticsDto(0, 0, 0), List.of(), List.of());
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
        assertThat(distribution.getFirst().species()).isEqualTo("Thrips");
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
            assertThat(item.species()).isEqualTo("Powdery mildew");
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
        ScoutingObservation alertObs = buildObservation(session, SpeciesCode.THRIPS, SeverityLevel.HIGH.getMinCount());

        when(sessionRepository.findByFarmId(farmId)).thenReturn(List.of(session));
        when(observationRepository.findBySessionIdIn(List.of(session.getId()))).thenReturn(List.of(alertObs, benign));

        var alerts = reportingService.getAlerts(farmId);

        assertThat(alerts).singleElement().satisfies(alert -> {
            assertThat(alert.severity()).isEqualTo("high");
            assertThat(alert.count()).isEqualTo(SeverityLevel.HIGH.getMinCount());
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
        ScoutingObservation observation = buildObservation(session, SpeciesCode.THRIPS, 4);

        when(farmRepository.findAll()).thenReturn(List.of(farm));
        when(sessionRepository.findByFarmId(farmId)).thenReturn(List.of(session));
        when(observationRepository.findBySessionIdIn(List.of(session.getId()))).thenReturn(List.of(observation));

        var comparisons = reportingService.getFarmComparison();

        assertThat(comparisons).singleElement().satisfies(dto -> {
            assertThat(dto.farmName()).isEqualTo("Alpha");
            assertThat(dto.avgSeverity()).isEqualTo(4.0);
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
            assertThat(dto.avgDuration()).endsWith("m");
        });
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
        ScoutingSessionTarget target = new ScoutingSessionTarget();
        target.setId(UUID.randomUUID());

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
        mofo.com.pestscout.auth.model.User user = new mofo.com.pestscout.auth.model.User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Scout");
        user.setLastName("User");
        user.setEmail("scout@example.test");
        return user;
    }
}

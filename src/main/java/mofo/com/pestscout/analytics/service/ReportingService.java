package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.*;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.service.AnalyticsService;
import mofo.com.pestscout.scouting.dto.ScoutingSessionDetailDto;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingPhotoAnalysisRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import mofo.com.pestscout.scouting.service.ScoutingSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final AnalyticsAccessService analyticsAccessService;
    private final FarmRepository farmRepository;
    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;
    private final ScoutingPhotoAnalysisRepository photoAnalysisRepository;
    private final HeatmapService heatmapService;
    private final AnalyticsService analyticsService;
    private final ScoutingSessionService scoutingSessionService;
    private final TrendAnalysisService trendAnalysisService;

    /**
     * Full report for a single session.
     */
    @Transactional(readOnly = true)
    public ScoutingSessionDetailDto getSessionReport(UUID sessionId) {
        return scoutingSessionService.getSession(sessionId);
    }

    /**
     * Weekly report for a farm:
     * - sessions in the week
     * - weekly heatmap
     * - analytics summary
     */
    @Transactional(readOnly = true)
    public WeeklyFarmReportDto getWeeklyFarmReport(UUID farmId, int week, int year) {
        Farm farm = analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);

        WeekFields weekFields = WeekFields.ISO;
        LocalDate firstWeekReference = LocalDate.of(year, 1, 4);

        LocalDate weekStart = firstWeekReference
                .with(weekFields.weekOfWeekBasedYear(), week)
                .with(weekFields.dayOfWeek(), 1);

        LocalDate weekEnd = weekStart.plusDays(6);

        List<ScoutingSession> sessions = sessionRepository
                .findByFarmIdAndSessionDateBetween(farmId, weekStart, weekEnd);

        List<ScoutingSessionDetailDto> sessionDtos = sessions.stream()
                .map(s -> scoutingSessionService.getSession(s.getId()))
                .toList();

        HeatmapResponse heatmap = heatmapService.generateHeatmap(farmId, week, year);
        FarmWeeklyAnalyticsDto analytics = analyticsService.computeWeeklyAnalytics(farmId, week, year);

        return new WeeklyFarmReportDto(
                farm.getId(),
                farm.getName(),
                week,
                year,
                weekStart,
                weekEnd,
                sessionDtos,
                heatmap,
                analytics
        );
    }

    /**
     * Wrapper DTO for the weekly report.
     * This is a nested record, so outside callers must use:
     *   ReportingService.WeeklyFarmReportDto
     */
    public record WeeklyFarmReportDto(
            UUID farmId,
            String farmName,
            int week,
            int year,
            LocalDate weekStart,
            LocalDate weekEnd,
            List<ScoutingSessionDetailDto> sessions,
            HeatmapResponse heatmap,
            FarmWeeklyAnalyticsDto analytics
    ) {
    }

    // --------------------------------------------------------------------
    // Additional methods needed by dashboard / reporting (SAFE STUBS)
    // You can fill these with real logic later.
    // --------------------------------------------------------------------

    /**
     * Monthly report composed of weekly heatmaps + trends + analytics.
     * Currently returns null as a placeholder to avoid compilation errors.
     */
    @Transactional(readOnly = true)
    public FarmMonthlyReportDto getMonthlyReport(UUID farmId, int year, int month) {
        Farm farm = analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);

        LocalDate periodStart = LocalDate.of(year, month, 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());

        List<WeeklyHeatmapResponse> heatmaps = IntStream.range(0, 4)
                .mapToObj(offset -> {
                    LocalDate weekStart = periodStart.plusWeeks(offset);
                    LocalDate weekEnd = weekStart.plusDays(6);
                    int weekNumber = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear());
                    HeatmapResponse response = heatmapService.generateHeatmap(farmId, weekNumber, year);
                    return new WeeklyHeatmapResponse(
                            weekNumber,
                            year,
                            weekStart,
                            weekEnd,
                            response.sections()
                    );
                })
                .toList();

        AtomicInteger weekCounter = new AtomicInteger();
        List<TrendPointDto> severityTrend = trendAnalysisService.getSeverityTrend(farmId).stream()
                .map(p -> new TrendPointDto(periodStart.plusWeeks(weekCounter.getAndIncrement()), p.critical()))
                .toList();

        List<PestTrendResponse> topPestTrends = List.of(
                trendAnalysisService.getPestTrend(farmId, "thrips", periodStart, periodEnd),
                trendAnalysisService.getPestTrend(farmId, "redSpider", periodStart, periodEnd)
        );

        return new FarmMonthlyReportDto(
                farm.getId(),
                year,
                month,
                heatmaps,
                severityTrend,
                topPestTrends,
                sessionsInPeriod(farmId, periodStart, periodEnd),
                totalObservationCount(farmId, periodStart, periodEnd),
                distinctScouts(farmId, periodStart, periodEnd),
                averageSeverity(farmId, periodStart, periodEnd),
                worstSeverity(farmId, periodStart, periodEnd),
                distinctSpeciesCount(farmId, periodStart, periodEnd),
                periodStart,
                periodEnd,
                SeverityLegendEntry.from(mofo.com.pestscout.scouting.model.SeverityLevel.ZERO)
        );
    }

    /**
     * Distribution of pests observed across the farm.
     */
    @Transactional(readOnly = true)
    public List<PestDistributionItemDto> getPestDistribution(UUID farmId) {
        analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);
        List<ScoutingObservation> observations = allObservationsForFarm(farmId);
        Map<String, Long> countsBySpecies = observations.stream()
                .filter(o -> o.getCategory() == ObservationCategory.PEST)
                .collect(Collectors.groupingBy(
                        this::speciesName,
                        Collectors.summingLong(o -> o.getCount() == null ? 0 : o.getCount())
                ));

        long total = countsBySpecies.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return List.of();
        }

        return countsBySpecies.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    double percentage = (entry.getValue() * 100.0) / total;
                    SeverityLevel severity = SeverityLevel.fromCount(entry.getValue().intValue());
                    return new PestDistributionItemDto(
                            entry.getKey(),
                            entry.getValue().intValue(),
                            Math.round(percentage * 10.0) / 10.0,
                            toSeverityLabel(severity)
                    );
                })
                .toList();
    }

    /**
     * Distribution of diseases observed across the farm.
     */
    @Transactional(readOnly = true)
    public List<DiseaseDistributionItemDto> getDiseaseDistribution(UUID farmId) {
        analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);
        List<ScoutingObservation> observations = allObservationsForFarm(farmId);
        Map<String, Long> countsBySpecies = observations.stream()
                .filter(o -> o.getCategory() == ObservationCategory.DISEASE)
                .collect(Collectors.groupingBy(
                        this::speciesName,
                        Collectors.summingLong(o -> o.getCount() == null ? 0 : o.getCount())
                ));

        long total = countsBySpecies.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) {
            return List.of();
        }

        return countsBySpecies.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    double percentage = (entry.getValue() * 100.0) / total;
                    SeverityLevel severity = SeverityLevel.fromCount(entry.getValue().intValue());
                    return new DiseaseDistributionItemDto(
                            entry.getKey(),
                            Math.toIntExact(entry.getValue()),
                            Math.round(percentage * 10.0) / 10.0,
                            toSeverityLabel(severity)
                    );
                })
                .toList();
    }

    /**
     * Recommendations generated by scouting sessions.
     */
    @Transactional(readOnly = true)
    public List<RecommendationDto> getRecommendations(UUID farmId) {
        analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);
        List<ScoutingSession> sessions = sessionRepository.findByFarmId(farmId);
        List<RecommendationDto> recommendations = new ArrayList<>();

        for (ScoutingSession session : sessions) {
            if (session.getRecommendations() == null || session.getRecommendations().isEmpty()) continue;

            String scoutName = session.getScout() != null
                    ? (session.getScout().getFirstName() + " " + session.getScout().getLastName()).trim()
                    : "Unknown";
            String location = session.getGreenhouse() != null
                    ? session.getGreenhouse().getName()
                    : session.getFieldBlock() != null
                    ? session.getFieldBlock().getName()
                    : session.getFarm().getName();

            String status = session.getStatus() != null ? session.getStatus().name().toLowerCase() : "unknown";
            String date = session.getSessionDate() != null ? session.getSessionDate().toString() : "";

            session.getRecommendations().forEach((type, note) -> {
                String priority = switch (type) {
                    case CHEMICAL_SPRAYS -> "critical";
                    case BIOLOGICAL_CONTROL -> "high";
                    case OTHER_METHODS -> "medium";
                };

                recommendations.add(new RecommendationDto(
                        scoutName.isBlank() ? "Unknown" : scoutName,
                        location,
                        note,
                        priority,
                        status,
                        date
                ));
            });
        }

        return recommendations;
    }

    /**
     * Active alerts for a farm.
     */
    @Transactional(readOnly = true)
    public List<AlertDto> getAlerts(UUID farmId) {
        analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);
        List<ScoutingObservation> observations = allObservationsForFarm(farmId);

        return observations.stream()
                .filter(o -> o.getCategory() != ObservationCategory.BENEFICIAL)
                .map(o -> {
                    SeverityLevel level = SeverityLevel.fromCount(o.getCount() == null ? 0 : o.getCount());
                    if (level.ordinal() < SeverityLevel.HIGH.ordinal()) {
                        return null;
                    }

                    String location = "Bay " + (o.getBayLabel() != null ? o.getBayLabel() : o.getBayIndex())
                            + " - Bench " + (o.getBenchLabel() != null ? o.getBenchLabel() : o.getBenchIndex());

                    return new AlertDto(
                            location,
                            speciesName(o),
                            toSeverityLabel(level),
                            o.getCount() == null ? 0 : o.getCount(),
                            o.getSession().getSessionDate() != null ? o.getSession().getSessionDate().toString() : ""
                    );
                })
                .filter(a -> a != null)
                .sorted(Comparator.comparingInt(AlertDto::count).reversed())
                .toList();
    }

    /**
     * Compares farms based on severity and observation counts.
     */
    @Transactional(readOnly = true)
    public List<FarmComparisonDto> getFarmComparison() {
        List<Farm> farms = farmRepository.findAll();

        return farms.stream()
                .map(farm -> {
                    List<ScoutingSession> sessions = sessionRepository.findByFarmId(farm.getId());
                    if (sessions.isEmpty()) {
                        return new FarmComparisonDto(farm.getName(), 0, 0, 0);
                    }

                    List<UUID> sessionIds = sessions.stream().map(ScoutingSession::getId).toList();
                    List<ScoutingObservation> observations = observationRepository.findBySessionIdIn(sessionIds);

                    int totalObservations = observations.stream()
                            .mapToInt(o -> o.getCount() == null ? 0 : o.getCount())
                            .sum();

                    double averageSeverity = observations.isEmpty()
                            ? 0
                            : totalObservations / (double) observations.size();

                    int alertCount = (int) observations.stream()
                            .filter(o -> SeverityLevel.fromCount(o.getCount() == null ? 0 : o.getCount())
                                    .ordinal() >= SeverityLevel.HIGH.ordinal())
                            .count();

                    return new FarmComparisonDto(
                            farm.getName(),
                            Math.round(averageSeverity * 10.0) / 10.0,
                            totalObservations,
                            alertCount
                    );
                })
                .sorted(Comparator.comparingDouble(FarmComparisonDto::avgSeverity).reversed())
                .toList();
    }

    /**
     * Aggregated scout performance.
     */
    @Transactional(readOnly = true)
    public List<ScoutPerformanceDto> getScoutPerformance(UUID farmId) {
        analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);
        List<ScoutingSession> sessions = sessionRepository.findByFarmId(farmId);
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<UUID> sessionIds = sessions.stream().map(ScoutingSession::getId).toList();
        Map<UUID, List<ScoutingObservation>> observationsBySession = observationRepository.findBySessionIdIn(sessionIds)
                .stream()
                .collect(Collectors.groupingBy(o -> o.getSession().getId()));

        Map<UUID, List<ScoutingSession>> sessionsByScout = sessions.stream()
                .filter(s -> s.getScout() != null)
                .collect(Collectors.groupingBy(s -> s.getScout().getId()));

        Map<UUID, List<ScoutingPhotoAnalysis>> reviewedAnalysesByScout = photoAnalysisRepository
                .findByFarmIdAndReviewStatusIn(
                        farmId,
                        List.of(PhotoAnalysisReviewStatus.CONFIRMED, PhotoAnalysisReviewStatus.CORRECTED)
                )
                .stream()
                .filter(analysis -> analysis.getPhoto() != null)
                .filter(analysis -> analysis.getPhoto().getSession() != null)
                .filter(analysis -> analysis.getPhoto().getSession().getScout() != null)
                .collect(Collectors.groupingBy(analysis -> analysis.getPhoto().getSession().getScout().getId()));

        List<ScoutPerformanceDto> performance = new ArrayList<>();

        for (var entry : sessionsByScout.entrySet()) {
            var scoutSessions = entry.getValue();
            var scout = scoutSessions.getFirst().getScout();
            List<ScoutingObservation> scoutObservations = scoutSessions.stream()
                    .map(ScoutingSession::getId)
                    .map(observationsBySession::get)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .toList();
            Set<String> observationKeys = scoutObservations.stream()
                    .map(this::toObservationComparisonKey)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            int totalObservationCount = scoutSessions.stream()
                    .map(ScoutingSession::getId)
                    .map(observationsBySession::get)
                    .filter(list -> list != null)
                    .flatMap(List::stream)
                    .mapToInt(o -> o.getCount() == null ? 0 : o.getCount())
                    .sum();

            List<ScoutingPhotoAnalysis> reviewedAnalyses = reviewedAnalysesByScout.getOrDefault(entry.getKey(), List.of());
            List<ScoutingPhotoAnalysis> comparableAnalyses = reviewedAnalyses.stream()
                    .filter(this::hasComparableScoutCell)
                    .toList();
            long exactMatchCount = comparableAnalyses.stream()
                    .filter(analysis -> observationKeys.contains(toPhotoComparisonKey(analysis)))
                    .count();
            int accuracy = comparableAnalyses.isEmpty()
                    ? 0
                    : (int) Math.round((exactMatchCount * 100.0) / comparableAnalyses.size());

            String avgDuration = averageDuration(scoutSessions);

            String scoutName = (scout.getFirstName() + " " + scout.getLastName()).trim();
            performance.add(new ScoutPerformanceDto(
                    scoutName.isBlank() ? scout.getEmail() : scoutName,
                    totalObservationCount,
                    accuracy,
                    avgDuration,
                    comparableAnalyses.size()
            ));
        }

        return performance.stream()
                .sorted(Comparator.comparingInt(ScoutPerformanceDto::observations).reversed())
                .toList();
    }

    private boolean hasComparableScoutCell(ScoutingPhotoAnalysis analysis) {
        return toPhotoComparisonKey(analysis) != null;
    }

    private String toObservationComparisonKey(ScoutingObservation observation) {
        if (observation.getSession() == null
                || observation.getSessionTarget() == null
                || observation.getBayIndex() == null
                || observation.getBenchIndex() == null
                || observation.getSpotIndex() == null
                || observation.getSpeciesCode() == null) {
            return null;
        }

        return observation.getSession().getId()
                + "|" + observation.getSessionTarget().getId()
                + "|" + observation.getBayIndex()
                + "|" + observation.getBenchIndex()
                + "|" + observation.getSpotIndex()
                + "|" + observation.getSpeciesCode().name();
    }

    private String toPhotoComparisonKey(ScoutingPhotoAnalysis analysis) {
        if (analysis.getReviewedSpeciesCode() == null || analysis.getPhoto() == null) {
            return null;
        }

        if (analysis.getPhoto().getObservation() != null
                && analysis.getPhoto().getObservation().getSession() != null
                && analysis.getPhoto().getObservation().getSessionTarget() != null
                && analysis.getPhoto().getObservation().getBayIndex() != null
                && analysis.getPhoto().getObservation().getBenchIndex() != null
                && analysis.getPhoto().getObservation().getSpotIndex() != null) {
            ScoutingObservation observation = analysis.getPhoto().getObservation();
            return observation.getSession().getId()
                    + "|" + observation.getSessionTarget().getId()
                    + "|" + observation.getBayIndex()
                    + "|" + observation.getBenchIndex()
                    + "|" + observation.getSpotIndex()
                    + "|" + analysis.getReviewedSpeciesCode().name();
        }

        if (analysis.getPhoto().getSession() == null
                || analysis.getPhoto().getSessionTarget() == null
                || analysis.getPhoto().getBayIndex() == null
                || analysis.getPhoto().getBenchIndex() == null
                || analysis.getPhoto().getSpotIndex() == null) {
            return null;
        }

        return analysis.getPhoto().getSession().getId()
                + "|" + analysis.getPhoto().getSessionTarget().getId()
                + "|" + analysis.getPhoto().getBayIndex()
                + "|" + analysis.getPhoto().getBenchIndex()
                + "|" + analysis.getPhoto().getSpotIndex()
                + "|" + analysis.getReviewedSpeciesCode().name();
    }

    private int sessionsInPeriod(UUID farmId, LocalDate start, LocalDate end) {
        return sessionRepository.findByFarmIdAndSessionDateBetween(farmId, start, end).size();
    }

    private int totalObservationCount(UUID farmId, LocalDate start, LocalDate end) {
        List<ScoutingSession> sessions = sessionRepository.findByFarmIdAndSessionDateBetween(farmId, start, end);
        if (sessions.isEmpty()) return 0;

        List<UUID> sessionIds = sessions.stream().map(ScoutingSession::getId).toList();
        return observationRepository.findBySessionIdIn(sessionIds).stream()
                .mapToInt(o -> o.getCount() == null ? 0 : o.getCount())
                .sum();
    }

    private int distinctScouts(UUID farmId, LocalDate start, LocalDate end) {
        return (int) sessionRepository.findByFarmIdAndSessionDateBetween(farmId, start, end).stream()
                .filter(s -> s.getScout() != null)
                .map(s -> s.getScout().getId())
                .distinct()
                .count();
    }

    private double averageSeverity(UUID farmId, LocalDate start, LocalDate end) {
        List<ScoutingSession> sessions = sessionRepository.findByFarmIdAndSessionDateBetween(farmId, start, end);
        if (sessions.isEmpty()) return 0;

        List<UUID> sessionIds = sessions.stream().map(ScoutingSession::getId).toList();
        List<ScoutingObservation> observations = observationRepository.findBySessionIdIn(sessionIds);
        if (observations.isEmpty()) return 0;

        double total = observations.stream()
                .mapToInt(o -> o.getCount() == null ? 0 : o.getCount())
                .sum();

        return Math.round((total / observations.size()) * 10.0) / 10.0;
    }

    private double worstSeverity(UUID farmId, LocalDate start, LocalDate end) {
        List<ScoutingSession> sessions = sessionRepository.findByFarmIdAndSessionDateBetween(farmId, start, end);
        if (sessions.isEmpty()) return 0;

        List<UUID> sessionIds = sessions.stream().map(ScoutingSession::getId).toList();
        return observationRepository.findBySessionIdIn(sessionIds).stream()
                .mapToInt(o -> o.getCount() == null ? 0 : o.getCount())
                .max()
                .orElse(0);
    }

    private int distinctSpeciesCount(UUID farmId, LocalDate start, LocalDate end) {
        List<ScoutingSession> sessions = sessionRepository.findByFarmIdAndSessionDateBetween(farmId, start, end);
        if (sessions.isEmpty()) return 0;

        List<UUID> sessionIds = sessions.stream().map(ScoutingSession::getId).toList();
        return (int) observationRepository.findBySessionIdIn(sessionIds).stream()
                .map(this::speciesKey)
                .distinct()
                .count();
    }

    private List<ScoutingObservation> allObservationsForFarm(UUID farmId) {
        List<ScoutingSession> sessions = sessionRepository.findByFarmId(farmId);
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<UUID> sessionIds = sessions.stream()
                .map(ScoutingSession::getId)
                .toList();

        return observationRepository.findBySessionIdIn(sessionIds);
    }

    private String toSeverityLabel(SeverityLevel level) {
        return switch (level) {
            case ZERO -> "zero";
            case LOW -> "low";
            case MODERATE -> "medium";
            case HIGH -> "high";
            case VERY_HIGH, EMERGENCY -> "critical";
        };
    }

    private String averageDuration(List<ScoutingSession> sessions) {
        List<Duration> durations = sessions.stream()
                .map(this::sessionDuration)
                .filter(d -> d != null)
                .toList();

        if (durations.isEmpty()) return "0m";

        long avgSeconds = (long) durations.stream()
                .mapToLong(Duration::getSeconds)
                .average()
                .orElse(0);

        long minutes = avgSeconds / 60;
        long remainingSeconds = avgSeconds % 60;

        if (remainingSeconds == 0) {
            return minutes + "m";
        }

        return minutes + "m " + remainingSeconds + "s";
    }

    private Duration sessionDuration(ScoutingSession session) {
        LocalDateTime start = session.getStartedAt();
        LocalDateTime end = session.getCompletedAt();
        if (start == null || end == null) {
            return null;
        }
        return Duration.between(start, end);
    }

    private String speciesName(ScoutingObservation observation) {
        String displayName = observation.getSpeciesDisplayName();
        return displayName == null ? "Unknown species" : displayName;
    }

    private String speciesKey(ScoutingObservation observation) {
        String identifier = observation.resolveSpeciesIdentifier();
        return identifier == null ? "UNKNOWN" : identifier;
    }
}

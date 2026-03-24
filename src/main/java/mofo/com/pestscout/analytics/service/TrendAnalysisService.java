package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.*;
import mofo.com.pestscout.scouting.model.ObservationCategory;
import mofo.com.pestscout.scouting.model.ScoutingObservation;
import mofo.com.pestscout.scouting.model.SeverityLevel;
import mofo.com.pestscout.scouting.model.SpeciesCode;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class TrendAnalysisService {

    private final ScoutingSessionRepository sessionRepo;
    private final ScoutingObservationRepository obsRepo;
    private final AnalyticsAccessService analyticsAccessService;

    @Transactional(readOnly = true)
    public List<WeeklyPestTrendDto> getWeeklyPestTrends(UUID farmId) {
        analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);
        LocalDate today = LocalDate.now();
        WeekFields weekFields = WeekFields.ISO;
        LocalDate startOfCurrentWeek = today.with(weekFields.dayOfWeek(), 1);
        LocalDate windowStart = startOfCurrentWeek.minusWeeks(6);
        LocalDate windowEnd = startOfCurrentWeek.plusDays(6);

        var sessions = sessionRepo.findByFarmIdAndSessionDateBetween(farmId, windowStart, windowEnd);
        if (sessions.isEmpty()) {
            return List.of();
        }

        Map<UUID, WeekBucketKey> sessionWeek = sessions.stream()
                .collect(Collectors.toMap(
                        s -> s.getId(),
                        s -> new WeekBucketKey(
                                s.getSessionDate().get(weekFields.weekOfWeekBasedYear()),
                                s.getSessionDate().get(weekFields.weekBasedYear())
                        )
                ));

        List<UUID> sessionIds = sessions.stream().map(s -> s.getId()).toList();
        List<ScoutingObservation> observations = obsRepo.findBySessionIdIn(sessionIds);

        Map<WeekBucketKey, PestWeekCounts> weekToCounts = new HashMap<>();
        for (ScoutingObservation observation : observations) {
            if (observation.getCategory() != ObservationCategory.PEST) {
                continue;
            }

            WeekBucketKey weekKey = sessionWeek.get(observation.getSession().getId());
            if (weekKey == null) continue;

            PestWeekCounts counts = weekToCounts.computeIfAbsent(weekKey, k -> new PestWeekCounts());
            counts.apply(observation);
        }

        return IntStream.rangeClosed(0, 6)
                .mapToObj(offset -> {
                    LocalDate weekStart = windowStart.plusWeeks(offset);
                    int weekNumber = weekStart.get(weekFields.weekOfWeekBasedYear());
                    int weekYear = weekStart.get(weekFields.weekBasedYear());
                    PestWeekCounts counts = weekToCounts.getOrDefault(new WeekBucketKey(weekNumber, weekYear), new PestWeekCounts());
                    return new WeeklyPestTrendDto(
                            "%04d-W%02d".formatted(weekYear, weekNumber),
                            counts.thrips,
                            counts.redSpider,
                            counts.whiteflies,
                            counts.mealybugs,
                            counts.caterpillars,
                            counts.fcm,
                            counts.otherPests,
                            weekNumber,
                            weekYear
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SeverityTrendPointDto> getSeverityTrend(UUID farmId) {
        analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);
        LocalDate today = LocalDate.now();
        WeekFields weekFields = WeekFields.ISO;
        LocalDate startOfCurrentWeek = today.with(weekFields.dayOfWeek(), 1);
        LocalDate windowStart = startOfCurrentWeek.minusWeeks(6);
        LocalDate windowEnd = startOfCurrentWeek.plusDays(6);

        var sessions = sessionRepo.findByFarmIdAndSessionDateBetween(farmId, windowStart, windowEnd);
        if (sessions.isEmpty()) {
            return List.of();
        }

        Map<UUID, WeekBucketKey> sessionWeek = sessions.stream()
                .collect(Collectors.toMap(
                        s -> s.getId(),
                        s -> new WeekBucketKey(
                                s.getSessionDate().get(weekFields.weekOfWeekBasedYear()),
                                s.getSessionDate().get(weekFields.weekBasedYear())
                        )
                ));

        List<UUID> sessionIds = sessions.stream().map(s -> s.getId()).toList();
        List<ScoutingObservation> observations = obsRepo.findBySessionIdIn(sessionIds);

        Map<WeekBucketKey, SeverityWeekCounts> weeklyBuckets = new HashMap<>();
        for (ScoutingObservation observation : observations) {
            if (observation.getCategory() == ObservationCategory.BENEFICIAL) {
                continue;
            }

            WeekBucketKey weekKey = sessionWeek.get(observation.getSession().getId());
            if (weekKey == null) continue;

            SeverityLevel level = SeverityLevel.fromCount(Optional.ofNullable(observation.getCount()).orElse(0));
            SeverityWeekCounts buckets = weeklyBuckets.computeIfAbsent(weekKey, k -> new SeverityWeekCounts());
            buckets.apply(level);
        }

        return IntStream.rangeClosed(0, 6)
                .mapToObj(offset -> {
                    LocalDate weekStart = windowStart.plusWeeks(offset);
                    int weekNumber = weekStart.get(weekFields.weekOfWeekBasedYear());
                    int weekYear = weekStart.get(weekFields.weekBasedYear());
                    SeverityWeekCounts buckets = weeklyBuckets.getOrDefault(new WeekBucketKey(weekNumber, weekYear), new SeverityWeekCounts());
                    return new SeverityTrendPointDto(
                            "%04d-W%02d".formatted(weekYear, weekNumber),
                            buckets.zero,
                            buckets.low,
                            buckets.medium,
                            buckets.high,
                            buckets.critical,
                            weekNumber,
                            weekYear
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GreenhouseWeeklyCountDto> getGreenhouseWeeklyCounts(
            UUID farmId,
            Integer requestedYear,
            String speciesCode
    ) {
        analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);

        WeekFields weekFields = WeekFields.ISO;
        int year = requestedYear != null ? requestedYear : LocalDate.now().get(weekFields.weekBasedYear());
        LocalDate windowStart = LocalDate.of(year, 1, 4)
                .with(weekFields.weekOfWeekBasedYear(), 1)
                .with(weekFields.dayOfWeek(), 1);
        LocalDate windowEnd = LocalDate.of(year, 12, 28)
                .with(weekFields.dayOfWeek(), 7);

        var sessions = sessionRepo.findByFarmIdAndSessionDateBetween(farmId, windowStart, windowEnd).stream()
                .filter(session -> session.getSessionDate() != null)
                .filter(session -> session.getSessionDate().get(weekFields.weekBasedYear()) == year)
                .toList();
        if (sessions.isEmpty()) {
            return List.of();
        }

        Map<UUID, LocalDate> sessionDates = sessions.stream()
                .collect(Collectors.toMap(
                        session -> session.getId(),
                        session -> session.getSessionDate()
                ));

        List<ScoutingObservation> observations = obsRepo.findBySessionIdIn(sessionDates.keySet());
        Map<GreenhouseWeekKey, Integer> counts = new HashMap<>();
        String resolvedSpecies = speciesCode == null || speciesCode.isBlank() ? "ALL_PESTS" : speciesCode;

        for (ScoutingObservation observation : observations) {
            if (observation.getCategory() != ObservationCategory.PEST) {
                continue;
            }
            if (speciesCode != null && !speciesCode.isBlank() && !matchesSpeciesQuery(observation, speciesCode)) {
                continue;
            }

            LocalDate sessionDate = sessionDates.get(observation.getSession().getId());
            if (sessionDate == null) {
                continue;
            }

            UUID greenhouseId = observation.getSessionTarget() != null && observation.getSessionTarget().getGreenhouse() != null
                    ? observation.getSessionTarget().getGreenhouse().getId()
                    : null;
            String greenhouseName = observation.getSessionTarget() != null && observation.getSessionTarget().getGreenhouse() != null
                    ? observation.getSessionTarget().getGreenhouse().getName()
                    : null;

            if (greenhouseId == null || greenhouseName == null) {
                continue;
            }

            int weekNumber = sessionDate.get(weekFields.weekOfWeekBasedYear());
            int count = Optional.ofNullable(observation.getCount()).orElse(0);
            counts.merge(new GreenhouseWeekKey(greenhouseId, greenhouseName, weekNumber, year), count, Integer::sum);
        }

        return counts.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<GreenhouseWeekKey, Integer> entry) -> entry.getKey().greenhouseName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> entry.getKey().weekNumber()))
                .map(entry -> new GreenhouseWeeklyCountDto(
                        entry.getKey().greenhouseId(),
                        entry.getKey().greenhouseName(),
                        entry.getKey().weekNumber(),
                        entry.getKey().year(),
                        "%04d-W%02d".formatted(entry.getKey().year(), entry.getKey().weekNumber()),
                        resolvedSpecies,
                        entry.getValue()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PestTrendResponse getPestTrend(
            UUID farmId,
            String speciesCode,
            LocalDate from,
            LocalDate to
    ) {
        analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);
        var sessions = sessionRepo.findByFarmIdAndSessionDateBetween(farmId, from, to);
        Map<LocalDate, Integer> dateToSeverity = new TreeMap<>();

        for (var session : sessions) {
            var observations = obsRepo.findBySessionIdIn(List.of(session.getId()));

            int total = observations.stream()
                    .filter(o -> matchesSpeciesQuery(o, speciesCode))
                    .mapToInt(o -> Optional.ofNullable(o.getCount()).orElse(0))
                    .sum();

            dateToSeverity.merge(session.getSessionDate(), total, Integer::sum);
        }

        List<TrendPointDto> points = dateToSeverity.entrySet().stream()
                .map(e -> new TrendPointDto(e.getKey(), e.getValue()))
                .toList();

        return new PestTrendResponse(farmId, speciesCode, points);
    }

    private boolean matchesSpeciesQuery(ScoutingObservation observation, String speciesQuery) {
        if (speciesQuery == null || speciesQuery.isBlank()) {
            return false;
        }

        SpeciesCode speciesCode = observation.getSpeciesCode();
        if (speciesCode != null && speciesCode.name().equalsIgnoreCase(speciesQuery)) {
            return true;
        }

        String displayName = observation.getSpeciesDisplayName();
        if (displayName != null && displayName.equalsIgnoreCase(speciesQuery)) {
            return true;
        }

        String identifier = observation.resolveSpeciesIdentifier();
        return identifier != null && identifier.equalsIgnoreCase(speciesQuery);
    }

    private static class SeverityWeekCounts {
        int zero;
        int low;
        int medium;
        int high;
        int critical;

        void apply(SeverityLevel level) {
            switch (level) {
                case ZERO -> zero++;
                case LOW -> low++;
                case MODERATE -> medium++;
                case HIGH -> high++;
                case VERY_HIGH, EMERGENCY -> critical++;
            }
        }
    }

    private static class PestWeekCounts {
        int thrips;
        int redSpider;
        int whiteflies;
        int mealybugs;
        int caterpillars;
        int fcm;
        int otherPests;

        void apply(ScoutingObservation observation) {
            int count = Optional.ofNullable(observation.getCount()).orElse(0);
            SpeciesCode speciesCode = observation.getSpeciesCode();
            if (speciesCode == null) {
                otherPests += count;
                return;
            }

            switch (speciesCode) {
                case THRIPS -> thrips += count;
                case RED_SPIDER_MITE -> redSpider += count;
                case WHITEFLIES -> whiteflies += count;
                case MEALYBUGS -> mealybugs += count;
                case CATERPILLARS -> caterpillars += count;
                case FALSE_CODLING_MOTH -> fcm += count;
                default -> otherPests += count;
            }
        }
    }

    private record WeekBucketKey(int weekNumber, int year) {
    }

    private record GreenhouseWeekKey(
            UUID greenhouseId,
            String greenhouseName,
            int weekNumber,
            int year
    ) {
    }
}

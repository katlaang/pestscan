package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.PestTrendResponse;
import mofo.com.pestscout.analytics.dto.SeverityTrendPointDto;
import mofo.com.pestscout.analytics.dto.TrendPointDto;
import mofo.com.pestscout.analytics.dto.WeeklyPestTrendDto;
import mofo.com.pestscout.scouting.model.ObservationCategory;
import mofo.com.pestscout.scouting.model.ScoutingObservation;
import mofo.com.pestscout.scouting.model.SeverityLevel;
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

    @Transactional(readOnly = true)
    public List<WeeklyPestTrendDto> getWeeklyPestTrends(UUID farmId) {
        LocalDate today = LocalDate.now();
        WeekFields weekFields = WeekFields.ISO;
        LocalDate startOfCurrentWeek = today.with(weekFields.dayOfWeek(), 1);
        LocalDate windowStart = startOfCurrentWeek.minusWeeks(6);
        LocalDate windowEnd = startOfCurrentWeek.plusDays(6);

        var sessions = sessionRepo.findByFarmIdAndSessionDateBetween(farmId, windowStart, windowEnd);
        if (sessions.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> sessionWeek = sessions.stream()
                .collect(Collectors.toMap(
                        s -> s.getId(),
                        s -> s.getSessionDate().get(weekFields.weekOfWeekBasedYear())
                ));

        List<UUID> sessionIds = sessions.stream().map(s -> s.getId()).toList();
        List<ScoutingObservation> observations = obsRepo.findBySessionIdIn(sessionIds);

        Map<Integer, PestWeekCounts> weekToCounts = new HashMap<>();
        for (ScoutingObservation observation : observations) {
            if (observation.getCategory() != ObservationCategory.PEST) {
                continue;
            }

            Integer weekNumber = sessionWeek.get(observation.getSession().getId());
            if (weekNumber == null) continue;

            PestWeekCounts counts = weekToCounts.computeIfAbsent(weekNumber, k -> new PestWeekCounts());
            counts.apply(observation);
        }

        return IntStream.rangeClosed(0, 6)
                .mapToObj(offset -> {
                    LocalDate weekStart = windowStart.plusWeeks(offset);
                    int weekNumber = weekStart.get(weekFields.weekOfWeekBasedYear());
                    PestWeekCounts counts = weekToCounts.getOrDefault(weekNumber, new PestWeekCounts());
                    return new WeeklyPestTrendDto(
                            "W" + weekNumber,
                            counts.thrips,
                            counts.redSpider,
                            counts.whiteflies,
                            counts.mealybugs,
                            counts.caterpillars,
                            counts.fcm,
                            counts.otherPests
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SeverityTrendPointDto> getSeverityTrend(UUID farmId) {
        LocalDate today = LocalDate.now();
        WeekFields weekFields = WeekFields.ISO;
        LocalDate startOfCurrentWeek = today.with(weekFields.dayOfWeek(), 1);
        LocalDate windowStart = startOfCurrentWeek.minusWeeks(6);
        LocalDate windowEnd = startOfCurrentWeek.plusDays(6);

        var sessions = sessionRepo.findByFarmIdAndSessionDateBetween(farmId, windowStart, windowEnd);
        if (sessions.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> sessionWeek = sessions.stream()
                .collect(Collectors.toMap(
                        s -> s.getId(),
                        s -> s.getSessionDate().get(weekFields.weekOfWeekBasedYear())
                ));

        List<UUID> sessionIds = sessions.stream().map(s -> s.getId()).toList();
        List<ScoutingObservation> observations = obsRepo.findBySessionIdIn(sessionIds);

        Map<Integer, SeverityWeekCounts> weeklyBuckets = new HashMap<>();
        for (ScoutingObservation observation : observations) {
            if (observation.getCategory() == ObservationCategory.BENEFICIAL) {
                continue;
            }

            Integer weekNumber = sessionWeek.get(observation.getSession().getId());
            if (weekNumber == null) continue;

            SeverityLevel level = SeverityLevel.fromCount(Optional.ofNullable(observation.getCount()).orElse(0));
            SeverityWeekCounts buckets = weeklyBuckets.computeIfAbsent(weekNumber, k -> new SeverityWeekCounts());
            buckets.apply(level);
        }

        return IntStream.rangeClosed(0, 6)
                .mapToObj(offset -> {
                    LocalDate weekStart = windowStart.plusWeeks(offset);
                    int weekNumber = weekStart.get(weekFields.weekOfWeekBasedYear());
                    SeverityWeekCounts buckets = weeklyBuckets.getOrDefault(weekNumber, new SeverityWeekCounts());
                    return new SeverityTrendPointDto(
                            "W" + weekNumber,
                            buckets.zero,
                            buckets.low,
                            buckets.medium,
                            buckets.high,
                            buckets.critical
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PestTrendResponse getPestTrend(
            UUID farmId,
            String speciesCode,
            LocalDate from,
            LocalDate to
    ) {
        var sessions = sessionRepo.findByFarmIdAndSessionDateBetween(farmId, from, to);
        Map<LocalDate, Integer> dateToSeverity = new TreeMap<>();

        for (var session : sessions) {
            var observations = obsRepo.findBySessionIdIn(List.of(session.getId()));

            int total = observations.stream()
                    .filter(o -> o.getSpeciesCode().name().equalsIgnoreCase(speciesCode))
                    .mapToInt(o -> Optional.ofNullable(o.getCount()).orElse(0))
                    .sum();

            dateToSeverity.merge(session.getSessionDate(), total, Integer::sum);
        }

        List<TrendPointDto> points = dateToSeverity.entrySet().stream()
                .map(e -> new TrendPointDto(e.getKey(), e.getValue()))
                .toList();

        return new PestTrendResponse(farmId, speciesCode, points);
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
            switch (observation.getSpeciesCode()) {
                case THRIPS -> thrips += count;
                case RED_SPIDER_MITE -> redSpider += count;
                case WHITEFLIES -> whiteflies += count;
                case MEALYBUGS -> mealybugs += count;
                case FALSE_CODLING_MOTH -> fcm += count;
                default -> {
                }
            }
        }
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
}

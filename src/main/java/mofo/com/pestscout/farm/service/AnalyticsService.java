package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.FarmWeeklyAnalyticsDto;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsService.class);

    private final FarmRepository farmRepository;
    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;

    /**
     * Compute basic weekly analytics for a farm:
     * session counts, observation counts and severity distribution.
     *
     * Cached for 30 minutes since this is an expensive aggregation query.
     * Cache key includes farmId, week, and year to ensure correct data per time period.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = "analytics",
            key = "#farmId.toString() + '::week=' + #week + '::year=' + #year",
            unless = "#result == null || #result.totalObservations() == 0"
    )
    public FarmWeeklyAnalyticsDto computeWeeklyAnalytics(UUID farmId, int week, int year) {
        LOGGER.info("Computing weekly analytics for farm {} week {} year {}", farmId, week, year);

        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        LocalDate firstWeekReference = LocalDate.of(year, 1, 4);

        LocalDate weekStart = firstWeekReference
                .with(weekFields.weekOfWeekBasedYear(), week)
                .with(weekFields.dayOfWeek(), 1);

        LocalDate weekEnd = weekStart.plusDays(6);

        List<ScoutingSession> sessions = sessionRepository
                .findByFarmIdAndSessionDateBetween(farmId, weekStart, weekEnd);

        long totalSessions = sessions.size();
        long completedSessions = sessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.COMPLETED)
                .count();

        if (sessions.isEmpty()) {
            LOGGER.debug("No sessions found for farm {} week {} year {}", farmId, week, year);
            return new FarmWeeklyAnalyticsDto(
                    farm.getId(),
                    farm.getName(),
                    week,
                    year,
                    farm.resolveBayCount(),
                    farm.resolveBenchesPerBay(),
                    totalSessions,
                    completedSessions,
                    0,
                    0,
                    0,
                    0,
                    Map.of()
            );
        }

        List<UUID> sessionIds = sessions.stream()
                .map(ScoutingSession::getId)
                .toList();

        List<ScoutingObservation> observations =
                observationRepository.findBySessionIdIn(sessionIds);

        long pestObservations = 0;
        long diseaseObservations = 0;
        long beneficialObservations = 0;

        Map<String, Long> severityBuckets = new HashMap<>();

        for (ScoutingObservation observation : observations) {
            int count = observation.getCount() != null ? observation.getCount() : 0;
            ObservationCategory category = observation.getCategory();

            if (category == ObservationCategory.PEST) {
                pestObservations += count;
            } else if (category == ObservationCategory.DISEASE) {
                diseaseObservations += count;
            } else if (category == ObservationCategory.BENEFICIAL) {
                beneficialObservations += count;
            }

            if (category == ObservationCategory.PEST || category == ObservationCategory.DISEASE) {
                SeverityLevel level = SeverityLevel.fromCount(count);
                severityBuckets.merge(level.name(), 1L, Long::sum);
            }
        }

        long totalObservations =
                pestObservations + diseaseObservations + beneficialObservations;

        LOGGER.debug("Computed analytics for farm {} week {} year {}: {} observations",
                farmId, week, year, totalObservations);

        return new FarmWeeklyAnalyticsDto(
                farm.getId(),
                farm.getName(),
                week,
                year,
                farm.resolveBayCount(),
                farm.resolveBenchesPerBay(),
                totalSessions,
                completedSessions,
                totalObservations,
                pestObservations,
                diseaseObservations,
                beneficialObservations,
                severityBuckets
        );
    }
}
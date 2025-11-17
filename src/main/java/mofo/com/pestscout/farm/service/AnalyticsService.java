package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.dto.FarmWeeklyAnalyticsDto;
import mofo.com.pestscout.farm.model.*;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.ScoutingObservationRepository;
import mofo.com.pestscout.farm.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final FarmRepository farmRepository;
    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;

    /**
     * Compute basic weekly analytics for a farm:
     * session counts, observation counts and severity distribution.
     */
    @Transactional(readOnly = true)
    public FarmWeeklyAnalyticsDto computeWeeklyAnalytics(UUID farmId, int week, int year) {
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


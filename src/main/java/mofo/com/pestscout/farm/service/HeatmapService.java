package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.dto.HeatmapCellResponse;
import mofo.com.pestscout.farm.dto.HeatmapResponse;
import mofo.com.pestscout.farm.model.*;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.ScoutingObservationRepository;
import mofo.com.pestscout.farm.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HeatmapService {

    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;
    private final FarmRepository farmRepository;

    /**
     * Build a weekly heatmap for a given farm.
     * Aggregates all sessions in the requested ISO week and year.
     */
    public HeatmapResponse generateHeatmap(UUID farmId, int week, int year) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        // Determine calendar week boundaries using locale-specific week rules
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        LocalDate firstWeekReference = LocalDate.of(year, 1, 4);
        LocalDate weekStart = firstWeekReference
                .with(weekFields.weekOfWeekBasedYear(), week)
                .with(weekFields.dayOfWeek(), 1);
        LocalDate weekEnd = weekStart.plusDays(6);

        // Load all sessions for the farm within the week
        List<ScoutingSession> sessions = sessionRepository
                .findByFarmIdAndSessionDateBetween(farmId, weekStart, weekEnd);

        int bayCount = farm.resolveBayCount();
        int benchesPerBay = farm.resolveBenchesPerBay();

        // If there are no sessions, return an empty heatmap with metadata and legend
        if (sessions.isEmpty()) {
            return HeatmapResponse.builder()
                    .farmId(farmId)
                    .farmName(farm.getName())
                    .week(week)
                    .year(year)
                    .bayCount(bayCount)
                    .benchesPerBay(benchesPerBay)
                    .cells(List.of())
                    .severityLegend(SeverityLevel.legend())
                    .build();
        }

        // Collect session ids to fetch observations in one query
        List<UUID> sessionIds = sessions.stream()
                .map(ScoutingSession::getId)
                .toList();

        List<ScoutingObservation> observations =
                observationRepository.findBySessionIdIn(sessionIds);

        // Group observations by (bay, bench) and accumulate counts per category
        Map<String, HeatmapAccumulator> accumulators = new HashMap<>();
        for (ScoutingObservation observation : observations) {
            if (observation.getBayIndex() == null || observation.getBenchIndex() == null) {
                // Skip observations that are not tied to a specific grid cell
                continue;
            }
            String key = observation.getBayIndex() + ":" + observation.getBenchIndex();
            HeatmapAccumulator accumulator = accumulators.computeIfAbsent(
                    key,
                    k -> new HeatmapAccumulator(observation.getBayIndex(), observation.getBenchIndex())
            );
            accumulator.add(observation);
        }

        // Convert accumulators to response cells, ordered by bay then bench
        List<HeatmapCellResponse> cells = accumulators.values().stream()
                .sorted(Comparator
                        .comparing(HeatmapAccumulator::bayIndex)
                        .thenComparing(HeatmapAccumulator::benchIndex))
                .map(HeatmapAccumulator::toResponse)
                .collect(Collectors.toList());

        return HeatmapResponse.builder()
                .farmId(farmId)
                .farmName(farm.getName())
                .week(week)
                .year(year)
                .bayCount(bayCount)
                .benchesPerBay(benchesPerBay)
                .cells(cells)
                .severityLegend(SeverityLevel.legend())
                .build();
    }

    /**
     * Helper used during aggregation to keep running totals
     * for one (bay, bench) cell across all sessions in the week.
     */
    private static class HeatmapAccumulator {

        private final int bayIndex;
        private final int benchIndex;
        private int pestCount;
        private int diseaseCount;
        private int beneficialCount;

        HeatmapAccumulator(int bayIndex, int benchIndex) {
            this.bayIndex = bayIndex;
            this.benchIndex = benchIndex;
        }

        void add(ScoutingObservation observation) {
            int value = observation.getCount() != null ? observation.getCount() : 0;
            ObservationCategory category = observation.getCategory();

            if (category == ObservationCategory.PEST) {
                pestCount += value;
            } else if (category == ObservationCategory.DISEASE) {
                diseaseCount += value;
            } else {
                beneficialCount += value;
            }
        }

        HeatmapCellResponse toResponse() {
            // Severity is based on harmful pressure only (pests + diseases)
            int total = pestCount + diseaseCount;
            SeverityLevel severityLevel = SeverityLevel.fromCount(total);

            return HeatmapCellResponse.builder()
                    .bayIndex(bayIndex)
                    .benchIndex(benchIndex)
                    .pestCount(pestCount)
                    .diseaseCount(diseaseCount)
                    .beneficialCount(beneficialCount)
                    .totalCount(total)
                    .severityLevel(severityLevel)
                    .color(severityLevel.getColorHex())
                    .build();
        }

        int bayIndex() {
            return bayIndex;
        }

        int benchIndex() {
            return benchIndex;
        }
    }
}

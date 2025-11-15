package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.dto.HeatmapCellResponse;
import mofo.com.pestscout.farm.dto.HeatmapResponse;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.ObservationCategory;
import mofo.com.pestscout.farm.model.ScoutingObservation;
import mofo.com.pestscout.farm.model.ScoutingSession;
import mofo.com.pestscout.farm.model.SeverityLevel;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.ScoutingObservationRepository;
import mofo.com.pestscout.farm.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HeatmapService {

    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;
    private final FarmRepository farmRepository;

    public HeatmapResponse generateHeatmap(UUID farmId, int week, int year) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        LocalDate firstWeekReference = LocalDate.of(year, 1, 4);
        LocalDate weekStart = firstWeekReference
                .with(weekFields.weekOfWeekBasedYear(), week)
                .with(weekFields.dayOfWeek(), 1);
        LocalDate weekEnd = weekStart.plusDays(6);

        List<ScoutingSession> sessions = sessionRepository
                .findByFarm_IdAndSessionDateBetween(farmId, weekStart, weekEnd);

        int bayCount = farm.resolveBayCount();
        int benchesPerBay = farm.resolveBenchesPerBay();

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

        List<UUID> sessionIds = sessions.stream()
                .map(ScoutingSession::getId)
                .toList();

        List<ScoutingObservation> observations = observationRepository.findBySession_IdIn(sessionIds);

        Map<String, HeatmapAccumulator> accumulators = new HashMap<>();
        for (ScoutingObservation observation : observations) {
            if (observation.getBayIndex() == null || observation.getBenchIndex() == null) {
                continue;
            }
            String key = observation.getBayIndex() + ":" + observation.getBenchIndex();
            HeatmapAccumulator accumulator = accumulators.computeIfAbsent(key, k -> new HeatmapAccumulator(
                    observation.getBayIndex(),
                    observation.getBenchIndex()));
            accumulator.add(observation);
        }

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
            if (observation.getCategory() == ObservationCategory.PEST) {
                pestCount += value;
            } else if (observation.getCategory() == ObservationCategory.DISEASE) {
                diseaseCount += value;
            } else {
                beneficialCount += value;
            }
        }

        HeatmapCellResponse toResponse() {
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

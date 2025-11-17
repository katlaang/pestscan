package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.dto.HeatmapCellResponse;
import mofo.com.pestscout.farm.dto.HeatmapResponse;
import mofo.com.pestscout.farm.dto.HeatmapSectionResponse;
import mofo.com.pestscout.farm.dto.SeverityLegendEntry;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FieldBlock;
import mofo.com.pestscout.farm.model.Greenhouse;
import mofo.com.pestscout.farm.model.ObservationCategory;
import mofo.com.pestscout.farm.model.ScoutingObservation;
import mofo.com.pestscout.farm.model.ScoutingSession;
import mofo.com.pestscout.farm.model.ScoutingSessionTarget;
import mofo.com.pestscout.farm.model.SeverityLevel;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.ScoutingObservationRepository;
import mofo.com.pestscout.farm.repository.ScoutingSessionRepository;
import mofo.com.pestscout.farm.repository.ScoutingSessionTargetRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds weekly heat maps for a farm.
 *
 * Supports:
 * - Farm level overview grid (aggregated across all session targets).
 * - Per section grids for each greenhouse or field block used in sessions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeatmapService {

    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;
    private final FarmRepository farmRepository;
    private final ScoutingSessionTargetRepository targetRepository;
    private final FarmAccessService farmAccessService;

    /**
     * Build a weekly heat map for a farm.
     *
     * - Uses ISO week and year.
     * - Aggregates all sessions in that week.
     * - Enforces farm view access using FarmAccessService.
     */
    public HeatmapResponse generateHeatmap(UUID farmId, int week, int year) {
        log.info("Generating heatmap for farm {}, week {}, year {}", farmId, week, year);

        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        // Farm level access control: super admin, owner/manager, or assigned scout.
        farmAccessService.requireViewAccess(farm);

        LocalDate weekStart = resolveWeekStart(year, week);
        LocalDate weekEnd = weekStart.plusDays(6);

        // Load all sessions for this farm and week
        List<ScoutingSession> sessions = sessionRepository
                .findByFarmIdAndSessionDateBetween(farmId, weekStart, weekEnd);

        if (sessions.isEmpty()) {
            log.info("No scouting sessions found for farm {} in week {} of {}", farmId, week, year);
            return emptyResponse(farm, week, year);
        }

        List<UUID> sessionIds = sessions.stream()
                .map(ScoutingSession::getId)
                .toList();

        // All targets across the sessions - used to build per section grids
        List<ScoutingSessionTarget> targets = targetRepository.findBySessionIdIn(sessionIds);

        // All observations across the sessions - used for overview and sections
        List<ScoutingObservation> observations = observationRepository.findBySessionIdIn(sessionIds);

        // Farm level overview: aggregate by (bay, bench) across all observations
        Map<String, HeatmapAccumulator> farmAccumulators = new HashMap<>();

        // Section level: one accumulator map per session target
        Map<UUID, SectionAccumulator> sectionMap = new LinkedHashMap<>();
        for (ScoutingSessionTarget target : targets) {
            sectionMap.put(target.getId(), new SectionAccumulator(farm, target));
        }

        for (ScoutingObservation observation : observations) {
            if (observation.getBayIndex() == null || observation.getBenchIndex() == null) {
                // Skip observations that are not tied to a specific grid cell
                continue;
            }

            // 1) Farm-level aggregate cell
            String farmKey = observation.getBayIndex() + ":" + observation.getBenchIndex();
            HeatmapAccumulator farmAccumulator = farmAccumulators.computeIfAbsent(
                    farmKey,
                    k -> new HeatmapAccumulator(
                            observation.getBayIndex(),
                            observation.getBenchIndex()
                    )
            );
            farmAccumulator.add(observation);

            // 2) Section-level cell
            ScoutingSessionTarget target = observation.getSessionTarget();
            SectionAccumulator sectionAccumulator = sectionMap.get(target.getId());
            if (sectionAccumulator != null) {
                sectionAccumulator.add(observation);
            }
        }

        // Convert farm-level accumulators to response cells
        List<HeatmapCellResponse> farmCells = farmAccumulators.values().stream()
                .sorted(Comparator
                        .comparing(HeatmapAccumulator::bayIndex)
                        .thenComparing(HeatmapAccumulator::benchIndex))
                .map(HeatmapAccumulator::toResponse)
                .collect(Collectors.toList());

        // Convert per section accumulators to section DTOs
        List<HeatmapSectionResponse> sectionResponses = sectionMap.values().stream()
                .sorted(Comparator.comparing(SectionAccumulator::getTargetName, String.CASE_INSENSITIVE_ORDER))
                .map(SectionAccumulator::toResponse)
                .toList();

        return HeatmapResponse.builder()
                .farmId(farmId)
                .farmName(farm.getName())
                .week(week)
                .year(year)
                .bayCount(farm.resolveBayCount())
                .benchesPerBay(farm.resolveBenchesPerBay())
                .cells(farmCells)
                .sections(sectionResponses)
                .severityLegend(toLegend())
                .build();
    }

    /**
     * Empty heat map response when there are no sessions.
     * UI can still show legend and metadata.
     */
    private HeatmapResponse emptyResponse(Farm farm, int week, int year) {
        return HeatmapResponse.builder()
                .farmId(farm.getId())
                .farmName(farm.getName())
                .week(week)
                .year(year)
                .bayCount(farm.resolveBayCount())
                .benchesPerBay(farm.resolveBenchesPerBay())
                .cells(List.of())
                .sections(List.of())
                .severityLegend(toLegend())
                .build();
    }

    /**
     * Convert SeverityLevel to API friendly legend entries.
     */
    private List<SeverityLegendEntry> toLegend() {
        return SeverityLevel.orderedLevels().stream()
                .map(SeverityLegendEntry::from)
                .toList();
    }

    /**
     * Resolve ISO week start (Monday) for the given week and year.
     */
    private LocalDate resolveWeekStart(int year, int week) {
        WeekFields weekFields = WeekFields.ISO;
        LocalDate firstWeekReference = LocalDate.of(year, 1, 4);
        return firstWeekReference
                .with(weekFields.weekOfWeekBasedYear(), week)
                .with(weekFields.dayOfWeek(), 1);
    }

    /**
     * Aggregator for a single farm level cell (bay, bench).
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
            } else if (category == ObservationCategory.BENEFICIAL) {
                beneficialCount += value;
            }
        }

        HeatmapCellResponse toResponse() {
            int totalHarmful = pestCount + diseaseCount;
            SeverityLevel severityLevel = SeverityLevel.fromCount(totalHarmful);

            return HeatmapCellResponse.builder()
                    .bayIndex(bayIndex)
                    .benchIndex(benchIndex)
                    .pestCount(pestCount)
                    .diseaseCount(diseaseCount)
                    .beneficialCount(beneficialCount)
                    .totalCount(totalHarmful)
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

    /**
     * Aggregator for one section (one session target - greenhouse or field block).
     */
    private static class SectionAccumulator {

        private final UUID targetId;
        private final UUID greenhouseId;
        private final UUID fieldBlockId;
        private final String targetName;
        private final int bayCount;
        private final int benchesPerBay;

        private final Map<String, HeatmapAccumulator> accumulators = new HashMap<>();

        SectionAccumulator(Farm farm, ScoutingSessionTarget target) {
            this.targetId = target.getId();
            Greenhouse greenhouse = target.getGreenhouse();
            FieldBlock fieldBlock = target.getFieldBlock();

            this.greenhouseId = greenhouse != null ? greenhouse.getId() : null;
            this.fieldBlockId = fieldBlock != null ? fieldBlock.getId() : null;

            this.targetName = greenhouse != null
                    ? greenhouse.getName()
                    : fieldBlock != null ? fieldBlock.getName() : farm.getName();

            this.bayCount = greenhouse != null
                    ? greenhouse.resolvedBayCount()
                    : fieldBlock != null
                    ? fieldBlock.resolvedBayCount()
                    : farm.resolveBayCount();

            this.benchesPerBay = greenhouse != null
                    ? greenhouse.resolvedBenchesPerBay()
                    : farm.resolveBenchesPerBay();
        }

        void add(ScoutingObservation observation) {
            String key = observation.getBayIndex() + ":" + observation.getBenchIndex();
            HeatmapAccumulator accumulator = accumulators.computeIfAbsent(
                    key,
                    k -> new HeatmapAccumulator(observation.getBayIndex(), observation.getBenchIndex())
            );
            accumulator.add(observation);
        }

        HeatmapSectionResponse toResponse() {
            List<HeatmapCellResponse> cells = accumulators.values().stream()
                    .sorted(Comparator
                            .comparing(HeatmapAccumulator::bayIndex)
                            .thenComparing(HeatmapAccumulator::benchIndex))
                    .map(HeatmapAccumulator::toResponse)
                    .collect(Collectors.toList());

            return new HeatmapSectionResponse(
                    targetId,
                    greenhouseId,
                    fieldBlockId,
                    targetName,
                    bayCount,
                    benchesPerBay,
                    cells
            );
        }

        String getTargetName() {
            return targetName;
        }
    }
}

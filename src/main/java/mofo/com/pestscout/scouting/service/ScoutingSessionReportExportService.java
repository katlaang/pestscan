package mofo.com.pestscout.scouting.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.scouting.dto.*;
import mofo.com.pestscout.scouting.model.ScoutingSession;
import mofo.com.pestscout.scouting.model.ScoutingSessionTarget;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScoutingSessionReportExportService {

    private static final String CSV_MEDIA_TYPE = "text/csv";
    private static final String UTF8_BOM = "\uFEFF";

    private final ScoutingSessionService scoutingSessionService;
    private final ScoutingSessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public GeneratedCsvDocument exportSessionCsv(UUID sessionId) {
        // Reuse the existing session access rules so only permitted farm roles can export.
        ScoutingSessionDetailDto report = scoutingSessionService.getSession(sessionId);
        ScoutingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", sessionId));

        String content = UTF8_BOM + buildCsv(session, report);
        return new GeneratedCsvDocument(
                buildFileName(report.id(), report.sessionDate()),
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String buildCsv(ScoutingSession session, ScoutingSessionDetailDto report) {
        Map<UUID, SectionMeta> sectionMetadata = session.getTargets().stream()
                .collect(Collectors.toMap(
                        ScoutingSessionTarget::getId,
                        this::toSectionMeta,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        String recommendationSummary = report.recommendations() == null
                ? ""
                : report.recommendations().stream()
                .map(this::formatRecommendation)
                .collect(Collectors.joining(" | "));

        List<String> lines = new ArrayList<>();
        lines.add(csvRow(
                "session_id",
                "session_date",
                "week_number",
                "status",
                "farm_id",
                "farm_name",
                "manager_id",
                "scout_id",
                "crop",
                "variety",
                "temperature_celsius",
                "relative_humidity_percent",
                "observation_time",
                "weather_notes",
                "session_notes",
                "recommendations",
                "section_target_id",
                "section_type",
                "section_name",
                "section_area_hectares",
                "covered_bays",
                "total_bays",
                "covered_beds",
                "total_beds",
                "section_fully_covered",
                "species_code",
                "custom_species_id",
                "species_name",
                "category",
                "bay_index",
                "bay_id",
                "bed_index",
                "bed_id",
                "spot_index",
                "count",
                "observation_notes",
                "observation_updated_at"
        ));

        if (report.sections() == null || report.sections().isEmpty()) {
            lines.add(csvRow(
                    report.id(),
                    report.sessionDate(),
                    report.weekNumber(),
                    report.status(),
                    report.farmId(),
                    session.getFarm().getName(),
                    report.managerId(),
                    report.scoutId(),
                    report.crop(),
                    report.variety(),
                    report.temperatureCelsius(),
                    report.relativeHumidityPercent(),
                    report.observationTime(),
                    report.weatherNotes(),
                    report.notes(),
                    recommendationSummary,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
            return String.join(System.lineSeparator(), lines);
        }

        for (ScoutingSessionSectionDto section : report.sections()) {
            SectionMeta sectionMeta = sectionMetadata.getOrDefault(section.targetId(), SectionMeta.empty());
            ScoutingSectionCoverageDto coverage = section.coverage();
            List<ScoutingObservationDto> observations = section.observations() == null
                    ? List.of()
                    : section.observations();

            if (observations.isEmpty()) {
                lines.add(csvRow(
                        report.id(),
                        report.sessionDate(),
                        report.weekNumber(),
                        report.status(),
                        report.farmId(),
                        session.getFarm().getName(),
                        report.managerId(),
                        report.scoutId(),
                        report.crop(),
                        report.variety(),
                        report.temperatureCelsius(),
                        report.relativeHumidityPercent(),
                        report.observationTime(),
                        report.weatherNotes(),
                        report.notes(),
                        recommendationSummary,
                        section.targetId(),
                        sectionMeta.type(),
                        sectionMeta.name(),
                        section.areaHectares(),
                        coverageValue(coverage, true, true),
                        coverageValue(coverage, true, false),
                        coverageValue(coverage, false, true),
                        coverageValue(coverage, false, false),
                        coverage == null ? null : coverage.fullyCovered(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
                continue;
            }

            for (ScoutingObservationDto observation : observations) {
                lines.add(csvRow(
                        report.id(),
                        report.sessionDate(),
                        report.weekNumber(),
                        report.status(),
                        report.farmId(),
                        session.getFarm().getName(),
                        report.managerId(),
                        report.scoutId(),
                        report.crop(),
                        report.variety(),
                        report.temperatureCelsius(),
                        report.relativeHumidityPercent(),
                        report.observationTime(),
                        report.weatherNotes(),
                        report.notes(),
                        recommendationSummary,
                        section.targetId(),
                        sectionMeta.type(),
                        sectionMeta.name(),
                        section.areaHectares(),
                        coverageValue(coverage, true, true),
                        coverageValue(coverage, true, false),
                        coverageValue(coverage, false, true),
                        coverageValue(coverage, false, false),
                        coverage == null ? null : coverage.fullyCovered(),
                        observation.speciesCode(),
                        observation.customSpeciesId(),
                        observation.speciesDisplayName(),
                        observation.category(),
                        observation.bayIndex(),
                        observation.bayTag(),
                        observation.benchIndex(),
                        observation.benchTag(),
                        observation.spotIndex(),
                        observation.count(),
                        observation.notes(),
                        observation.updatedAt()
                ));
            }
        }

        return String.join(System.lineSeparator(), lines);
    }

    private String buildFileName(UUID sessionId, LocalDate sessionDate) {
        String suffix = sessionDate != null ? sessionDate.toString() : "undated";
        return "scouting-session-" + sessionId + "-" + suffix + ".csv";
    }

    private String formatRecommendation(RecommendationEntryDto recommendation) {
        if (recommendation == null || recommendation.type() == null) {
            return "";
        }
        return recommendation.type().name() + ": " + nullSafe(recommendation.text());
    }

    private SectionMeta toSectionMeta(ScoutingSessionTarget target) {
        if (target.getGreenhouse() != null) {
            return new SectionMeta("GREENHOUSE", target.getGreenhouse().getName());
        }
        if (target.getFieldBlock() != null) {
            return new SectionMeta("FIELD", target.getFieldBlock().getName());
        }
        return SectionMeta.empty();
    }

    private Integer coverageValue(ScoutingSectionCoverageDto coverage, boolean bayMetric, boolean coveredMetric) {
        if (coverage == null) {
            return null;
        }
        if (bayMetric) {
            return coveredMetric ? coverage.coveredBayCount() : coverage.totalBayCount();
        }
        return coveredMetric ? coverage.coveredBedCount() : coverage.totalBedCount();
    }

    private String csvRow(Object... values) {
        return java.util.Arrays.stream(values)
                .map(this::toCsvCell)
                .collect(Collectors.joining(","));
    }

    private String toCsvCell(Object value) {
        String normalized = sanitizeForSpreadsheet(nullSafe(value));
        return "\"" + normalized.replace("\"", "\"\"") + "\"";
    }

    private String nullSafe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String sanitizeForSpreadsheet(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char firstChar = value.charAt(0);
        if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@'
                || firstChar == '\t' || firstChar == '\r') {
            return "'" + value;
        }
        return value;
    }

    public record GeneratedCsvDocument(String fileName, byte[] content) {
        public String mediaType() {
            return CSV_MEDIA_TYPE;
        }
    }

    private record SectionMeta(String type, String name) {
        private static SectionMeta empty() {
            return new SectionMeta("", "");
        }
    }
}

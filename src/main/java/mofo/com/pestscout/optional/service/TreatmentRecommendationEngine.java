package mofo.com.pestscout.optional.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.optional.dto.OptionalCapabilityDtos.TreatmentRecommendationItem;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TreatmentRecommendationEngine {

    private static final int LOOKBACK_DAYS = 21;

    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;

    private static int priorityRank(TreatmentRecommendationItem item) {
        return switch (item.priority()) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            default -> 3;
        };
    }

    @Transactional(readOnly = true)
    public List<TreatmentRecommendationItem> generateForFarm(UUID farmId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(LOOKBACK_DAYS);

        List<ScoutingSession> sessions = sessionRepository.findByFarmIdAndSessionDateBetween(farmId, startDate, endDate);
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<UUID> sessionIds = sessions.stream()
                .map(ScoutingSession::getId)
                .toList();

        List<ScoutingObservation> observations = observationRepository.findBySessionIdIn(sessionIds);
        Map<RecommendationKey, RecommendationAccumulator> accumulators = new LinkedHashMap<>();

        for (ScoutingObservation observation : observations) {
            if (observation.getCategory() == ObservationCategory.BENEFICIAL) {
                continue;
            }

            int count = Optional.ofNullable(observation.getCount()).orElse(0);
            if (count <= 0) {
                continue;
            }

            String sectionName = resolveSectionName(observation);
            RecommendationKey key = new RecommendationKey(sectionName, observation.resolveSpeciesIdentifier());
            RecommendationAccumulator accumulator = accumulators.computeIfAbsent(
                    key,
                    ignored -> new RecommendationAccumulator(sectionName, observation)
            );
            accumulator.add(observation, count);
        }

        return accumulators.values().stream()
                .map(RecommendationAccumulator::toResponse)
                .sorted(Comparator
                        .comparing(TreatmentRecommendationEngine::priorityRank)
                        .thenComparing(TreatmentRecommendationItem::observedCount, Comparator.reverseOrder())
                        .thenComparing(TreatmentRecommendationItem::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String resolveSectionName(ScoutingObservation observation) {
        ScoutingSessionTarget target = observation.getSessionTarget();
        if (target == null) {
            return observation.getSession() != null && observation.getSession().getFarm() != null
                    ? observation.getSession().getFarm().getName()
                    : "Farm overview";
        }

        if (target.getGreenhouse() != null && target.getGreenhouse().getName() != null) {
            return target.getGreenhouse().getName();
        }

        if (target.getFieldBlock() != null && target.getFieldBlock().getName() != null) {
            return target.getFieldBlock().getName();
        }

        return observation.getSession() != null && observation.getSession().getFarm() != null
                ? observation.getSession().getFarm().getName()
                : "Farm overview";
    }

    private record RecommendationKey(String sectionName, String speciesIdentifier) {
    }

    private static final class RecommendationAccumulator {

        private final String sectionName;
        private final String speciesIdentifier;
        private final String speciesDisplayName;
        private final ObservationCategory category;
        private final SpeciesCode speciesCode;
        private final List<String> notes = new ArrayList<>();
        private int observedCount;
        private int affectedCells;
        private SeverityLevel maxSeverity = SeverityLevel.ZERO;

        private RecommendationAccumulator(String sectionName, ScoutingObservation observation) {
            this.sectionName = sectionName;
            this.speciesIdentifier = observation.resolveSpeciesIdentifier();
            this.speciesDisplayName = observation.getSpeciesDisplayName();
            this.category = observation.getCategory();
            this.speciesCode = observation.getSpeciesCode();
        }

        private static String toPriority(SeverityLevel severity, int totalCount) {
            if (severity == SeverityLevel.EMERGENCY || severity == SeverityLevel.VERY_HIGH || totalCount >= 40) {
                return "CRITICAL";
            }
            if (severity == SeverityLevel.HIGH || totalCount >= 20) {
                return "HIGH";
            }
            if (severity == SeverityLevel.MODERATE || totalCount >= 8) {
                return "MEDIUM";
            }
            return "LOW";
        }

        private void add(ScoutingObservation observation, int count) {
            observedCount += count;
            affectedCells++;

            SeverityLevel severity = SeverityLevel.fromCount(count);
            if (severity.ordinal() > maxSeverity.ordinal()) {
                maxSeverity = severity;
            }

            if (observation.getNotes() != null && !observation.getNotes().isBlank()) {
                notes.add(observation.getNotes().trim());
            }
        }

        private TreatmentRecommendationItem toResponse() {
            RecommendationTemplate template = RecommendationTemplate.forSpecies(speciesCode, category);
            String priority = toPriority(maxSeverity, observedCount);
            BigDecimal suggestedQuantity = template.suggestedQuantity(observedCount, affectedCells);
            BigDecimal unitPrice = template.estimatedUnitPrice();
            String rationale = buildRationale(template);

            return new TreatmentRecommendationItem(
                    speciesCode != null ? speciesCode.name() : speciesIdentifier,
                    speciesDisplayName,
                    category.name(),
                    sectionName,
                    observedCount,
                    maxSeverity.name(),
                    priority,
                    template.treatmentType(),
                    template.action(),
                    rationale,
                    template.skuHint(),
                    template.supplyItemName(),
                    suggestedQuantity,
                    template.unitOfMeasure(),
                    unitPrice
            );
        }

        private String buildRationale(RecommendationTemplate template) {
            StringBuilder builder = new StringBuilder();
            builder.append("Detected ")
                    .append(observedCount)
                    .append(' ')
                    .append(speciesDisplayName.toLowerCase())
                    .append(" observations across ")
                    .append(affectedCells)
                    .append(affectedCells == 1 ? " affected cell" : " affected cells")
                    .append(" in ")
                    .append(sectionName)
                    .append('.');

            if (!notes.isEmpty()) {
                builder.append(" Scout notes: ").append(notes.get(0));
            } else {
                builder.append(' ').append(template.rationaleSuffix());
            }

            return builder.toString();
        }
    }

    private record RecommendationTemplate(
            String treatmentType,
            String action,
            String rationaleSuffix,
            String skuHint,
            String supplyItemName,
            BigDecimal baseQuantity,
            String unitOfMeasure,
            BigDecimal estimatedUnitPrice
    ) {

        private static RecommendationTemplate forSpecies(SpeciesCode speciesCode, ObservationCategory category) {
            if (speciesCode == null) {
                return switch (category) {
                    case PEST -> new RecommendationTemplate(
                            "SCOUTING_ESCALATION",
                            "Escalate for manual review and deploy a broad-spectrum monitoring pack.",
                            "A custom pest should be reviewed before selecting a targeted treatment.",
                            "KIT-SCOUT-REVIEW",
                            "Scouting review kit",
                            new BigDecimal("1.00"),
                            "kit",
                            new BigDecimal("15.00")
                    );
                    case DISEASE -> new RecommendationTemplate(
                            "SANITATION_AND_FUNGICIDE",
                            "Remove affected material, tighten environment control, and apply a disease treatment.",
                            "A custom disease should be handled conservatively with sanitation and disease control.",
                            "FUNGI-PROTECT",
                            "Protective fungicide pack",
                            new BigDecimal("1.00"),
                            "pack",
                            new BigDecimal("41.00")
                    );
                    case BENEFICIAL -> new RecommendationTemplate(
                            "SCOUTING_ESCALATION",
                            "Review manually before action.",
                            "Beneficial detections do not trigger treatment orders by default.",
                            "KIT-SCOUT-REVIEW",
                            "Scouting review kit",
                            new BigDecimal("1.00"),
                            "kit",
                            new BigDecimal("15.00")
                    );
                };
            }

            return switch (speciesCode) {
                case THRIPS -> new RecommendationTemplate(
                        "BIOLOGICAL_CONTROL",
                        "Release beneficial mites and refresh sticky trap monitoring.",
                        "This pattern usually responds best to early biological controls.",
                        "BIO-THRIPS-PRED",
                        "Predatory mite sachets",
                        new BigDecimal("2.00"),
                        "pack",
                        new BigDecimal("34.50")
                );
                case RED_SPIDER_MITE -> new RecommendationTemplate(
                        "BIOLOGICAL_CONTROL",
                        "Increase miticide rotation or release predatory mites on hotspot rows.",
                        "Mite pressure often escalates quickly in warm, dry zones.",
                        "BIO-MITE-PRED",
                        "Predatory mite release pack",
                        new BigDecimal("2.00"),
                        "pack",
                        new BigDecimal("39.00")
                );
                case WHITEFLIES -> new RecommendationTemplate(
                        "TRAP_AND_CHEMICAL_ROTATION",
                        "Replace yellow sticky cards and review rotation coverage.",
                        "Whitefly pressure usually requires trap refresh plus targeted treatment.",
                        "TRAP-WF-YELLOW",
                        "Yellow sticky card roll",
                        new BigDecimal("1.00"),
                        "roll",
                        new BigDecimal("26.00")
                );
                case MEALYBUGS -> new RecommendationTemplate(
                        "TARGETED_CONTACT_TREATMENT",
                        "Isolate infested plants and apply a targeted contact treatment.",
                        "Localized sanitation and contact products reduce spread.",
                        "SPRAY-MB-CONTACT",
                        "Contact treatment concentrate",
                        new BigDecimal("1.00"),
                        "litre",
                        new BigDecimal("18.50")
                );
                case CATERPILLARS, FALSE_CODLING_MOTH -> new RecommendationTemplate(
                        "BIOLOGICAL_LARVICIDE",
                        "Apply a larvicide treatment and verify canopy coverage on the next round.",
                        "Larval pests are best handled before counts move into adjacent bays.",
                        "BIO-LARV-BT",
                        "Bt larvicide concentrate",
                        new BigDecimal("2.00"),
                        "litre",
                        new BigDecimal("29.50")
                );
                case DOWNY_MILDEW, POWDERY_MILDEW, BOTRYTIS, VERTICILLIUM, BACTERIAL_WILT, DISEASE_OTHER ->
                        new RecommendationTemplate(
                                "SANITATION_AND_FUNGICIDE",
                                "Remove infected material, tighten humidity control, and apply a disease treatment.",
                                "Disease pressure needs sanitation plus environment control to avoid repeat spread.",
                                "FUNGI-PROTECT",
                                "Protective fungicide pack",
                                new BigDecimal("1.00"),
                                "pack",
                                new BigDecimal("41.00")
                        );
                case PEST_OTHER, BENEFICIAL_PP, BENEFICIAL_OTHER -> new RecommendationTemplate(
                        "SCOUTING_ESCALATION",
                        "Escalate for manual review and use a broad-spectrum monitoring supply pack.",
                        "The species needs confirmation before a more specific treatment is ordered.",
                        "KIT-SCOUT-REVIEW",
                        "Scouting review kit",
                        new BigDecimal("1.00"),
                        "kit",
                        new BigDecimal("15.00")
                );
            };
        }

        private BigDecimal suggestedQuantity(int observedCount, int affectedCells) {
            int multiplier = Math.max(1, (int) Math.ceil(observedCount / 10.0));
            multiplier = Math.max(multiplier, Math.max(1, affectedCells / 2));
            return baseQuantity.multiply(BigDecimal.valueOf(multiplier))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }
}

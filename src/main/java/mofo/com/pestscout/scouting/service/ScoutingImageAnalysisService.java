package mofo.com.pestscout.scouting.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.scouting.dto.ImageAnalysisDtos.*;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingPhotoAnalysisRepository;
import mofo.com.pestscout.scouting.repository.ScoutingPhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScoutingImageAnalysisService {

    private static final String PROVIDER = "heuristic-local-v1";
    private static final String MODEL_VERSION = "heuristic-local-v1";

    private final ScoutingAnalysisAccessService accessService;
    private final CurrentUserService currentUserService;
    private final ScoutingPhotoRepository photoRepository;
    private final ScoutingObservationRepository observationRepository;
    private final ScoutingPhotoAnalysisRepository analysisRepository;

    @Transactional
    public PhotoAnalysisResponse analyzePhoto(UUID farmId, UUID photoId) {
        accessService.loadFarmAndEnsureViewer(farmId);
        ScoutingPhoto photo = loadPhoto(photoId, farmId);

        List<ResolvedCandidate> resolvedCandidates = resolveCandidates(photo);
        ResolvedCandidate topCandidate = resolvedCandidates.getFirst();
        boolean requiresHumanReview = topCandidate.confidence() < 0.82d
                || photo.getObservation() == null
                || resolveSourceType(photo) == PhotoSourceType.DRONE;

        ScoutingPhotoAnalysis analysis = analysisRepository.findByPhoto_Id(photoId)
                .orElseGet(() -> ScoutingPhotoAnalysis.builder()
                        .photo(photo)
                        .farmId(farmId)
                        .build());

        analysis.setPhoto(photo);
        analysis.setFarmId(farmId);
        analysis.setProvider(PROVIDER);
        analysis.setModelVersion(MODEL_VERSION);
        analysis.setSummary(buildSummary(photo, topCandidate));
        analysis.setPredictedSpeciesCode(topCandidate.speciesCode());
        analysis.setPredictedConfidence(toDecimal(topCandidate.confidence()));
        analysis.setCandidates(new ArrayList<>(resolvedCandidates.stream()
                .map(candidate -> new ScoutingPhotoAnalysisCandidate(
                        candidate.speciesCode(),
                        toDecimal(candidate.confidence()),
                        candidate.rationale()
                ))
                .toList()));

        if (analysis.getReviewStatus() == null || analysis.getReviewedSpeciesCode() == null) {
            analysis.setReviewStatus(PhotoAnalysisReviewStatus.PENDING_REVIEW);
        }
        analysis.setReviewRequired(analysis.getReviewStatus() == PhotoAnalysisReviewStatus.PENDING_REVIEW && requiresHumanReview);

        ScoutingPhotoAnalysis saved = analysisRepository.save(analysis);
        return toResponse(saved);
    }

    @Transactional
    public PhotoAnalysisResponse getPhotoAnalysis(UUID farmId, UUID photoId) {
        accessService.loadFarmAndEnsureViewer(farmId);
        loadPhoto(photoId, farmId);
        Optional<ScoutingPhotoAnalysis> existing = analysisRepository.findByPhoto_Id(photoId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        return analyzePhoto(farmId, photoId);
    }

    @Transactional
    public PhotoAnalysisResponse reviewPhotoAnalysis(UUID photoId, PhotoAnalysisReviewRequest request) {
        accessService.loadFarmAndEnsureManager(request.farmId());
        loadPhoto(photoId, request.farmId());

        ScoutingPhotoAnalysis analysis = analysisRepository.findByPhoto_Id(photoId)
                .orElseGet(() -> {
                    analyzePhoto(request.farmId(), photoId);
                    return analysisRepository.findByPhoto_Id(photoId)
                            .orElseThrow(() -> new ResourceNotFoundException("ScoutingPhotoAnalysis", "photoId", photoId));
                });

        User reviewer = currentUserService.getCurrentUser();
        SpeciesCode reviewedSpeciesCode = request.speciesCode();
        analysis.setReviewedSpeciesCode(reviewedSpeciesCode);
        analysis.setReviewStatus(Objects.equals(analysis.getPredictedSpeciesCode(), reviewedSpeciesCode)
                ? PhotoAnalysisReviewStatus.CONFIRMED
                : PhotoAnalysisReviewStatus.CORRECTED);
        analysis.setReviewedAt(LocalDateTime.now());
        analysis.setReviewerId(reviewer.getId());
        analysis.setReviewerName(resolveReviewerName(reviewer));
        analysis.setReviewNotes(request.reviewNotes());
        analysis.setReviewRequired(false);

        return toResponse(analysisRepository.save(analysis));
    }

    @Transactional(readOnly = true)
    public PhotoAnalysisAccuracyResponse getAccuracy(UUID farmId) {
        accessService.loadFarmAndEnsureManager(farmId);

        List<ScoutingPhotoAnalysis> allAnalyses = analysisRepository.findByFarmId(farmId);
        List<ScoutingPhotoAnalysis> reviewedAnalyses = analysisRepository.findByFarmIdAndReviewStatusIn(
                farmId,
                List.of(PhotoAnalysisReviewStatus.CONFIRMED, PhotoAnalysisReviewStatus.CORRECTED)
        );

        long pendingReviewCount = allAnalyses.stream()
                .filter(analysis -> analysis.getReviewStatus() == PhotoAnalysisReviewStatus.PENDING_REVIEW)
                .count();

        long exactMatchCount = reviewedAnalyses.stream()
                .filter(this::isExactMatch)
                .count();

        long correctedCount = reviewedAnalyses.stream()
                .filter(analysis -> analysis.getReviewStatus() == PhotoAnalysisReviewStatus.CORRECTED)
                .count();

        double accuracyRate = reviewedAnalyses.isEmpty()
                ? 0.0d
                : round(exactMatchCount / (double) reviewedAnalyses.size());

        OptionalDouble averageConfidence = reviewedAnalyses.stream()
                .map(ScoutingPhotoAnalysis::getPredictedConfidence)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average();
        double averagePredictedConfidence = averageConfidence.isPresent()
                ? round(averageConfidence.getAsDouble())
                : 0.0d;

        List<PhotoAnalysisAccuracyBySpecies> speciesBreakdown = reviewedAnalyses.stream()
                .filter(analysis -> analysis.getReviewedSpeciesCode() != null)
                .collect(
                        LinkedHashMap<SpeciesCode, List<ScoutingPhotoAnalysis>>::new,
                        (accumulator, analysis) -> accumulator
                                .computeIfAbsent(analysis.getReviewedSpeciesCode(), key -> new ArrayList<>())
                                .add(analysis),
                        LinkedHashMap::putAll
                )
                .entrySet().stream()
                .map(entry -> {
                    long reviewedCount = entry.getValue().size();
                    long correctCount = entry.getValue().stream()
                            .filter(this::isExactMatch)
                            .count();
                    SpeciesCode speciesCode = entry.getKey();
                    return new PhotoAnalysisAccuracyBySpecies(
                            speciesCode.name(),
                            speciesCode.getDisplayName(),
                            reviewedCount,
                            correctCount,
                            reviewedCount == 0 ? 0.0d : round(correctCount / (double) reviewedCount)
                    );
                })
                .sorted(Comparator.comparing(PhotoAnalysisAccuracyBySpecies::reviewedCount).reversed())
                .toList();

        return new PhotoAnalysisAccuracyResponse(
                farmId,
                PROVIDER,
                MODEL_VERSION,
                allAnalyses.size(),
                pendingReviewCount,
                reviewedAnalyses.size(),
                exactMatchCount,
                correctedCount,
                accuracyRate,
                averagePredictedConfidence,
                speciesBreakdown
        );
    }

    private ScoutingPhoto loadPhoto(UUID photoId, UUID farmId) {
        ScoutingPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingPhoto", "id", photoId));

        if (!farmId.equals(photo.getFarmId())) {
            throw new ResourceNotFoundException("ScoutingPhoto", "id", photoId);
        }

        return photo;
    }

    private List<ResolvedCandidate> resolveCandidates(ScoutingPhoto photo) {
        List<ScoutingObservation> sessionObservations = observationRepository.findBySessionId(photo.getSession().getId());
        EnumMap<SpeciesCode, CandidateAccumulator> candidates = new EnumMap<>(SpeciesCode.class);

        if (photo.getObservation() != null && photo.getObservation().getSpeciesCode() != null) {
            int count = Optional.ofNullable(photo.getObservation().getCount()).orElse(0);
            double linkedConfidence = count >= 10 ? 0.86d : count >= 5 ? 0.78d : 0.68d;
            upsertCandidate(
                    candidates,
                    photo.getObservation().getSpeciesCode(),
                    linkedConfidence,
                    "Linked to a scouting observation recorded for this image."
            );
        }

        String metadata = normalizeText(
                photo.getPurpose(),
                photo.getObjectKey(),
                photo.getLocalPhotoId(),
                resolveSourceType(photo).name()
        );

        keywordSpecies().forEach((speciesCode, keywords) -> {
            for (String keyword : keywords) {
                if (metadata.contains(keyword)) {
                    upsertCandidate(
                            candidates,
                            speciesCode,
                            0.19d,
                            "Photo metadata contains keyword '" + keyword + "'."
                    );
                    break;
                }
            }
        });

        Map<SpeciesCode, Integer> sessionCounts = aggregateCountsBySpecies(sessionObservations);
        int totalSessionCount = sessionCounts.values().stream().mapToInt(Integer::intValue).sum();
        sessionCounts.entrySet().stream()
                .sorted(Map.Entry.<SpeciesCode, Integer>comparingByValue().reversed())
                .limit(3)
                .forEach(entry -> {
                    double contextualBoost = totalSessionCount == 0
                            ? 0.08d
                            : Math.min(0.22d, entry.getValue() / (double) totalSessionCount);
                    upsertCandidate(
                            candidates,
                            entry.getKey(),
                            contextualBoost,
                            "Commonly observed in the same scouting session (" + entry.getValue() + " counts)."
                    );
                });

        if (candidates.isEmpty()) {
            upsertCandidate(
                    candidates,
                    fallbackSpecies(metadata),
                    0.34d,
                    "No strong heuristic match was found in photo metadata or linked observations."
            );
        }

        return candidates.values().stream()
                .map(CandidateAccumulator::toResolvedCandidate)
                .sorted(Comparator.comparing(ResolvedCandidate::confidence).reversed())
                .limit(3)
                .toList();
    }

    private Map<SpeciesCode, Integer> aggregateCountsBySpecies(List<ScoutingObservation> observations) {
        Map<SpeciesCode, Integer> counts = new LinkedHashMap<>();
        for (ScoutingObservation observation : observations) {
            if (observation.getSpeciesCode() == null) {
                continue;
            }
            counts.merge(
                    observation.getSpeciesCode(),
                    Optional.ofNullable(observation.getCount()).orElse(0),
                    Integer::sum
            );
        }
        return counts;
    }

    private Map<SpeciesCode, List<String>> keywordSpecies() {
        Map<SpeciesCode, List<String>> keywords = new EnumMap<>(SpeciesCode.class);
        keywords.put(SpeciesCode.THRIPS, List.of("thrip", "silvering", "streak"));
        keywords.put(SpeciesCode.RED_SPIDER_MITE, List.of("red spider", "mite", "webbing"));
        keywords.put(SpeciesCode.WHITEFLIES, List.of("whitefly", "whiteflies", "honeydew"));
        keywords.put(SpeciesCode.MEALYBUGS, List.of("mealy", "cottony", "wax"));
        keywords.put(SpeciesCode.CATERPILLARS, List.of("caterpillar", "larva", "chewed"));
        keywords.put(SpeciesCode.FALSE_CODLING_MOTH, List.of("codling", "fcm", "fruit entry"));
        keywords.put(SpeciesCode.DOWNY_MILDEW, List.of("downy", "angular lesion"));
        keywords.put(SpeciesCode.POWDERY_MILDEW, List.of("powdery", "white powder"));
        keywords.put(SpeciesCode.BOTRYTIS, List.of("botrytis", "grey mold", "gray mold"));
        keywords.put(SpeciesCode.BACTERIAL_WILT, List.of("wilt", "vascular"));
        return keywords;
    }

    private SpeciesCode fallbackSpecies(String metadata) {
        if (metadata.contains("disease")
                || metadata.contains("mildew")
                || metadata.contains("wilt")
                || metadata.contains("mold")) {
            return SpeciesCode.DISEASE_OTHER;
        }
        return SpeciesCode.PEST_OTHER;
    }

    private void upsertCandidate(
            Map<SpeciesCode, CandidateAccumulator> candidates,
            SpeciesCode speciesCode,
            double scoreIncrement,
            String rationale
    ) {
        CandidateAccumulator accumulator = candidates.computeIfAbsent(speciesCode, CandidateAccumulator::new);
        accumulator.add(scoreIncrement, rationale);
    }

    private String buildSummary(ScoutingPhoto photo, ResolvedCandidate topCandidate) {
        String sourceLabel = resolveSourceType(photo) == PhotoSourceType.DRONE ? "drone image" : "photo";
        return "Most likely " + topCandidate.speciesCode().getDisplayName().toLowerCase(Locale.ROOT)
                + " based on " + sourceLabel + " metadata and recent session observations.";
    }

    private String normalizeText(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    private PhotoAnalysisResponse toResponse(ScoutingPhotoAnalysis analysis) {
        SpeciesCode predictedSpeciesCode = analysis.getPredictedSpeciesCode();
        SpeciesCode reviewedSpeciesCode = analysis.getReviewedSpeciesCode();
        List<PhotoAnalysisCandidate> candidates = analysis.getCandidates().stream()
                .map(candidate -> new PhotoAnalysisCandidate(
                        candidate.getSpeciesCode().name(),
                        candidate.getSpeciesCode().getDisplayName(),
                        candidate.getSpeciesCode().getCategory().name(),
                        candidate.getConfidenceScore().doubleValue(),
                        candidate.getRationale()
                ))
                .toList();

        AiAnalysisSnapshot aiAnalysis = new AiAnalysisSnapshot(
                analysis.getProvider(),
                analysis.getModelVersion(),
                analysis.getSummary(),
                predictedSpeciesCode != null ? predictedSpeciesCode.name() : null,
                predictedSpeciesCode != null ? predictedSpeciesCode.getDisplayName() : null,
                predictedSpeciesCode != null ? predictedSpeciesCode.getCategory().name() : null,
                analysis.getPredictedConfidence() != null ? analysis.getPredictedConfidence().doubleValue() : null,
                candidates
        );

        ManualAnalysisSnapshot manualAnalysis = new ManualAnalysisSnapshot(
                analysis.getReviewStatus().name(),
                reviewedSpeciesCode != null ? reviewedSpeciesCode.name() : null,
                reviewedSpeciesCode != null ? reviewedSpeciesCode.getDisplayName() : null,
                reviewedSpeciesCode != null ? reviewedSpeciesCode.getCategory().name() : null,
                analysis.getReviewNotes(),
                analysis.getReviewedAt(),
                analysis.getReviewerName()
        );

        AnalysisComparison comparison = buildComparison(predictedSpeciesCode, reviewedSpeciesCode);

        return new PhotoAnalysisResponse(
                analysis.getFarmId(),
                analysis.getPhoto().getId(),
                resolveSourceType(analysis.getPhoto()).name(),
                analysis.getProvider(),
                analysis.getModelVersion(),
                analysis.getSummary(),
                analysis.isReviewRequired(),
                analysis.getReviewStatus().name(),
                predictedSpeciesCode != null ? predictedSpeciesCode.name() : null,
                predictedSpeciesCode != null ? predictedSpeciesCode.getDisplayName() : null,
                predictedSpeciesCode != null ? predictedSpeciesCode.getCategory().name() : null,
                analysis.getPredictedConfidence() != null ? analysis.getPredictedConfidence().doubleValue() : null,
                reviewedSpeciesCode != null ? reviewedSpeciesCode.name() : null,
                reviewedSpeciesCode != null ? reviewedSpeciesCode.getDisplayName() : null,
                reviewedSpeciesCode != null ? reviewedSpeciesCode.getCategory().name() : null,
                analysis.getReviewNotes(),
                analysis.getUpdatedAt(),
                analysis.getReviewedAt(),
                analysis.getReviewerName(),
                candidates,
                aiAnalysis,
                manualAnalysis,
                comparison
        );
    }

    private AnalysisComparison buildComparison(SpeciesCode predictedSpeciesCode, SpeciesCode reviewedSpeciesCode) {
        if (reviewedSpeciesCode == null) {
            return new AnalysisComparison("PENDING_MANUAL_REVIEW", false, false);
        }
        if (predictedSpeciesCode == reviewedSpeciesCode) {
            return new AnalysisComparison("EXACT_MATCH", true, true);
        }
        if (predictedSpeciesCode != null && predictedSpeciesCode.getCategory() == reviewedSpeciesCode.getCategory()) {
            return new AnalysisComparison("CATEGORY_MATCH", false, true);
        }
        return new AnalysisComparison("MISMATCH", false, false);
    }

    private String resolveReviewerName(User reviewer) {
        String fullName = ((reviewer.getFirstName() != null ? reviewer.getFirstName().trim() : "")
                + " "
                + (reviewer.getLastName() != null ? reviewer.getLastName().trim() : "")).trim();
        return fullName.isBlank() ? reviewer.getEmail() : fullName;
    }

    private PhotoSourceType resolveSourceType(ScoutingPhoto photo) {
        return photo.getSourceType() != null ? photo.getSourceType() : PhotoSourceType.SCOUT_HANDHELD;
    }

    private boolean isExactMatch(ScoutingPhotoAnalysis analysis) {
        return analysis.getPredictedSpeciesCode() != null
                && analysis.getReviewedSpeciesCode() != null
                && analysis.getPredictedSpeciesCode() == analysis.getReviewedSpeciesCode();
    }

    private BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private double round(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record ResolvedCandidate(SpeciesCode speciesCode, double confidence, String rationale) {
    }

    private static final class CandidateAccumulator {

        private final SpeciesCode speciesCode;
        private final Set<String> rationales = new LinkedHashSet<>();
        private double score;

        private CandidateAccumulator(SpeciesCode speciesCode) {
            this.speciesCode = speciesCode;
        }

        private void add(double scoreIncrement, String rationale) {
            score += scoreIncrement;
            rationales.add(rationale);
        }

        private ResolvedCandidate toResolvedCandidate() {
            return new ResolvedCandidate(
                    speciesCode,
                    Math.max(0.20d, Math.min(0.98d, Math.round(score * 100.0d) / 100.0d)),
                    String.join(" ", rationales)
            );
        }
    }
}

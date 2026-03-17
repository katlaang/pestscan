package mofo.com.pestscout.scouting.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class ImageAnalysisDtos {

    private ImageAnalysisDtos() {
    }

    public record RunPhotoAnalysisRequest(
            @NotNull UUID farmId
    ) {
    }

    public record PhotoAnalysisReviewRequest(
            @NotNull UUID farmId,
            @NotNull mofo.com.pestscout.scouting.model.SpeciesCode speciesCode,
            String reviewNotes
    ) {
    }

    public record PhotoAnalysisResponse(
            UUID farmId,
            UUID photoId,
            String photoSourceType,
            String provider,
            String modelVersion,
            String summary,
            boolean reviewRequired,
            String reviewStatus,
            String predictedSpeciesCode,
            String predictedDisplayName,
            String predictedCategory,
            Double predictedConfidence,
            String reviewedSpeciesCode,
            String reviewedDisplayName,
            String reviewedCategory,
            String reviewNotes,
            LocalDateTime analyzedAt,
            LocalDateTime reviewedAt,
            String reviewerName,
            List<PhotoAnalysisCandidate> candidates,
            AiAnalysisSnapshot aiAnalysis,
            ManualAnalysisSnapshot manualAnalysis,
            AnalysisComparison comparison
    ) {
    }

    public record PhotoAnalysisCandidate(
            String speciesCode,
            String displayName,
            String category,
            double confidence,
            String rationale
    ) {
    }

    public record AiAnalysisSnapshot(
            String provider,
            String modelVersion,
            String summary,
            String speciesCode,
            String displayName,
            String category,
            Double confidence,
            List<PhotoAnalysisCandidate> candidates
    ) {
    }

    public record ManualAnalysisSnapshot(
            String reviewStatus,
            String speciesCode,
            String displayName,
            String category,
            String reviewNotes,
            LocalDateTime reviewedAt,
            String reviewerName
    ) {
    }

    public record AnalysisComparison(
            String status,
            boolean exactMatch,
            boolean sameCategory
    ) {
    }

    public record PhotoAnalysisAccuracyResponse(
            UUID farmId,
            String provider,
            String modelVersion,
            long totalAnalyses,
            long pendingReviewCount,
            long reviewedCount,
            long exactMatchCount,
            long correctedCount,
            double accuracyRate,
            double averagePredictedConfidence,
            List<PhotoAnalysisAccuracyBySpecies> speciesBreakdown
    ) {
    }

    public record PhotoAnalysisAccuracyBySpecies(
            String speciesCode,
            String displayName,
            long reviewedCount,
            long exactMatchCount,
            double accuracyRate
    ) {
    }
}

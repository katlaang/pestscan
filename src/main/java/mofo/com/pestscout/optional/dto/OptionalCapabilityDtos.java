package mofo.com.pestscout.optional.dto;

import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.analytics.dto.SeverityLegendEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OptionalCapabilityDtos {

    private OptionalCapabilityDtos() {
    }

    public record AiPestIdentificationResponse(
            UUID farmId,
            UUID photoId,
            String provider,
            String summary,
            boolean reviewRequired,
            List<AiPestCandidate> candidates
    ) {
    }

    public record AiPestCandidate(
            String speciesCode,
            String displayName,
            String category,
            double confidence,
            String rationale
    ) {
    }

    public record DroneImageProcessingRequest(
            @NotNull UUID farmId,
            @NotNull UUID sessionId,
            List<UUID> photoIds
    ) {
    }

    public record DroneImageProcessingResponse(
            UUID farmId,
            UUID sessionId,
            int week,
            int year,
            int processedPhotoCount,
            String analysisMode,
            String coverageSummary,
            List<DroneHotspot> hotspots,
            List<String> notes
    ) {
    }

    public record DroneHotspot(
            String layerName,
            int bayIndex,
            int benchIndex,
            int totalCount,
            String severityLevel,
            String colorHex
    ) {
    }

    public record PredictiveModelResponse(
            UUID farmId,
            LocalDate generatedOn,
            int forecastHorizonWeeks,
            List<PredictiveForecast> forecasts
    ) {
    }

    public record PredictiveForecast(
            String speciesCode,
            String displayName,
            String direction,
            int lastObservedCount,
            int projectedCount,
            String riskLevel,
            double confidence,
            String rationale
    ) {
    }

    public record GisHeatmapResponse(
            UUID farmId,
            int week,
            int year,
            String coordinateMode,
            boolean geoReferenced,
            String layerMode,
            List<GisLayer> layers,
            List<SeverityLegendEntry> legend
    ) {
    }

    public record GisLayer(
            String layerId,
            String layerName,
            String layerType,
            List<GisFeature> features
    ) {
    }

    public record GisFeature(
            String featureId,
            String sectionName,
            int bayIndex,
            int benchIndex,
            int totalCount,
            String severityLevel,
            String colorHex,
            List<List<Double>> polygon
    ) {
    }

    public record TreatmentRecommendationResponse(
            UUID farmId,
            LocalDate generatedOn,
            List<TreatmentRecommendationItem> recommendations
    ) {
    }

    public record TreatmentRecommendationItem(
            String speciesCode,
            String displayName,
            String category,
            String sectionName,
            int observedCount,
            String severityLevel,
            String priority,
            String treatmentType,
            String action,
            String rationale,
            String skuHint,
            String supplyItemName,
            BigDecimal suggestedOrderQuantity,
            String unitOfMeasure,
            BigDecimal estimatedUnitPrice
    ) {
    }
}

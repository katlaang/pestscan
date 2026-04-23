package mofo.com.pestscout.optional.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.*;
import mofo.com.pestscout.analytics.service.HeatmapService;
import mofo.com.pestscout.analytics.service.TrendAnalysisService;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.feature.FeatureAccessService;
import mofo.com.pestscout.common.feature.FeatureKey;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.optional.dto.OptionalCapabilityDtos.*;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingPhotoRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import mofo.com.pestscout.scouting.service.ScoutingImageAnalysisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.ToIntFunction;

@Service
@RequiredArgsConstructor
public class OptionalCapabilityService {

    private static final List<String> DRONE_KEYWORDS = List.of("drone", "aerial", "uav", "ortho", "canopy");

    private final OptionalCapabilityAccessService accessService;
    private final FeatureAccessService featureAccessService;
    private final ScoutingPhotoRepository photoRepository;
    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;
    private final HeatmapService heatmapService;
    private final TrendAnalysisService trendAnalysisService;
    private final TreatmentRecommendationEngine treatmentRecommendationEngine;
    private final ScoutingImageAnalysisService imageAnalysisService;

    @Transactional(readOnly = true)
    public AiPestIdentificationResponse identifyFromPhoto(UUID farmId, UUID photoId) {
        var response = imageAnalysisService.analyzePhoto(farmId, photoId);
        return new AiPestIdentificationResponse(
                response.farmId(),
                response.photoId(),
                response.provider(),
                response.summary(),
                response.reviewRequired(),
                response.candidates().stream()
                        .map(candidate -> new AiPestCandidate(
                                candidate.speciesCode(),
                                candidate.displayName(),
                                candidate.category(),
                                candidate.confidence(),
                                candidate.rationale()
                        ))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public DroneImageProcessingResponse processDroneImagery(DroneImageProcessingRequest request) {
        accessService.loadFarmAndEnsureManager(request.farmId());
        featureAccessService.assertEnabled(FeatureKey.DRONE_IMAGE_PROCESSING, request.farmId());

        ScoutingSession session = sessionRepository.findByIdAndFarmId(request.sessionId(), request.farmId())
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingSession", "id", request.sessionId()));

        List<ScoutingPhoto> sessionPhotos = photoRepository.findBySessionId(session.getId());
        List<ScoutingPhoto> selectedPhotos = resolveSelectedPhotos(sessionPhotos, request.photoIds());
        List<String> notes = new ArrayList<>();

        String analysisMode;
        if (request.photoIds() != null && !request.photoIds().isEmpty()) {
            analysisMode = "EXPLICIT_SELECTION";
        } else if (selectedPhotos.stream().anyMatch(this::isDroneLike)) {
            analysisMode = "AUTO_DRONE_KEYWORD";
            notes.add("Automatically selected session images tagged as drone or aerial captures.");
        } else {
            analysisMode = "SESSION_GRID_PROXY";
            notes.add("No drone-tagged imagery found; hotspot output is based on weekly observation density.");
        }

        LocalDate sessionDate = session.getSessionDate();
        WeekFields iso = WeekFields.ISO;
        int week = sessionDate.get(iso.weekOfWeekBasedYear());
        int year = sessionDate.get(iso.weekBasedYear());
        HeatmapResponse heatmap = heatmapService.generateHeatmap(request.farmId(), week, year);
        Set<UUID> targetIds = session.getTargets().stream()
                .map(ScoutingSessionTarget::getId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        List<DroneHotspot> hotspots = heatmap.sections().stream()
                .filter(section -> targetIds.isEmpty() || targetIds.contains(section.targetId()))
                .flatMap(section -> section.cells().stream().map(cell -> toDroneHotspot(section.targetName(), cell)))
                .filter(hotspot -> hotspot.totalCount() > 0)
                .sorted(Comparator.comparing(DroneHotspot::totalCount).reversed())
                .limit(10)
                .toList();

        if (hotspots.isEmpty()) {
            hotspots = heatmap.cells().stream()
                    .map(cell -> toDroneHotspot(heatmap.farmName(), cell))
                    .filter(hotspot -> hotspot.totalCount() > 0)
                    .sorted(Comparator.comparing(DroneHotspot::totalCount).reversed())
                    .limit(10)
                    .toList();
        }

        String coverageSummary = hotspots.isEmpty()
                ? "No actionable hotspots were found for the selected imagery window."
                : "Processed " + selectedPhotos.size()
                + " image(s) and correlated them with "
                + hotspots.size()
                + " hotspot cell(s) from the weekly heatmap.";

        return new DroneImageProcessingResponse(
                request.farmId(),
                request.sessionId(),
                week,
                year,
                selectedPhotos.size(),
                analysisMode,
                coverageSummary,
                hotspots,
                notes
        );
    }

    @Transactional(readOnly = true)
    public PredictiveModelResponse getPredictiveForecast(UUID farmId) {
        accessService.loadFarmAndEnsureManager(farmId);
        featureAccessService.assertEnabled(FeatureKey.PREDICTIVE_MODELING, farmId);

        List<WeeklyPestTrendDto> trends = trendAnalysisService.getWeeklyPestTrends(farmId);
        List<PredictiveForecast> forecasts = new ArrayList<>();

        List<TrendSeriesDefinition> seriesDefinitions = List.of(
                new TrendSeriesDefinition(SpeciesCode.THRIPS, WeeklyPestTrendDto::thrips),
                new TrendSeriesDefinition(SpeciesCode.RED_SPIDER_MITE, WeeklyPestTrendDto::redSpider),
                new TrendSeriesDefinition(SpeciesCode.WHITEFLIES, WeeklyPestTrendDto::whiteflies),
                new TrendSeriesDefinition(SpeciesCode.MEALYBUGS, WeeklyPestTrendDto::mealybugs),
                new TrendSeriesDefinition(SpeciesCode.CATERPILLARS, WeeklyPestTrendDto::caterpillars),
                new TrendSeriesDefinition(SpeciesCode.FALSE_CODLING_MOTH, WeeklyPestTrendDto::fcm),
                new TrendSeriesDefinition(SpeciesCode.PEST_OTHER, WeeklyPestTrendDto::otherPests)
        );

        for (TrendSeriesDefinition definition : seriesDefinitions) {
            List<Integer> counts = trends.stream()
                    .map(definition.extractor()::applyAsInt)
                    .toList();

            if (counts.stream().mapToInt(Integer::intValue).sum() == 0) {
                continue;
            }

            int last = counts.get(counts.size() - 1);
            int previous = counts.size() >= 2 ? counts.get(counts.size() - 2) : last;
            int third = counts.size() >= 3 ? counts.get(counts.size() - 3) : previous;
            int slope = last - previous;

            double weightedAverage = (last * 0.6d) + (previous * 0.3d) + (third * 0.1d);
            int projectedCount = Math.max(0, (int) Math.round(weightedAverage + (slope * 0.5d)));
            String direction = slope > 2 ? "INCREASING" : slope < -2 ? "DECREASING" : "STABLE";
            String riskLevel = toRiskLevel(projectedCount);

            long nonZeroWeeks = counts.stream().filter(count -> count > 0).count();
            double confidence = clamp(0.48d + (nonZeroWeeks / 7.0d) * 0.32d + (Math.abs(slope) <= 5 ? 0.08d : 0.0d));

            forecasts.add(new PredictiveForecast(
                    definition.speciesCode().name(),
                    definition.speciesCode().getDisplayName(),
                    direction,
                    last,
                    projectedCount,
                    riskLevel,
                    confidence,
                    "Weighted forecast from recent weekly counts " + counts + "."
            ));
        }

        forecasts.sort(Comparator
                .comparing(PredictiveForecast::projectedCount, Comparator.reverseOrder())
                .thenComparing(PredictiveForecast::lastObservedCount, Comparator.reverseOrder()));

        return new PredictiveModelResponse(
                farmId,
                LocalDate.now(),
                2,
                forecasts
        );
    }

    @Transactional(readOnly = true)
    public GisHeatmapResponse getGisHeatmapLayers(UUID farmId, Integer week, Integer year, HeatmapLayerMode layerMode) {
        Farm farm = accessService.loadFarmAndEnsureManager(farmId);
        featureAccessService.assertEnabled(FeatureKey.GIS_HEATMAPS, farmId);

        LocalDate now = LocalDate.now();
        WeekFields iso = WeekFields.ISO;
        int resolvedWeek = week != null ? week : now.get(iso.weekOfWeekBasedYear());
        int resolvedYear = year != null ? year : now.get(iso.weekBasedYear());

        HeatmapResponse heatmap = heatmapService.generateHeatmap(farmId, resolvedWeek, resolvedYear, layerMode);
        boolean geoReferenced = farm.getLatitude() != null && farm.getLongitude() != null;
        String coordinateMode = geoReferenced ? "FARM_ANCHORED_GRID" : "LOCAL_GRID";

        double originLat = farm.getLatitude() != null ? farm.getLatitude().doubleValue() : 0.0d;
        double originLng = farm.getLongitude() != null ? farm.getLongitude().doubleValue() : 0.0d;

        List<GisLayer> layers = new ArrayList<>();
        layers.add(new GisLayer(
                "farm-overview",
                heatmap.farmName(),
                "FARM_OVERVIEW",
                heatmap.cells().stream()
                        .map(cell -> toGisFeature("farm", heatmap.farmName(), cell, geoReferenced, originLat, originLng))
                        .toList()
        ));

        int[] sectionIndex = {0};
        for (HeatmapSectionResponse section : heatmap.sections()) {
            double sectionLat = geoReferenced ? originLat - (sectionIndex[0] * 0.0012d) : 0.0d;
            double sectionLng = geoReferenced ? originLng + (sectionIndex[0] * 0.0012d) : 0.0d;
            layers.add(new GisLayer(
                    "section-" + section.targetId(),
                    section.targetName(),
                    section.greenhouseId() != null ? "GREENHOUSE" : "FIELD_BLOCK",
                    section.cells().stream()
                            .map(cell -> toGisFeature(
                                    String.valueOf(section.targetId()),
                                    section.targetName(),
                                    cell,
                                    geoReferenced,
                                    sectionLat,
                                    sectionLng
                            ))
                            .toList()
            ));
            sectionIndex[0]++;
        }

        return new GisHeatmapResponse(
                farmId,
                resolvedWeek,
                resolvedYear,
                coordinateMode,
                geoReferenced,
                layerMode.apiValue(),
                layers,
                heatmap.severityLegend()
        );
    }

    @Transactional(readOnly = true)
    public TreatmentRecommendationResponse getTreatmentRecommendations(UUID farmId) {
        accessService.loadFarmAndEnsureManager(farmId);
        featureAccessService.assertEnabled(FeatureKey.AUTOMATED_TREATMENT_RECOMMENDATIONS, farmId);

        return new TreatmentRecommendationResponse(
                farmId,
                LocalDate.now(),
                treatmentRecommendationEngine.generateForFarm(farmId)
        );
    }

    private List<ScoutingPhoto> resolveSelectedPhotos(List<ScoutingPhoto> sessionPhotos, Collection<UUID> explicitPhotoIds) {
        if (explicitPhotoIds != null && !explicitPhotoIds.isEmpty()) {
            Set<UUID> selectedIds = Set.copyOf(explicitPhotoIds);
            return sessionPhotos.stream()
                    .filter(photo -> selectedIds.contains(photo.getId()))
                    .toList();
        }

        List<ScoutingPhoto> droneTagged = sessionPhotos.stream()
                .filter(this::isDroneLike)
                .toList();
        return droneTagged.isEmpty() ? sessionPhotos : droneTagged;
    }

    private boolean isDroneLike(ScoutingPhoto photo) {
        if (photo.getSourceType() == PhotoSourceType.DRONE) {
            return true;
        }
        String text = normalizeText(photo.getPurpose(), photo.getObjectKey(), photo.getLocalPhotoId());
        return DRONE_KEYWORDS.stream().anyMatch(text::contains);
    }

    private DroneHotspot toDroneHotspot(String layerName, HeatmapCellResponse cell) {
        return new DroneHotspot(
                layerName,
                cell.bayIndex(),
                cell.benchIndex(),
                cell.totalCount(),
                cell.severityLevel().name(),
                cell.colorHex()
        );
    }

    private GisFeature toGisFeature(
            String featurePrefix,
            String sectionName,
            HeatmapCellResponse cell,
            boolean geoReferenced,
            double originLat,
            double originLng
    ) {
        return new GisFeature(
                featurePrefix + "-" + cell.bayIndex() + "-" + cell.benchIndex(),
                sectionName,
                cell.bayIndex(),
                cell.benchIndex(),
                cell.totalCount(),
                cell.severityLevel().name(),
                cell.colorHex(),
                buildPolygon(cell.bayIndex(), cell.benchIndex(), geoReferenced, originLat, originLng)
        );
    }

    private List<List<Double>> buildPolygon(
            int bayIndex,
            int benchIndex,
            boolean geoReferenced,
            double originLat,
            double originLng
    ) {
        double x = Math.max(0, benchIndex - 1);
        double y = Math.max(0, bayIndex - 1);

        if (!geoReferenced) {
            return List.of(
                    List.of(x, y),
                    List.of(x + 1.0d, y),
                    List.of(x + 1.0d, y + 1.0d),
                    List.of(x, y + 1.0d),
                    List.of(x, y)
            );
        }

        double cellHeight = 0.00018d;
        double cellWidth = 0.00018d;
        double north = originLat - (y * cellHeight);
        double south = north - cellHeight;
        double west = originLng + (x * cellWidth);
        double east = west + cellWidth;

        return List.of(
                List.of(west, north),
                List.of(east, north),
                List.of(east, south),
                List.of(west, south),
                List.of(west, north)
        );
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

    private String toRiskLevel(int projectedCount) {
        SeverityLevel severityLevel = SeverityLevel.fromCount(projectedCount);
        return switch (severityLevel) {
            case ZERO, LOW -> "LOW";
            case MODERATE -> "MEDIUM";
            case HIGH -> "HIGH";
            case VERY_HIGH, EMERGENCY -> "CRITICAL";
        };
    }

    private double clamp(double value) {
        return Math.max(0.20d, Math.min(0.98d, Math.round(value * 100.0d) / 100.0d));
    }

    private record TrendSeriesDefinition(SpeciesCode speciesCode, ToIntFunction<WeeklyPestTrendDto> extractor) {
    }
}

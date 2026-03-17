package mofo.com.pestscout.optional.service;

import mofo.com.pestscout.analytics.service.HeatmapService;
import mofo.com.pestscout.analytics.service.TrendAnalysisService;
import mofo.com.pestscout.scouting.dto.ImageAnalysisDtos.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingPhotoRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import mofo.com.pestscout.scouting.service.ScoutingImageAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OptionalCapabilityServiceTest {

    @Mock
    private OptionalCapabilityAccessService accessService;

    @Mock
    private mofo.com.pestscout.common.feature.FeatureAccessService featureAccessService;

    @Mock
    private ScoutingPhotoRepository photoRepository;

    @Mock
    private ScoutingSessionRepository sessionRepository;

    @Mock
    private ScoutingObservationRepository observationRepository;

    @Mock
    private HeatmapService heatmapService;

    @Mock
    private TrendAnalysisService trendAnalysisService;

    @Mock
    private TreatmentRecommendationEngine treatmentRecommendationEngine;

    @Mock
    private ScoutingImageAnalysisService imageAnalysisService;

    @InjectMocks
    private OptionalCapabilityService optionalCapabilityService;

    @Test
    void identifyFromPhoto_delegatesToCoreAnalysisService() {
        UUID farmId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        when(imageAnalysisService.analyzePhoto(farmId, photoId)).thenReturn(new PhotoAnalysisResponse(
                farmId,
                photoId,
                "SCOUT_HANDHELD",
                "heuristic-local-v1",
                "heuristic-local-v1",
                "Most likely thrips based on photo metadata and recent session observations.",
                false,
                "PENDING_REVIEW",
                "THRIPS",
                "Thrips",
                "PEST",
                0.91d,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                null,
                null,
                List.of(new PhotoAnalysisCandidate("THRIPS", "Thrips", "PEST", 0.91d, "Linked observation")),
                new AiAnalysisSnapshot(
                        "heuristic-local-v1",
                        "heuristic-local-v1",
                        "Most likely thrips based on photo metadata and recent session observations.",
                        "THRIPS",
                        "Thrips",
                        "PEST",
                        0.91d,
                        List.of(new PhotoAnalysisCandidate("THRIPS", "Thrips", "PEST", 0.91d, "Linked observation"))
                ),
                new ManualAnalysisSnapshot(
                        "PENDING_REVIEW",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new AnalysisComparison("PENDING_MANUAL_REVIEW", false, false)
        ));

        var response = optionalCapabilityService.identifyFromPhoto(farmId, photoId);

        assertThat(response.provider()).isEqualTo("heuristic-local-v1");
        assertThat(response.reviewRequired()).isFalse();
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().speciesCode()).isEqualTo("THRIPS");
    }
}

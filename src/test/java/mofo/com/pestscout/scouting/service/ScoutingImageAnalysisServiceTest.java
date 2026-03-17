package mofo.com.pestscout.scouting.service;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.scouting.dto.ImageAnalysisDtos.PhotoAnalysisAccuracyResponse;
import mofo.com.pestscout.scouting.dto.ImageAnalysisDtos.PhotoAnalysisResponse;
import mofo.com.pestscout.scouting.dto.ImageAnalysisDtos.PhotoAnalysisReviewRequest;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingPhotoAnalysisRepository;
import mofo.com.pestscout.scouting.repository.ScoutingPhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoutingImageAnalysisServiceTest {

    @Mock
    private ScoutingAnalysisAccessService accessService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ScoutingPhotoRepository photoRepository;

    @Mock
    private ScoutingObservationRepository observationRepository;

    @Mock
    private ScoutingPhotoAnalysisRepository analysisRepository;

    @InjectMocks
    private ScoutingImageAnalysisService imageAnalysisService;

    private UUID farmId;
    private UUID photoId;
    private UUID sessionId;
    private Farm farm;
    private User manager;
    private ScoutingSession session;
    private ScoutingObservation linkedObservation;
    private ScoutingPhoto photo;

    @BeforeEach
    void setUp() {
        farmId = UUID.randomUUID();
        photoId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        manager = User.builder()
                .id(UUID.randomUUID())
                .firstName("Mia")
                .lastName("Manager")
                .email("manager@example.com")
                .role(Role.MANAGER)
                .isEnabled(true)
                .build();

        farm = Farm.builder()
                .id(farmId)
                .name("North Farm")
                .owner(manager)
                .build();

        session = ScoutingSession.builder()
                .farm(farm)
                .sessionDate(LocalDate.now())
                .build();
        session.setId(sessionId);

        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .session(session)
                .build();

        linkedObservation = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(9)
                .build();

        photo = ScoutingPhoto.builder()
                .session(session)
                .observation(linkedObservation)
                .farmId(farmId)
                .localPhotoId("thrips-leaf-01")
                .purpose("Close-up thrip damage")
                .sourceType(PhotoSourceType.SCOUT_HANDHELD)
                .capturedAt(LocalDateTime.now())
                .build();
        photo.setId(photoId);

    }

    @Test
    void analyzePhoto_prefersLinkedObservationAndSessionContext() {
        ScoutingObservation whitefliesObservation = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(ScoutingSessionTarget.builder().session(session).build())
                .speciesCode(SpeciesCode.WHITEFLIES)
                .bayIndex(2)
                .benchIndex(1)
                .spotIndex(1)
                .count(2)
                .build();

        when(accessService.loadFarmAndEnsureViewer(farmId)).thenReturn(farm);
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));
        when(observationRepository.findBySessionId(sessionId))
                .thenReturn(List.of(linkedObservation, whitefliesObservation));
        when(analysisRepository.findByPhoto_Id(photoId)).thenReturn(Optional.empty());
        when(analysisRepository.save(any(ScoutingPhotoAnalysis.class))).thenAnswer(invocation -> {
            ScoutingPhotoAnalysis analysis = invocation.getArgument(0);
            if (analysis.getId() == null) {
                analysis.setId(UUID.randomUUID());
            }
            if (analysis.getCreatedAt() == null) {
                analysis.setCreatedAt(LocalDateTime.now());
            }
            analysis.setUpdatedAt(LocalDateTime.now());
            return analysis;
        });

        PhotoAnalysisResponse response = imageAnalysisService.analyzePhoto(farmId, photoId);

        assertThat(response.provider()).isEqualTo("heuristic-local-v1");
        assertThat(response.predictedSpeciesCode()).isEqualTo("THRIPS");
        assertThat(response.reviewRequired()).isFalse();
        assertThat(response.reviewStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(response.candidates()).isNotEmpty();
        assertThat(response.candidates().getFirst().speciesCode()).isEqualTo("THRIPS");
        assertThat(response.candidates().getFirst().confidence()).isGreaterThanOrEqualTo(0.90d);
    }

    @Test
    void reviewPhotoAnalysis_marksCorrectedAndStoresReviewer() {
        ScoutingPhotoAnalysis existingAnalysis = ScoutingPhotoAnalysis.builder()
                .photo(photo)
                .farmId(farmId)
                .provider("heuristic-local-v1")
                .modelVersion("heuristic-local-v1")
                .summary("Initial summary")
                .reviewRequired(true)
                .predictedSpeciesCode(SpeciesCode.THRIPS)
                .predictedConfidence(new BigDecimal("0.91"))
                .reviewStatus(PhotoAnalysisReviewStatus.PENDING_REVIEW)
                .build();
        existingAnalysis.setId(UUID.randomUUID());
        existingAnalysis.setUpdatedAt(LocalDateTime.now());

        when(accessService.loadFarmAndEnsureManager(farmId)).thenReturn(farm);
        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));
        when(analysisRepository.findByPhoto_Id(photoId)).thenReturn(Optional.of(existingAnalysis));
        when(currentUserService.getCurrentUser()).thenReturn(manager);
        when(analysisRepository.save(any(ScoutingPhotoAnalysis.class))).thenAnswer(invocation -> {
            ScoutingPhotoAnalysis analysis = invocation.getArgument(0);
            analysis.setUpdatedAt(LocalDateTime.now());
            return analysis;
        });

        PhotoAnalysisResponse response = imageAnalysisService.reviewPhotoAnalysis(
                photoId,
                new PhotoAnalysisReviewRequest(farmId, SpeciesCode.WHITEFLIES, "Corrected after manual review")
        );

        assertThat(response.reviewRequired()).isFalse();
        assertThat(response.reviewStatus()).isEqualTo("CORRECTED");
        assertThat(response.reviewedSpeciesCode()).isEqualTo("WHITEFLIES");
        assertThat(response.reviewNotes()).isEqualTo("Corrected after manual review");
        assertThat(response.reviewerName()).isEqualTo("Mia Manager");
    }

    @Test
    void getAccuracy_aggregatesReviewedPredictions() {
        ScoutingPhotoAnalysis pending = ScoutingPhotoAnalysis.builder()
                .photo(photo)
                .farmId(farmId)
                .provider("heuristic-local-v1")
                .modelVersion("heuristic-local-v1")
                .reviewStatus(PhotoAnalysisReviewStatus.PENDING_REVIEW)
                .predictedSpeciesCode(SpeciesCode.THRIPS)
                .predictedConfidence(new BigDecimal("0.91"))
                .build();

        ScoutingPhotoAnalysis confirmed = ScoutingPhotoAnalysis.builder()
                .photo(photo)
                .farmId(farmId)
                .provider("heuristic-local-v1")
                .modelVersion("heuristic-local-v1")
                .reviewStatus(PhotoAnalysisReviewStatus.CONFIRMED)
                .predictedSpeciesCode(SpeciesCode.THRIPS)
                .reviewedSpeciesCode(SpeciesCode.THRIPS)
                .predictedConfidence(new BigDecimal("0.88"))
                .build();

        ScoutingPhotoAnalysis corrected = ScoutingPhotoAnalysis.builder()
                .photo(photo)
                .farmId(farmId)
                .provider("heuristic-local-v1")
                .modelVersion("heuristic-local-v1")
                .reviewStatus(PhotoAnalysisReviewStatus.CORRECTED)
                .predictedSpeciesCode(SpeciesCode.THRIPS)
                .reviewedSpeciesCode(SpeciesCode.WHITEFLIES)
                .predictedConfidence(new BigDecimal("0.72"))
                .build();

        when(accessService.loadFarmAndEnsureManager(farmId)).thenReturn(farm);
        when(analysisRepository.findByFarmId(farmId)).thenReturn(List.of(pending, confirmed, corrected));
        when(analysisRepository.findByFarmIdAndReviewStatusIn(
                farmId,
                List.of(PhotoAnalysisReviewStatus.CONFIRMED, PhotoAnalysisReviewStatus.CORRECTED)
        )).thenReturn(List.of(confirmed, corrected));

        PhotoAnalysisAccuracyResponse response = imageAnalysisService.getAccuracy(farmId);

        assertThat(response.totalAnalyses()).isEqualTo(3);
        assertThat(response.pendingReviewCount()).isEqualTo(1);
        assertThat(response.reviewedCount()).isEqualTo(2);
        assertThat(response.exactMatchCount()).isEqualTo(1);
        assertThat(response.correctedCount()).isEqualTo(1);
        assertThat(response.accuracyRate()).isEqualTo(0.50d);
        assertThat(response.averagePredictedConfidence()).isEqualTo(0.80d);
        assertThat(response.speciesBreakdown()).hasSize(2);
    }
}

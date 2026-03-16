package mofo.com.pestscout.optional.service;

import mofo.com.pestscout.analytics.service.HeatmapService;
import mofo.com.pestscout.analytics.service.TrendAnalysisService;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.optional.dto.OptionalCapabilityDtos.AiPestIdentificationResponse;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingPhotoRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

    @InjectMocks
    private OptionalCapabilityService optionalCapabilityService;

    @Test
    void identifyFromPhoto_prefersLinkedObservationAndSessionContext() {
        UUID farmId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        Farm farm = Farm.builder().name("North Farm").build();
        ScoutingSession session = ScoutingSession.builder()
                .farm(farm)
                .sessionDate(LocalDate.now())
                .build();
        session.setId(UUID.randomUUID());

        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .session(session)
                .build();

        ScoutingObservation linkedObservation = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(9)
                .build();

        ScoutingObservation whitefliesObservation = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.WHITEFLIES)
                .bayIndex(2)
                .benchIndex(1)
                .spotIndex(1)
                .count(2)
                .build();

        ScoutingPhoto photo = ScoutingPhoto.builder()
                .session(session)
                .observation(linkedObservation)
                .farmId(farmId)
                .localPhotoId("thrips-leaf-01")
                .purpose("Close-up thrip damage")
                .build();
        photo.setId(photoId);

        when(photoRepository.findById(photoId)).thenReturn(Optional.of(photo));
        when(observationRepository.findBySessionId(session.getId()))
                .thenReturn(List.of(linkedObservation, whitefliesObservation));

        AiPestIdentificationResponse response = optionalCapabilityService.identifyFromPhoto(farmId, photoId);

        assertThat(response.provider()).isEqualTo("heuristic-local-v1");
        assertThat(response.reviewRequired()).isFalse();
        assertThat(response.summary()).contains("thrips");
        assertThat(response.candidates()).isNotEmpty();
        assertThat(response.candidates().getFirst().speciesCode()).isEqualTo("THRIPS");
        assertThat(response.candidates().getFirst().confidence()).isGreaterThanOrEqualTo(0.9d);
    }
}

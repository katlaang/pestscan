package mofo.com.pestscout.optional.service;

import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.Greenhouse;
import mofo.com.pestscout.optional.dto.OptionalCapabilityDtos.TreatmentRecommendationItem;
import mofo.com.pestscout.scouting.model.ScoutingObservation;
import mofo.com.pestscout.scouting.model.ScoutingSession;
import mofo.com.pestscout.scouting.model.ScoutingSessionTarget;
import mofo.com.pestscout.scouting.model.SpeciesCode;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreatmentRecommendationEngineTest {

    @Mock
    private ScoutingSessionRepository sessionRepository;

    @Mock
    private ScoutingObservationRepository observationRepository;

    @InjectMocks
    private TreatmentRecommendationEngine treatmentRecommendationEngine;

    @Test
    void generateForFarm_buildsRankedRecommendationsFromRecentObservations() {
        UUID farmId = UUID.randomUUID();
        ScoutingSession session = ScoutingSession.builder()
                .sessionDate(LocalDate.now())
                .build();
        session.setId(UUID.randomUUID());

        Farm farm = Farm.builder().name("North Farm").build();
        Greenhouse greenhouse = Greenhouse.builder().farm(farm).name("GH-1").build();
        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .session(session)
                .greenhouse(greenhouse)
                .build();
        target.setId(UUID.randomUUID());

        ScoutingObservation first = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(12)
                .notes("Hot aisle")
                .build();

        ScoutingObservation second = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(2)
                .spotIndex(1)
                .count(6)
                .build();

        ScoutingObservation beneficial = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.BENEFICIAL_PP)
                .bayIndex(2)
                .benchIndex(1)
                .spotIndex(1)
                .count(4)
                .build();

        when(sessionRepository.findByFarmIdAndSessionDateBetween(
                farmId,
                LocalDate.now().minusDays(21),
                LocalDate.now()
        )).thenReturn(List.of(session));
        when(observationRepository.findBySessionIdIn(List.of(session.getId())))
                .thenReturn(List.of(first, second, beneficial));

        List<TreatmentRecommendationItem> recommendations = treatmentRecommendationEngine.generateForFarm(farmId);

        assertThat(recommendations).hasSize(1);
        TreatmentRecommendationItem recommendation = recommendations.getFirst();
        assertThat(recommendation.speciesCode()).isEqualTo("THRIPS");
        assertThat(recommendation.sectionName()).isEqualTo("GH-1");
        assertThat(recommendation.priority()).isEqualTo("HIGH");
        assertThat(recommendation.skuHint()).isEqualTo("BIO-THRIPS-PRED");
        assertThat(recommendation.suggestedOrderQuantity()).isEqualByComparingTo("4.00");
        assertThat(recommendation.rationale()).contains("Hot aisle");
    }
}

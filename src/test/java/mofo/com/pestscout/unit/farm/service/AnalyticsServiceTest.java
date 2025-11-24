package mofo.com.pestscout.unit.farm.service;

import mofo.com.pestscout.analytics.dto.FarmWeeklyAnalyticsDto;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FarmStructureType;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.service.AnalyticsService;
import mofo.com.pestscout.scouting.model.ObservationCategory;
import mofo.com.pestscout.scouting.model.ScoutingObservation;
import mofo.com.pestscout.scouting.model.ScoutingSession;
import mofo.com.pestscout.scouting.model.SessionStatus;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private ScoutingSessionRepository sessionRepository;

    @Mock
    private ScoutingObservationRepository observationRepository;

    @Test
    void computeWeeklyAnalyticsReturnsEmptyResponseWhenNoSessions() {
        UUID farmId = UUID.randomUUID();
        Farm farm = Farm.builder()
                .id(farmId)
                .name("Empty Farm")
                .structureType(FarmStructureType.GREENHOUSE)
                .defaultBayCount(2)
                .defaultBenchesPerBay(3)
                .build();

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(sessionRepository.findByFarmIdAndSessionDateBetween(any(), any(), any()))
                .thenReturn(List.of());

        AnalyticsService service =
                new AnalyticsService(farmRepository, sessionRepository, observationRepository);

        FarmWeeklyAnalyticsDto dto = service.computeWeeklyAnalytics(farmId, 5, 2024);

        assertThat(dto.totalSessions()).isZero();
        assertThat(dto.totalObservations()).isZero();
        assertThat(dto.bayCount()).isEqualTo(2);
        assertThat(dto.benchesPerBay()).isEqualTo(3);
    }

    @Test
    void computeWeeklyAnalyticsAggregatesObservationCounts() {
        UUID farmId = UUID.randomUUID();
        Farm farm = Farm.builder()
                .id(farmId)
                .name("Demo Farm")
                .structureType(FarmStructureType.FIELD)   // was FIELD_BLOCK
                .defaultBayCount(1)
                .defaultBenchesPerBay(1)
                .build();

        ScoutingSession session = new ScoutingSession();
        session.setId(UUID.randomUUID());
        session.setStatus(SessionStatus.COMPLETED);

        ScoutingObservation pest = new ScoutingObservation() {
            @Override
            public ObservationCategory getCategory() {
                return ObservationCategory.PEST;
            }
        };
        pest.setCount(3);

        ScoutingObservation disease = new ScoutingObservation() {
            @Override
            public ObservationCategory getCategory() {
                return ObservationCategory.DISEASE;
            }
        };
        disease.setCount(2);

        ScoutingObservation beneficial = new ScoutingObservation() {
            @Override
            public ObservationCategory getCategory() {
                return ObservationCategory.BENEFICIAL;
            }
        };
        beneficial.setCount(1);

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(sessionRepository.findByFarmIdAndSessionDateBetween(any(), any(), any()))
                .thenReturn(List.of(session));
        when(observationRepository.findBySessionIdIn(any()))
                .thenReturn(List.of(pest, disease, beneficial));

        AnalyticsService service =
                new AnalyticsService(farmRepository, sessionRepository, observationRepository);

        FarmWeeklyAnalyticsDto dto = service.computeWeeklyAnalytics(farmId, 6, 2024);

        assertThat(dto.totalSessions()).isEqualTo(1);
        assertThat(dto.completedSessions()).isEqualTo(1);
        assertThat(dto.pestObservations()).isEqualTo(3);
        assertThat(dto.diseaseObservations()).isEqualTo(2);
        assertThat(dto.beneficialObservations()).isEqualTo(1);
        assertThat(dto.totalObservations()).isEqualTo(6);
    }

    @Test
    void computeWeeklyAnalyticsThrowsWhenFarmMissing() {
        UUID farmId = UUID.randomUUID();
        when(farmRepository.findById(farmId)).thenReturn(Optional.empty());

        AnalyticsService service =
                new AnalyticsService(farmRepository, sessionRepository, observationRepository);

        assertThatThrownBy(() -> service.computeWeeklyAnalytics(farmId, 1, 2024))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

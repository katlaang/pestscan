package mofo.com.pestscout.unit.analytics.service;

import mofo.com.pestscout.analytics.dto.HeatmapResponse;
import mofo.com.pestscout.analytics.service.HeatmapService;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.Greenhouse;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HeatmapService Unit Tests")
class HeatmapServiceTest {

    @Mock
    private ScoutingSessionRepository sessionRepository;

    @Mock
    private ScoutingObservationRepository observationRepository;

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private ScoutingSessionTargetRepository targetRepository;

    @Mock
    private FarmAccessService farmAccessService;

    @InjectMocks
    private HeatmapService heatmapService;

    private Farm testFarm;
    private Greenhouse greenhouse;
    private ScoutingSession session;
    private ScoutingSessionTarget target;
    private LocalDate weekStart;
    private LocalDate weekEnd;

    @BeforeEach
    void setUp() {
        testFarm = Farm.builder()
                .id(UUID.randomUUID())
                .name("Test Farm")
                .defaultBayCount(5)
                .defaultBenchesPerBay(10)
                .defaultSpotChecksPerBench(3)
                .build();

        greenhouse = Greenhouse.builder()
                .id(UUID.randomUUID())
                .farm(testFarm)
                .name("Greenhouse 1")
                .bayCount(5)
                .benchesPerBay(10)
                .spotChecksPerBench(3)
                .build();

        session = ScoutingSession.builder()
                .id(UUID.randomUUID())
                .farm(testFarm)
                .sessionDate(LocalDate.now())
                .status(SessionStatus.COMPLETED)
                .build();

        target = ScoutingSessionTarget.builder()
                .id(UUID.randomUUID())
                .session(session)
                .greenhouse(greenhouse)
                .includeAllBays(true)
                .includeAllBenches(true)
                .build();

        weekStart = LocalDate.now().minusDays(3);
        weekEnd = weekStart.plusDays(6);
    }

    @Test
    @DisplayName("Should generate heatmap with observations")
    void generateHeatmap_WithObservations_ReturnsHeatmap() {
        // Arrange
        int week = 1;
        int year = 2025;

        ScoutingObservation observation1 = ScoutingObservation.builder()
                .id(UUID.randomUUID())
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(5)
                .build();

        ScoutingObservation observation2 = ScoutingObservation.builder()
                .id(UUID.randomUUID())
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.RED_SPIDER_MITE)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(3)
                .build();

        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(sessionRepository.findByFarmIdAndSessionDateBetween(
                eq(testFarm.getId()),
                any(LocalDate.class),
                any(LocalDate.class)
        ))
                .thenReturn(List.of(session));
        when(targetRepository.findBySessionIdIn(anyList()))
                .thenReturn(List.of(target));
        when(observationRepository.findBySessionIdIn(anyList()))
                .thenReturn(List.of(observation1, observation2));

        // Act
        HeatmapResponse response = heatmapService.generateHeatmap(
                testFarm.getId(),
                week,
                year
        );

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.farmId()).isEqualTo(testFarm.getId());
        assertThat(response.week()).isEqualTo(week);
        assertThat(response.year()).isEqualTo(year);
        assertThat(response.cells()).isNotEmpty();
        assertThat(response.sections()).isNotEmpty();
        assertThat(response.severityLegend()).isNotEmpty();

        verify(farmAccessService).requireViewAccess(testFarm);
    }

    @Test
    @DisplayName("Should return empty heatmap when no sessions")
    void generateHeatmap_WithNoSessions_ReturnsEmptyHeatmap() {
        // Arrange
        int week = 1;
        int year = 2025;

        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(sessionRepository.findByFarmIdAndSessionDateBetween(
                eq(testFarm.getId()),
                any(LocalDate.class),
                any(LocalDate.class)
        ))
                .thenReturn(new ArrayList<>());

        // Act
        HeatmapResponse response = heatmapService.generateHeatmap(
                testFarm.getId(),
                week,
                year
        );

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.cells()).isEmpty();
        assertThat(response.sections()).isEmpty();
        assertThat(response.severityLegend()).isNotEmpty();
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for invalid farm")
    void generateHeatmap_WithInvalidFarm_ThrowsResourceNotFoundException() {
        // Arrange
        UUID invalidFarmId = UUID.randomUUID();
        when(farmRepository.findById(invalidFarmId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> heatmapService.generateHeatmap(invalidFarmId, 1, 2025))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Farm");
    }

    @Test
    @DisplayName("Should aggregate observations correctly by cell")
    void generateHeatmap_WithMultipleObservations_AggregatesCorrectly() {
        // Arrange
        int week = 1;
        int year = 2025;

        // Same cell, different species
        ScoutingObservation obs1 = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(5)
                .build();

        ScoutingObservation obs2 = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.WHITEFLIES)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(2)
                .count(3)
                .build();

        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(sessionRepository.findByFarmIdAndSessionDateBetween(
                any(), any(LocalDate.class), any(LocalDate.class)
        ))
                .thenReturn(List.of(session));
        when(targetRepository.findBySessionIdIn(anyList()))
                .thenReturn(List.of(target));
        when(observationRepository.findBySessionIdIn(anyList()))
                .thenReturn(List.of(obs1, obs2));

        // Act
        HeatmapResponse response = heatmapService.generateHeatmap(
                testFarm.getId(),
                week,
                year
        );

        // Assert
        assertThat(response.cells()).isNotEmpty();
        // Should aggregate both observations into the same cell
        assertThat(response.cells().get(0).totalCount()).isEqualTo(8);
        assertThat(response.cells().get(0).pestCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("Should calculate severity levels correctly")
    void generateHeatmap_WithVaryingCounts_CalculatesSeverityCorrectly() {
        // Arrange
        int week = 1;
        int year = 2025;

        // Low severity (1-5)
        ScoutingObservation lowSeverity = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(3)
                .build();

        // High severity (11-20)
        ScoutingObservation highSeverity = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(2)
                .benchIndex(1)
                .spotIndex(1)
                .count(15)
                .build();

        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(sessionRepository.findByFarmIdAndSessionDateBetween(
                any(), any(LocalDate.class), any(LocalDate.class)
        ))
                .thenReturn(List.of(session));
        when(targetRepository.findBySessionIdIn(anyList()))
                .thenReturn(List.of(target));
        when(observationRepository.findBySessionIdIn(anyList()))
                .thenReturn(List.of(lowSeverity, highSeverity));

        // Act
        HeatmapResponse response = heatmapService.generateHeatmap(
                testFarm.getId(),
                week,
                year
        );

        // Assert
        assertThat(response.cells()).hasSize(2);
        assertThat(response.cells().get(0).severityLevel()).isEqualTo(SeverityLevel.LOW);
        assertThat(response.cells().get(1).severityLevel()).isEqualTo(SeverityLevel.HIGH);
    }

    @Test
    @DisplayName("Should create separate sections for multiple targets")
    void generateHeatmap_WithMultipleTargets_CreatesSeparateSections() {
        // Arrange
        int week = 1;
        int year = 2025;

        Greenhouse greenhouse2 = Greenhouse.builder()
                .id(UUID.randomUUID())
                .farm(testFarm)
                .name("Greenhouse 2")
                .bayCount(5)
                .benchesPerBay(10)
                .build();

        ScoutingSessionTarget target2 = ScoutingSessionTarget.builder()
                .id(UUID.randomUUID())
                .session(session)
                .greenhouse(greenhouse2)
                .build();

        ScoutingObservation obs1 = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(5)
                .build();

        ScoutingObservation obs2 = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target2)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(3)
                .build();

        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(sessionRepository.findByFarmIdAndSessionDateBetween(
                any(), any(LocalDate.class), any(LocalDate.class)
        ))
                .thenReturn(List.of(session));
        when(targetRepository.findBySessionIdIn(anyList()))
                .thenReturn(List.of(target, target2));
        when(observationRepository.findBySessionIdIn(anyList()))
                .thenReturn(List.of(obs1, obs2));

        // Act
        HeatmapResponse response = heatmapService.generateHeatmap(
                testFarm.getId(),
                week,
                year
        );

        // Assert
        assertThat(response.sections()).hasSize(2);
        assertThat(response.sections().get(0).targetName()).isEqualTo("Greenhouse 1");
        assertThat(response.sections().get(1).targetName()).isEqualTo("Greenhouse 2");
    }

    @Test
    @DisplayName("Should exclude beneficial observations from severity calculation")
    void generateHeatmap_WithBeneficials_ExcludesFromSeverity() {
        // Arrange
        int week = 1;
        int year = 2025;

        ScoutingObservation pestObs = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .count(5)
                .build();

        ScoutingObservation beneficialObs = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.BENEFICIAL_PP)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(2)
                .count(10)
                .build();

        when(farmRepository.findById(testFarm.getId()))
                .thenReturn(Optional.of(testFarm));
        when(sessionRepository.findByFarmIdAndSessionDateBetween(
                any(), any(LocalDate.class), any(LocalDate.class)
        ))
                .thenReturn(List.of(session));
        when(targetRepository.findBySessionIdIn(anyList()))
                .thenReturn(List.of(target));
        when(observationRepository.findBySessionIdIn(anyList()))
                .thenReturn(List.of(pestObs, beneficialObs));

        // Act
        HeatmapResponse response = heatmapService.generateHeatmap(
                testFarm.getId(),
                week,
                year
        );

        // Assert
        assertThat(response.cells()).hasSize(1);
        // Total count should be 5 (pests only), not 15
        assertThat(response.cells().getFirst().totalCount()).isEqualTo(5);
        assertThat(response.cells().getFirst().beneficialCount()).isEqualTo(10);
        assertThat(response.cells().getFirst().severityLevel()).isEqualTo(SeverityLevel.LOW);
    }
}

package mofo.com.pestscout.analytics.service;

import mofo.com.pestscout.analytics.dto.PestTrendResponse;
import mofo.com.pestscout.analytics.dto.SeverityTrendPointDto;
import mofo.com.pestscout.analytics.dto.TrendPointDto;
import mofo.com.pestscout.analytics.dto.WeeklyPestTrendDto;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrendAnalysisServiceTest {

    @Mock
    private ScoutingSessionRepository sessionRepository;

    @Mock
    private ScoutingObservationRepository observationRepository;

    private TrendAnalysisService service;

    @Test
    void aggregatesWeeklyPestTrendsAcrossWindow() {
        service = new TrendAnalysisService(sessionRepository, observationRepository);
        UUID farmId = UUID.randomUUID();
        LocalDate referenceMonday = LocalDate.of(2024, 6, 3); // Monday for deterministic week numbers

        ScoutingSession session = sessionOnDate(referenceMonday.minusWeeks(2));
        ScoutingObservation thrips = observation(session, SpeciesCode.THRIPS, 3);
        ScoutingObservation redSpider = observation(session, SpeciesCode.RED_SPIDER_MITE, 2);

        mockCurrentDate(referenceMonday, () -> {
            when(sessionRepository.findByFarmIdAndSessionDateBetween(Mockito.eq(farmId), Mockito.any(), Mockito.any()))
                    .thenReturn(List.of(session));
            when(observationRepository.findBySessionIdIn(List.of(session.getId())))
                    .thenReturn(List.of(thrips, redSpider));

            List<WeeklyPestTrendDto> result = service.getWeeklyPestTrends(farmId);

            WeekFields weekFields = WeekFields.ISO;
            int sessionWeek = session.getSessionDate().get(weekFields.weekOfWeekBasedYear());
            WeeklyPestTrendDto matchingWeek = result.stream()
                    .filter(dto -> dto.week().equals("W" + sessionWeek))
                    .findFirst()
                    .orElseThrow();

            assertThat(matchingWeek.thrips()).isEqualTo(3);
            assertThat(matchingWeek.redSpider()).isEqualTo(2);
            assertThat(matchingWeek.whiteflies()).isZero();
        });
    }

    @Test
    void aggregatesSeverityTrendByWeek() {
        service = new TrendAnalysisService(sessionRepository, observationRepository);
        UUID farmId = UUID.randomUUID();
        LocalDate referenceMonday = LocalDate.of(2024, 6, 3);

        ScoutingSession session = sessionOnDate(referenceMonday.minusWeeks(1));

        // Use the SeverityLevel overload so the count always falls into the correct bucket
        ScoutingObservation lowSeverity =
                observation(session, SpeciesCode.THRIPS, SeverityLevel.LOW);
        ScoutingObservation highSeverity =
                observation(session, SpeciesCode.MEALYBUGS, SeverityLevel.HIGH);

        mockCurrentDate(referenceMonday, () -> {
            when(sessionRepository.findByFarmIdAndSessionDateBetween(Mockito.eq(farmId), Mockito.any(), Mockito.any()))
                    .thenReturn(List.of(session));
            when(observationRepository.findBySessionIdIn(List.of(session.getId())))
                    .thenReturn(List.of(lowSeverity, highSeverity));

            List<SeverityTrendPointDto> trend = service.getSeverityTrend(farmId);
            WeekFields weekFields = WeekFields.ISO;
            int weekNumber = session.getSessionDate().get(weekFields.weekOfWeekBasedYear());
            SeverityTrendPointDto point = trend.stream()
                    .filter(dto -> dto.week().equals("W" + weekNumber))
                    .findFirst()
                    .orElseThrow();

            assertThat(point.low()).isEqualTo(1);
            assertThat(point.high()).isEqualTo(1);
            assertThat(point.medium()).isZero();
        });
    }

    @Test
    void buildsPestTrendResponseAcrossSessions() {
        service = new TrendAnalysisService(sessionRepository, observationRepository);
        UUID farmId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);

        ScoutingSession first = sessionOnDate(LocalDate.of(2024, 1, 5));
        ScoutingSession second = sessionOnDate(LocalDate.of(2024, 1, 12));

        ScoutingObservation obs1 = observation(first, SpeciesCode.WHITEFLIES, 4);
        ScoutingObservation obs2 = observation(second, SpeciesCode.WHITEFLIES, 6);
        ScoutingObservation differentSpecies = observation(second, SpeciesCode.THRIPS, 10);

        when(sessionRepository.findByFarmIdAndSessionDateBetween(farmId, start, end))
                .thenReturn(List.of(first, second));
        when(observationRepository.findBySessionIdIn(List.of(first.getId())))
                .thenReturn(List.of(obs1));
        when(observationRepository.findBySessionIdIn(List.of(second.getId())))
                .thenReturn(List.of(obs2, differentSpecies));

        PestTrendResponse response = service.getPestTrend(farmId, "WHITEFLIES", start, end);

        List<TrendPointDto> points = response.points();
        assertThat(points).hasSize(2);
        assertThat(points.getFirst().severity()).isEqualTo(4d);
        assertThat(points.get(1).severity()).isEqualTo(6d);

    }

    private ScoutingSession sessionOnDate(LocalDate date) {
        ScoutingSession session = ScoutingSession.builder()
                .sessionDate(date)
                .status(SessionStatus.DRAFT)
                .build();
        session.setId(UUID.randomUUID());
        return session;
    }

    private ScoutingObservation observation(ScoutingSession session, SpeciesCode species, int count) {
        ScoutingObservation observation = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(ScoutingSessionTarget.builder().build())
                .speciesCode(species)
                .count(count)
                .bayIndex(1)
                .benchIndex(1)
                .spotIndex(1)
                .build();
        observation.setId(UUID.randomUUID());
        return observation;
    }

    // Uses SeverityLevel API (getMinInclusive) instead of a nonexistent minThreshold()
    private ScoutingObservation observation(ScoutingSession session, SpeciesCode species, SeverityLevel level) {
        int count = level.getMinInclusive();   // any value in the level’s range would work
        return observation(session, species, count);
    }

    private void mockCurrentDate(LocalDate referenceDate, Runnable runnable) {
        // Make all LocalDate static calls real methods by default
        try (MockedStatic<LocalDate> mocked =
                     Mockito.mockStatic(LocalDate.class, Mockito.CALLS_REAL_METHODS)) {

            // Override only LocalDate.now()
            mocked.when(LocalDate::now).thenReturn(referenceDate);

            // Run the test code inside this scope
            runnable.run();
        }
    }

}

package mofo.com.pestscout.scouting.service;

import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.Greenhouse;
import mofo.com.pestscout.scouting.dto.*;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoutingSessionReportExportServiceTest {

    @Mock
    private ScoutingSessionService scoutingSessionService;

    @Mock
    private ScoutingSessionRepository sessionRepository;

    @InjectMocks
    private ScoutingSessionReportExportService reportExportService;

    @Test
    void exportSessionCsv_buildsDownloadableCsv() {
        UUID sessionId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID greenhouseId = UUID.randomUUID();

        ScoutingObservationDto observation = new ScoutingObservationDto(
                UUID.randomUUID(),
                1L,
                sessionId,
                targetId,
                greenhouseId,
                null,
                SpeciesCode.THRIPS,
                ObservationCategory.PEST,
                1,
                "Bay-A",
                2,
                "Bed-02",
                1,
                7,
                "=SUM(A1:A2)",
                LocalDateTime.of(2026, 3, 18, 12, 30),
                SyncStatus.SYNCED,
                false,
                null,
                UUID.randomUUID()
        );

        ScoutingSessionSectionDto section = new ScoutingSessionSectionDto(
                targetId,
                greenhouseId,
                null,
                true,
                true,
                List.of("Bay-A"),
                List.of("Bed-01", "Bed-02"),
                List.of(observation),
                new BigDecimal("1.50"),
                new ScoutingSectionCoverageDto(1, 1, 1, 2, false)
        );

        ScoutingSessionDetailDto report = new ScoutingSessionDetailDto(
                sessionId,
                3L,
                farmId,
                LocalDate.of(2026, 3, 18),
                12,
                SessionStatus.COMPLETED,
                SyncStatus.SYNCED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Tomato",
                "Cherry",
                new BigDecimal("24.5"),
                new BigDecimal("60.0"),
                LocalTime.of(8, 45),
                "Warm day",
                "Session summary",
                List.of(SpeciesCode.THRIPS),
                List.of(),
                PhotoSourceType.SCOUT_HANDHELD,
                LocalDateTime.of(2026, 3, 18, 8, 30),
                LocalDateTime.of(2026, 3, 18, 10, 0),
                LocalDateTime.of(2026, 3, 18, 11, 30),
                false,
                null,
                null,
                LocalDateTime.of(2026, 3, 18, 11, 30),
                true,
                null,
                List.of(section),
                List.of(new RecommendationEntryDto(RecommendationType.CHEMICAL_SPRAYS, "Apply targeted control")),
                null,
                null,
                null,
                false,
                "Africa/Nairobi"
        );

        ScoutingSession session = ScoutingSession.builder()
                .id(sessionId)
                .farm(Farm.builder().id(farmId).name("North Farm").build())
                .sessionDate(report.sessionDate())
                .status(SessionStatus.COMPLETED)
                .targets(new java.util.ArrayList<>())
                .observations(new java.util.ArrayList<>())
                .recommendations(new java.util.EnumMap<>(RecommendationType.class))
                .build();
        session.addTarget(ScoutingSessionTarget.builder()
                .id(targetId)
                .session(session)
                .greenhouse(Greenhouse.builder().id(greenhouseId).name("GH-1").build())
                .includeAllBays(true)
                .includeAllBenches(true)
                .build());

        when(scoutingSessionService.getSession(sessionId)).thenReturn(report);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        ScoutingSessionReportExportService.GeneratedCsvDocument document = reportExportService.exportSessionCsv(sessionId);

        String csv = new String(document.content(), StandardCharsets.UTF_8);

        assertThat(document.fileName()).isEqualTo("scouting-session-" + sessionId + "-2026-03-18.csv");
        assertThat(document.mediaType()).isEqualTo("text/csv");
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("\"session_id\"");
        assertThat(csv).contains("\"North Farm\"");
        assertThat(csv).contains("\"GH-1\"");
        assertThat(csv).contains("\"Thrips\"");
        assertThat(csv).contains("\"CHEMICAL_SPRAYS: Apply targeted control\"");
        assertThat(csv).contains("\"observation_timezone\"");
        assertThat(csv).contains("\"Africa/Nairobi\"");
        assertThat(csv).contains("\"'=SUM(A1:A2)\"");
    }
}

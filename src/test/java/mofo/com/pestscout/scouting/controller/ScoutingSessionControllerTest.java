package mofo.com.pestscout.scouting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.analytics.dto.SessionTargetRequest;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.scouting.dto.*;
import mofo.com.pestscout.scouting.model.PhotoSourceType;
import mofo.com.pestscout.scouting.model.SessionStatus;
import mofo.com.pestscout.scouting.service.ScoutingSessionReportExportService;
import mofo.com.pestscout.scouting.service.ScoutingSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ScoutingSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScoutingSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ScoutingSessionService sessionService;

    @MockitoBean
    private ScoutingSessionReportExportService reportExportService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void createsSession() throws Exception {
        UUID farmId = UUID.randomUUID();

        SessionTargetRequest targetRequest = new SessionTargetRequest(
                UUID.randomUUID(), // greenhouseId
                null,              // fieldBlockId
                true,
                true,
                List.of(),
                List.of()
        );

        CreateScoutingSessionRequest request = new CreateScoutingSessionRequest(
                farmId,
                UUID.randomUUID(),
                List.of(targetRequest),
                LocalDate.of(2024, 3, 5),
                10,
                "Tomatoes",
                "Cherry",
                new BigDecimal("22.5"),
                new BigDecimal("65.0"),
                LocalTime.NOON,
                "Clear",
                "Notes",
                PhotoSourceType.SCOUT_HANDHELD,
                SessionStatus.DRAFT,
                null,
                null,
                null,
                null,
                null
        );

        ScoutingSessionDetailDto detail = new ScoutingSessionDetailDto(
                UUID.randomUUID(),
                1L,
                farmId,
                request.sessionDate(),
                request.weekNumber(),
                SessionStatus.DRAFT,
                SyncStatus.SYNCED,
                null,
                null,
                request.crop(),
                request.variety(),
                request.temperatureCelsius(),
                request.relativeHumidityPercent(),
                request.observationTime(),
                request.weatherNotes(),
                request.notes(),
                request.defaultPhotoSourceType(),
                null,
                null,
                null,
                false,
                null,
                null,
                LocalDateTime.now(),
                false,
                null,
                List.of(
                        new ScoutingSessionSectionDto(
                                UUID.randomUUID(),
                                targetRequest.greenhouseId(),
                                targetRequest.fieldBlockId(),
                                targetRequest.includeAllBays(),
                                targetRequest.includeAllBenches(),
                                List.copyOf(targetRequest.bayTags()),
                                List.copyOf(targetRequest.benchTags()),
                                List.<ScoutingObservationDto>of()
                        )
                ),
                List.<RecommendationEntryDto>of()
        );

        when(sessionService.createSession(any(CreateScoutingSessionRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/scouting/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(farmId.toString()));
    }

    @Test
    void createsSessionWithoutScoutOrTargets() throws Exception {
        UUID farmId = UUID.randomUUID();

        CreateScoutingSessionRequest request = new CreateScoutingSessionRequest(
                farmId,
                null,
                null,
                LocalDate.of(2024, 3, 5),
                10,
                "Tomatoes",
                "Cherry",
                new BigDecimal("22.5"),
                new BigDecimal("65.0"),
                LocalTime.NOON,
                "Clear",
                "Notes",
                PhotoSourceType.SCOUT_HANDHELD,
                SessionStatus.DRAFT,
                null,
                null,
                null,
                null,
                null
        );

        ScoutingSessionDetailDto detail = new ScoutingSessionDetailDto(
                UUID.randomUUID(),
                1L,
                farmId,
                request.sessionDate(),
                request.weekNumber(),
                SessionStatus.DRAFT,
                SyncStatus.SYNCED,
                null,
                null,
                request.crop(),
                request.variety(),
                request.temperatureCelsius(),
                request.relativeHumidityPercent(),
                request.observationTime(),
                request.weatherNotes(),
                request.notes(),
                request.defaultPhotoSourceType(),
                null,
                null,
                null,
                false,
                null,
                null,
                LocalDateTime.now(),
                false,
                null,
                List.of(),
                List.of()
        );

        when(sessionService.createSession(any(CreateScoutingSessionRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/scouting/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(farmId.toString()));
    }

    @Test
    void requestsRemoteStart() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RemoteStartSessionRequest request = new RemoteStartSessionRequest(1L, "Scout needs help", null, null, null, "System Admin");

        ScoutingSessionDetailDto detail = new ScoutingSessionDetailDto(
                sessionId,
                1L,
                farmId,
                LocalDate.of(2024, 3, 5),
                10,
                SessionStatus.DRAFT,
                SyncStatus.SYNCED,
                null,
                UUID.randomUUID(),
                "Tomatoes",
                "Cherry",
                new BigDecimal("22.5"),
                new BigDecimal("65.0"),
                LocalTime.NOON,
                "Clear",
                "Notes",
                PhotoSourceType.SCOUT_HANDHELD,
                null,
                null,
                null,
                true,
                LocalDateTime.now(),
                "System Admin",
                LocalDateTime.now(),
                false,
                null,
                List.of(),
                List.of()
        );

        when(sessionService.requestRemoteStart(sessionId, request)).thenReturn(detail);

        mockMvc.perform(post("/api/scouting/sessions/{sessionId}/remote-start-request", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remoteStartConsentRequired").value(true))
                .andExpect(jsonPath("$.remoteStartRequestedByName").value("System Admin"));
    }

    @Test
    void acceptsRemoteStart() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AcceptRemoteStartRequest request = new AcceptRemoteStartRequest(1L, "Accepted remote accessed session start", null, null, null, "Scout User");

        ScoutingSessionDetailDto detail = new ScoutingSessionDetailDto(
                sessionId,
                1L,
                farmId,
                LocalDate.of(2024, 3, 5),
                10,
                SessionStatus.IN_PROGRESS,
                SyncStatus.SYNCED,
                null,
                UUID.randomUUID(),
                "Tomatoes",
                "Cherry",
                new BigDecimal("22.5"),
                new BigDecimal("65.0"),
                LocalTime.NOON,
                "Clear",
                "Notes",
                PhotoSourceType.SCOUT_HANDHELD,
                LocalDateTime.now(),
                null,
                null,
                false,
                null,
                null,
                LocalDateTime.now(),
                false,
                null,
                List.of(),
                List.of()
        );

        when(sessionService.acceptRemoteStart(sessionId, request)).thenReturn(detail);

        mockMvc.perform(post("/api/scouting/sessions/{sessionId}/accept-remote-start", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.remoteStartConsentRequired").value(false));
    }

    @Test
    void deletesSession() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(delete("/api/scouting/sessions/{sessionId}", sessionId))
                .andExpect(status().isNoContent());
    }

    @Test
    void reusesSessionAsDraft() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        ScoutingSessionDetailDto detail = new ScoutingSessionDetailDto(
                UUID.randomUUID(),
                1L,
                farmId,
                LocalDate.of(2024, 3, 12),
                11,
                SessionStatus.DRAFT,
                SyncStatus.PENDING_UPLOAD,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Tomatoes",
                "Cherry",
                null,
                null,
                null,
                null,
                "Carry over planning notes",
                PhotoSourceType.SCOUT_HANDHELD,
                null,
                null,
                null,
                false,
                null,
                null,
                LocalDateTime.now(),
                false,
                null,
                List.of(),
                List.of()
        );

        when(sessionService.reuseSession(sessionId)).thenReturn(detail);

        mockMvc.perform(post("/api/scouting/sessions/{sessionId}/reuse", sessionId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.notes").value("Carry over planning notes"));
    }

    @Test
    void syncsSessionsWithParams() throws Exception {
        UUID farmId = UUID.randomUUID();
        LocalDateTime since = LocalDateTime.of(2024, 3, 1, 0, 0);
        ScoutingSyncResponse sync = new ScoutingSyncResponse(List.of(), List.of());

        when(sessionService.syncChanges(farmId, since, true)).thenReturn(sync);

        mockMvc.perform(get("/api/scouting/sessions/sync")
                        .param("farmId", farmId.toString())
                        .param("since", since.toString())
                        .param("includeDeleted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray());
    }

    @Test
    void downloadsSessionReportCsv() throws Exception {
        UUID sessionId = UUID.randomUUID();
        byte[] content = "session_id,status\n123,COMPLETED\n".getBytes();

        when(reportExportService.exportSessionCsv(sessionId))
                .thenReturn(new ScoutingSessionReportExportService.GeneratedCsvDocument(
                        "scouting-session-" + sessionId + ".csv",
                        content
                ));

        mockMvc.perform(get("/api/scouting/sessions/{sessionId}/report.csv", sessionId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"scouting-session-" + sessionId + ".csv\""))
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(content));
    }
}

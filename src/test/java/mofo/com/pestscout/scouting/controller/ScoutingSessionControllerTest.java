package mofo.com.pestscout.scouting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.analytics.dto.SessionTargetRequest;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.common.model.SyncStatus;
import mofo.com.pestscout.scouting.dto.*;
import mofo.com.pestscout.scouting.model.ObservationLifecycleStatus;
import mofo.com.pestscout.scouting.model.ObservationType;
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
    void createsSessionWithObservationTimezone() throws Exception {
        UUID farmId = UUID.randomUUID();

        CreateScoutingSessionRequest request = new CreateScoutingSessionRequest(
                farmId,
                UUID.randomUUID(),
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
                null,
                null,
                PhotoSourceType.SCOUT_HANDHELD,
                SessionStatus.DRAFT,
                null,
                null,
                null,
                null,
                null,
                "America/Chicago"
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
                List.of(),
                List.of(),
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
                List.of(),
                null,
                null,
                null,
                false,
                request.observationTimezone()
        );

        when(sessionService.createSession(any(CreateScoutingSessionRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/scouting/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.observationTimezone").value("America/Chicago"));
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
    void acceptsSubmittedSession() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        AcceptSubmittedSessionRequest request = new AcceptSubmittedSessionRequest(1L, "Reviewed and accepted", null, null, null, "Manager User");

        ScoutingSessionDetailDto detail = new ScoutingSessionDetailDto(
                sessionId,
                1L,
                farmId,
                LocalDate.of(2024, 3, 5),
                10,
                SessionStatus.COMPLETED,
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
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now(),
                false,
                null,
                null,
                LocalDateTime.now(),
                true,
                null,
                List.of(),
                List.of()
        );

        when(sessionService.completeSession(sessionId, request)).thenReturn(detail);

        mockMvc.perform(post("/api/scouting/sessions/{sessionId}/accept", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void startsSession() throws Exception {
        UUID farmId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

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

        when(sessionService.startSession(sessionId)).thenReturn(detail);

        mockMvc.perform(post("/api/scouting/sessions/{sessionId}/start", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
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
    void listsSessionsWithoutFarmIdForSuperAdminView() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();
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
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
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
                List.of(),
                "Farm A",
                2024,
                "2024-W10",
                true
        );

        when(sessionService.listSessions(null)).thenReturn(List.of(detail));

        mockMvc.perform(get("/api/scouting/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].farmName").value("Farm A"))
                .andExpect(jsonPath("$[0].openRestricted").value(true));
    }

    @Test
    void updatesObservation() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();

        UpsertObservationRequest request = new UpsertObservationRequest(
                sessionId,
                UUID.randomUUID(),
                null,
                1,
                "Bay-1",
                1,
                "Bed-1",
                1,
                8,
                "Edited value",
                UUID.randomUUID(),
                1L
        );

        ScoutingObservationDto observation = new ScoutingObservationDto(
                observationId,
                2L,
                sessionId,
                request.sessionTargetId(),
                UUID.randomUUID(),
                null,
                mofo.com.pestscout.scouting.model.SpeciesCode.THRIPS,
                null,
                "Thrips",
                "CODE:THRIPS",
                mofo.com.pestscout.scouting.model.ObservationCategory.PEST,
                request.bayIndex(),
                request.bayTag(),
                request.benchIndex(),
                request.benchTag(),
                request.spotIndex(),
                request.count(),
                request.notes(),
                LocalDateTime.now(),
                SyncStatus.PENDING_UPLOAD,
                false,
                null,
                request.clientRequestId(),
                "LOCAL-OBS-77",
                ObservationType.SUSPECTED_PEST,
                ObservationLifecycleStatus.CAPTURED_OFFLINE,
                new BigDecimal("1.2000000"),
                new BigDecimal("36.8000000"),
                "{\"type\":\"Point\",\"coordinates\":[36.8,1.2]}"
        );

        when(sessionService.updateObservation(sessionId, observationId, request)).thenReturn(observation);

        mockMvc.perform(put("/api/scouting/sessions/{sessionId}/observations/{observationId}", sessionId, observationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(observationId.toString()))
                .andExpect(jsonPath("$.count").value(8))
                .andExpect(jsonPath("$.notes").value("Edited value"))
                .andExpect(jsonPath("$.localObservationId").value("LOCAL-OBS-77"))
                .andExpect(jsonPath("$.observationType").value("SUSPECTED_PEST"))
                .andExpect(jsonPath("$.lifecycleStatus").value("CAPTURED_OFFLINE"))
                .andExpect(jsonPath("$.latitude").value(1.2))
                .andExpect(jsonPath("$.longitude").value(36.8));
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

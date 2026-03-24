package mofo.com.pestscout.scouting.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.scouting.dto.*;
import mofo.com.pestscout.scouting.service.ScoutingSessionReportExportService;
import mofo.com.pestscout.scouting.service.ScoutingSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/scouting/sessions")
@RequiredArgsConstructor
public class ScoutingSessionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScoutingSessionController.class);

    private final ScoutingSessionService sessionService;
    private final ScoutingSessionReportExportService reportExportService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<ScoutingSessionDetailDto> createSession(@Valid @RequestBody CreateScoutingSessionRequest request) {
        LOGGER.info("POST /api/scouting/sessions — creating session");
        ScoutingSessionDetailDto session = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PutMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    public ResponseEntity<ScoutingSessionDetailDto> updateSession(@PathVariable UUID sessionId,
                                                                 @Valid @RequestBody UpdateScoutingSessionRequest request) {
        LOGGER.info("PUT /api/scouting/sessions/{} — updating session", sessionId);
        return ResponseEntity.ok(sessionService.updateSession(sessionId, request));
    }

    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSession(@PathVariable UUID sessionId) {
        LOGGER.info("DELETE /api/scouting/sessions/{} â€” deleting session", sessionId);
        sessionService.deleteSession(sessionId);
    }

    @PostMapping("/{sessionId}/reuse")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<ScoutingSessionDetailDto> reuseSession(@PathVariable UUID sessionId) {
        LOGGER.info("POST /api/scouting/sessions/{}/reuse - creating reusable draft copy", sessionId);
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.reuseSession(sessionId));
    }

    @PostMapping("/{sessionId}/start")
    @PreAuthorize("hasRole('SCOUT')")
    public ResponseEntity<ScoutingSessionDetailDto> startSession(@PathVariable UUID sessionId) {
        LOGGER.info("POST /api/scouting/sessions/{}/start — starting session", sessionId);
        return ResponseEntity.ok(sessionService.startSession(sessionId));
    }

    @PostMapping("/{sessionId}/remote-start-request")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ScoutingSessionDetailDto> requestRemoteStart(@PathVariable UUID sessionId,
                                                                       @Valid @RequestBody RemoteStartSessionRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/remote-start-request â€” requesting scout consent for remote start", sessionId);
        return ResponseEntity.ok(sessionService.requestRemoteStart(sessionId, request));
    }

    @PostMapping("/{sessionId}/accept-remote-start")
    @PreAuthorize("hasRole('SCOUT')")
    public ResponseEntity<ScoutingSessionDetailDto> acceptRemoteStart(@PathVariable UUID sessionId,
                                                                      @Valid @RequestBody AcceptRemoteStartRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/accept-remote-start â€” accepting remote start request", sessionId);
        return ResponseEntity.ok(sessionService.acceptRemoteStart(sessionId, request));
    }

    @PostMapping("/{sessionId}/submit")
    @PreAuthorize("hasRole('SCOUT')")
    public ResponseEntity<ScoutingSessionDetailDto> submitSession(@PathVariable UUID sessionId,
                                                                  @Valid @RequestBody SubmitSessionRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/submit — submitting session for approval", sessionId);
        return ResponseEntity.ok(sessionService.submitSession(sessionId, request));
    }

    @PostMapping({"/{sessionId}/accept", "/{sessionId}/complete"})
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<ScoutingSessionDetailDto> acceptSession(@PathVariable UUID sessionId,
                                                                  @Valid @RequestBody AcceptSubmittedSessionRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/accept — accepting submitted session", sessionId);
        return ResponseEntity.ok(sessionService.completeSession(sessionId, request));
    }

    @PostMapping("/{sessionId}/reopen")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<ScoutingSessionDetailDto> reopenSession(@PathVariable UUID sessionId,
                                                                  @RequestBody(required = false) ReopenSessionRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/reopen — reopening session", sessionId);
        return ResponseEntity.ok(sessionService.reopenSession(sessionId, request));
    }

    @PostMapping("/{sessionId}/observations")
    @PreAuthorize("hasRole('SCOUT')")
    public ResponseEntity<ScoutingObservationDto> upsertObservation(@PathVariable UUID sessionId,
                                                                    @Valid @RequestBody UpsertObservationRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/observations — upserting observation", sessionId);
        ScoutingObservationDto observation = sessionService.upsertObservation(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(observation);
    }

    @PutMapping("/{sessionId}/observations/{observationId}")
    @PreAuthorize("hasAnyRole('SCOUT')")
    public ResponseEntity<ScoutingObservationDto> updateObservation(@PathVariable UUID sessionId,
                                                                    @PathVariable UUID observationId,
                                                                    @Valid @RequestBody UpsertObservationRequest request) {
        LOGGER.info("PUT /api/scouting/sessions/{}/observations/{} - updating observation", sessionId, observationId);
        return ResponseEntity.ok(sessionService.updateObservation(sessionId, observationId, request));
    }

    @PostMapping("/{sessionId}/observations/bulk")
    @PreAuthorize("hasRole('SCOUT')")
    public ResponseEntity<List<ScoutingObservationDto>> bulkUpsertObservations(@PathVariable UUID sessionId,
                                                                               @Valid @RequestBody BulkUpsertObservationsRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/observations/bulk — bulk upsert {} rows", sessionId, request.observations().size());
        List<ScoutingObservationDto> observations = sessionService.bulkUpsertObservations(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(observations);
    }

    @DeleteMapping("/{sessionId}/observations/{observationId}")
    @PreAuthorize("hasRole('SCOUT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteObservation(@PathVariable UUID sessionId,
                                  @PathVariable UUID observationId) {
        LOGGER.info("DELETE /api/scouting/sessions/{}/observations/{} — deleting observation", sessionId, observationId);
        sessionService.deleteObservation(sessionId, observationId);
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    public ResponseEntity<ScoutingSessionDetailDto> getSession(@PathVariable UUID sessionId,
                                                               @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                                                               @RequestHeader(value = "X-Device-Type", required = false) String deviceType,
                                                               @RequestHeader(value = "X-Device-Location", required = false) String location,
                                                               @RequestHeader(value = "X-Actor-Name", required = false) String actorName) {
        LOGGER.info("GET /api/scouting/sessions/{} — loading session", sessionId);
        return ResponseEntity.ok(sessionService.getSession(sessionId, deviceId, deviceType, location, actorName));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    public ResponseEntity<List<ScoutingSessionDetailDto>> listSessions(@RequestParam(required = false) UUID farmId) {
        LOGGER.info("GET /api/scouting/sessions — listing sessions for {}", farmId != null ? "farm " + farmId : "all visible farms");
        return ResponseEntity.ok(sessionService.listSessions(farmId));
    }

    @GetMapping("/sync")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    public ResponseEntity<ScoutingSyncResponse> syncSessions(
            @RequestParam UUID farmId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @RequestParam(defaultValue = "false") boolean includeDeleted
    ) {
        LOGGER.info("GET /api/scouting/sessions/sync — changes for farm {} since {}", farmId, since);
        return ResponseEntity.ok(sessionService.syncChanges(farmId, since, includeDeleted));
    }

    @GetMapping("/{sessionId}/audits")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<ScoutingSessionAuditDto>> listAuditTrail(@PathVariable UUID sessionId) {
        LOGGER.info("GET /api/scouting/sessions/{}/audits — listing audit trail", sessionId);
        return ResponseEntity.ok(sessionService.listAuditTrail(sessionId));
    }

    @GetMapping(value = {"/{sessionId}/report.csv", "/{sessionId}/export.csv"}, produces = "text/csv")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<byte[]> downloadSessionReportCsv(@PathVariable UUID sessionId) {
        LOGGER.info("GET /api/scouting/sessions/{}/report.csv - exporting session report", sessionId);
        ScoutingSessionReportExportService.GeneratedCsvDocument document = reportExportService.exportSessionCsv(sessionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.fileName() + "\"")
                .contentType(MediaType.parseMediaType(document.mediaType()))
                .body(document.content());
    }
}

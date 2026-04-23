package mofo.com.pestscout.scouting.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.dto.ErrorResponse;
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
@Tag(name = "Scouting Sessions", description = "Scouting session planning, field capture, review, and sync APIs")
@SecurityRequirement(name = "bearerAuth")
/**
 * REST controller for scouting session planning, capture, review, and sync operations.
 */
public class ScoutingSessionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScoutingSessionController.class);

    private final ScoutingSessionService sessionService;
    private final ScoutingSessionReportExportService reportExportService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    @Operation(summary = "Create a scouting session", description = "Creates a new scouting session for a farm.")
    public ResponseEntity<ScoutingSessionDetailDto> createSession(@Valid @RequestBody CreateScoutingSessionRequest request) {
        LOGGER.info("POST /api/scouting/sessions - creating session");
        ScoutingSessionDetailDto session = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PutMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    @Operation(summary = "Update session metadata", description = "Updates session planning metadata or scout-editable runtime fields.")
    public ResponseEntity<ScoutingSessionDetailDto> updateSession(@PathVariable UUID sessionId,
                                                                  @Valid @RequestBody UpdateScoutingSessionRequest request) {
        LOGGER.info("PUT /api/scouting/sessions/{} - updating session", sessionId);
        return ResponseEntity.ok(sessionService.updateSession(sessionId, request));
    }

    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a planning session", description = "Deletes a draft or new scouting session.")
    public void deleteSession(@PathVariable UUID sessionId) {
        LOGGER.info("DELETE /api/scouting/sessions/{} - deleting session", sessionId);
        sessionService.deleteSession(sessionId);
    }

    @PostMapping("/{sessionId}/reuse")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    @Operation(summary = "Reuse a session", description = "Creates a reusable draft copy of an existing session.")
    public ResponseEntity<ScoutingSessionDetailDto> reuseSession(@PathVariable UUID sessionId) {
        LOGGER.info("POST /api/scouting/sessions/{}/reuse - creating reusable draft copy", sessionId);
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.reuseSession(sessionId));
    }

    @PostMapping("/{sessionId}/start")
    @PreAuthorize("hasRole('SCOUT')")
    @Operation(summary = "Start a session", description = "Starts a session as the assigned scout.")
    public ResponseEntity<ScoutingSessionDetailDto> startSession(@PathVariable UUID sessionId) {
        LOGGER.info("POST /api/scouting/sessions/{}/start - starting session", sessionId);
        return ResponseEntity.ok(sessionService.startSession(sessionId));
    }

    @PostMapping("/{sessionId}/remote-start-request")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Request remote start", description = "Creates a remote-start consent request for the assigned scout.")
    public ResponseEntity<ScoutingSessionDetailDto> requestRemoteStart(@PathVariable UUID sessionId,
                                                                       @Valid @RequestBody RemoteStartSessionRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/remote-start-request - requesting scout consent for remote start", sessionId);
        return ResponseEntity.ok(sessionService.requestRemoteStart(sessionId, request));
    }

    @PostMapping("/{sessionId}/accept-remote-start")
    @PreAuthorize("hasRole('SCOUT')")
    @Operation(summary = "Accept remote start", description = "Accepts a pending remote-start request as the assigned scout.")
    public ResponseEntity<ScoutingSessionDetailDto> acceptRemoteStart(@PathVariable UUID sessionId,
                                                                      @Valid @RequestBody AcceptRemoteStartRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/accept-remote-start - accepting remote start request", sessionId);
        return ResponseEntity.ok(sessionService.acceptRemoteStart(sessionId, request));
    }

    @PostMapping("/{sessionId}/submit")
    @PreAuthorize("hasRole('SCOUT')")
    @Operation(summary = "Submit a session", description = "Submits a completed scouting session for manager review.")
    public ResponseEntity<ScoutingSessionDetailDto> submitSession(@PathVariable UUID sessionId,
                                                                  @Valid @RequestBody SubmitSessionRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/submit - submitting session for approval", sessionId);
        return ResponseEntity.ok(sessionService.submitSession(sessionId, request));
    }

    @PostMapping({"/{sessionId}/accept", "/{sessionId}/complete"})
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    @Operation(summary = "Accept a submitted session", description = "Completes a submitted session after manager review.")
    public ResponseEntity<ScoutingSessionDetailDto> acceptSession(@PathVariable UUID sessionId,
                                                                  @Valid @RequestBody AcceptSubmittedSessionRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/accept - accepting submitted session", sessionId);
        return ResponseEntity.ok(sessionService.completeSession(sessionId, request));
    }

    @PostMapping("/{sessionId}/reopen")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    @Operation(summary = "Reopen a completed session", description = "Reopens a completed session so it can be edited again.")
    public ResponseEntity<ScoutingSessionDetailDto> reopenSession(@PathVariable UUID sessionId,
                                                                  @RequestBody(required = false) ReopenSessionRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/reopen - reopening session", sessionId);
        return ResponseEntity.ok(sessionService.reopenSession(sessionId, request));
    }

    @PostMapping("/{sessionId}/observations")
    @PreAuthorize("hasRole('SCOUT')")
    @Operation(
            summary = "Create or upsert one observation",
            description = "Creates or updates a scouting observation row for the selected session target. Supports species-based observations, type-only suspicious observations, local observation ids, lifecycle state, and per-observation coordinates.",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Observation stored successfully",
                            content = @Content(schema = @Schema(implementation = ScoutingObservationDto.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Validation error, including mismatched session ids or incomplete coordinate pairs",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Caller is not allowed to capture observations for this session",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Idempotency or version conflict",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    /**
     * Creates or upserts one observation row inside a scouting session.
     */
    public ResponseEntity<ScoutingObservationDto> upsertObservation(@PathVariable UUID sessionId,
                                                                    @Valid @RequestBody UpsertObservationRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/observations - upserting observation", sessionId);
        ScoutingObservationDto observation = sessionService.upsertObservation(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(observation);
    }

    @PutMapping("/{sessionId}/observations/{observationId}")
    @PreAuthorize("hasAnyRole('SCOUT')")
    @Operation(
            summary = "Update one draft observation",
            description = "Updates a persisted draft observation by id. The request may change the observation type, lifecycle state, coordinates, geometry, notes, or identified species.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Observation updated successfully",
                            content = @Content(schema = @Schema(implementation = ScoutingObservationDto.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Observation draft not found in the session",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Version or idempotency conflict",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    /**
     * Updates one persisted draft observation by its server-side id.
     */
    public ResponseEntity<ScoutingObservationDto> updateObservation(@PathVariable UUID sessionId,
                                                                    @PathVariable UUID observationId,
                                                                    @Valid @RequestBody UpsertObservationRequest request) {
        LOGGER.info("PUT /api/scouting/sessions/{}/observations/{} - updating observation", sessionId, observationId);
        return ResponseEntity.ok(sessionService.updateObservation(sessionId, observationId, request));
    }

    @PostMapping("/{sessionId}/observations/bulk")
    @PreAuthorize("hasRole('SCOUT')")
    @Operation(
            summary = "Bulk upsert observations",
            description = "Processes one or more observation rows for a scouting session. Designed for offline grid capture and batched sync uploads.",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Observations processed successfully",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScoutingObservationDto.class)))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Validation error or bulk payload/session mismatch",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Caller is not allowed to edit observations for this session",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    /**
     * Processes multiple observation upserts in one request for batch or offline replay.
     */
    public ResponseEntity<List<ScoutingObservationDto>> bulkUpsertObservations(@PathVariable UUID sessionId,
                                                                               @Valid @RequestBody BulkUpsertObservationsRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/observations/bulk - bulk upsert {} rows", sessionId, request.observations().size());
        List<ScoutingObservationDto> observations = sessionService.bulkUpsertObservations(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(observations);
    }

    @DeleteMapping("/{sessionId}/observations/{observationId}")
    @PreAuthorize("hasRole('SCOUT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one observation", description = "Soft-deletes a persisted draft observation row.")
    public void deleteObservation(@PathVariable UUID sessionId,
                                  @PathVariable UUID observationId) {
        LOGGER.info("DELETE /api/scouting/sessions/{}/observations/{} - deleting observation", sessionId, observationId);
        sessionService.deleteObservation(sessionId, observationId);
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    @Operation(
            summary = "Load one scouting session",
            description = "Returns the full session detail payload, including nested observation rows with localObservationId, observationType, lifecycleStatus, and optional coordinates when the caller is allowed to view them.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Session loaded successfully",
                            content = @Content(schema = @Schema(implementation = ScoutingSessionDetailDto.class))
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Caller cannot open the session detail",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Session not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    /**
     * Loads one full scouting session, including nested section observations when visible to the caller.
     */
    public ResponseEntity<ScoutingSessionDetailDto> getSession(@PathVariable UUID sessionId,
                                                               @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                                                               @RequestHeader(value = "X-Device-Type", required = false) String deviceType,
                                                               @RequestHeader(value = "X-Device-Location", required = false) String location,
                                                               @RequestHeader(value = "X-Actor-Name", required = false) String actorName) {
        LOGGER.info("GET /api/scouting/sessions/{} - loading session", sessionId);
        return ResponseEntity.ok(sessionService.getSession(sessionId, deviceId, deviceType, location, actorName));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    @Operation(summary = "List visible sessions", description = "Lists scouting sessions visible to the current caller, optionally scoped to one farm.")
    public ResponseEntity<List<ScoutingSessionDetailDto>> listSessions(@RequestParam(required = false) UUID farmId) {
        LOGGER.info("GET /api/scouting/sessions - listing sessions for {}", farmId != null ? "farm " + farmId : "all visible farms");
        return ResponseEntity.ok(sessionService.listSessions(farmId));
    }

    @GetMapping("/sync")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    @Operation(
            summary = "Fetch session and observation deltas",
            description = "Returns session and observation changes since a timestamp for offline clients. Observation deltas include localObservationId, observationType, lifecycleStatus, and per-observation coordinates when present.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Delta sync payload returned successfully",
                            content = @Content(schema = @Schema(implementation = ScoutingSyncResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid sync request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Caller cannot access scouting data for the farm",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    /**
     * Returns session and observation deltas since the supplied timestamp.
     */
    public ResponseEntity<ScoutingSyncResponse> syncSessions(
            @Parameter(description = "Farm id to synchronize.")
            @RequestParam UUID farmId,
            @Parameter(description = "Return records changed after this timestamp.")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @Parameter(description = "Include soft-deleted observations in the response.")
            @RequestParam(defaultValue = "false") boolean includeDeleted
    ) {
        LOGGER.info("GET /api/scouting/sessions/sync - changes for farm {} since {}", farmId, since);
        return ResponseEntity.ok(sessionService.syncChanges(farmId, since, includeDeleted));
    }

    @GetMapping("/{sessionId}/audits")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List audit trail", description = "Returns the audit trail for one scouting session.")
    public ResponseEntity<List<ScoutingSessionAuditDto>> listAuditTrail(@PathVariable UUID sessionId) {
        LOGGER.info("GET /api/scouting/sessions/{}/audits - listing audit trail", sessionId);
        return ResponseEntity.ok(sessionService.listAuditTrail(sessionId));
    }

    @GetMapping(value = {"/{sessionId}/report.csv", "/{sessionId}/export.csv"}, produces = "text/csv")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    @Operation(summary = "Download session CSV report", description = "Exports a scouting session report as CSV.")
    public ResponseEntity<byte[]> downloadSessionReportCsv(@PathVariable UUID sessionId) {
        LOGGER.info("GET /api/scouting/sessions/{}/report.csv - exporting session report", sessionId);
        ScoutingSessionReportExportService.GeneratedCsvDocument document = reportExportService.exportSessionCsv(sessionId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.fileName() + "\"")
                .contentType(MediaType.parseMediaType(document.mediaType()))
                .body(document.content());
    }
}

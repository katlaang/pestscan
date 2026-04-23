package mofo.com.pestscout.scouting.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.common.dto.ErrorResponse;
import mofo.com.pestscout.scouting.dto.*;
import mofo.com.pestscout.scouting.service.ScoutingPhotoService;
import mofo.com.pestscout.scouting.service.ScoutingSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cloud/sync")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Edge Sync", description = "Edge-to-cloud sync endpoints for scouting sessions, observations, and photos")
@SecurityRequirement(name = "bearerAuth")
/**
 * Edge-facing controller for cloud sync of scouting sessions, observations, and photos.
 */
public class CloudSyncController {

    private final ScoutingSessionService sessionService;
    private final ScoutingPhotoService photoService;

    @PostMapping("/sessions")
    @PreAuthorize("hasRole('EDGE_SYNC')")
    @Operation(
            summary = "Fetch delta sync payload for edge clients",
            description = "Returns session and observation deltas for edge clients. Observation rows include localObservationId, observationType, lifecycleStatus, and optional coordinates when present.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Sync payload returned successfully",
                            content = @Content(schema = @Schema(implementation = ScoutingSyncResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid sync request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Caller is not an EDGE_SYNC client",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    /**
     * Returns delta sync payloads to edge clients for one farm and timestamp window.
     */
    public ResponseEntity<ScoutingSyncResponse> syncSessions(@Valid @RequestBody CloudSessionSyncRequest request) {
        log.info("POST /api/cloud/sync/sessions - farm {} since {}", request.farmId(), request.since());
        return ResponseEntity.ok(sessionService.syncChanges(request.farmId(), request.since(), request.includeDeleted()));
    }

    @PostMapping("/photos/register")
    @PreAuthorize("hasRole('EDGE_SYNC')")
    @Operation(summary = "Register photo metadata", description = "Registers photo metadata before the image upload completes.")
    public ResponseEntity<ScoutingPhotoDto> registerPhoto(@Valid @RequestBody PhotoMetadataRequest request) {
        log.info("POST /api/cloud/sync/photos/register - session {}", request.sessionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(photoService.registerMetadata(request));
    }

    @PostMapping("/photos/confirm")
    @PreAuthorize("hasRole('EDGE_SYNC')")
    @Operation(summary = "Confirm photo upload", description = "Marks a previously registered scouting photo as uploaded.")
    public ResponseEntity<ScoutingPhotoDto> confirmPhoto(@Valid @RequestBody PhotoUploadConfirmationRequest request) {
        log.info("POST /api/cloud/sync/photos/confirm - session {}", request.sessionId());
        return ResponseEntity.ok(photoService.confirmUpload(request));
    }
}

package mofo.com.pestscout.scouting.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.scouting.dto.CloudSessionSyncRequest;
import mofo.com.pestscout.scouting.dto.PhotoMetadataRequest;
import mofo.com.pestscout.scouting.dto.PhotoUploadConfirmationRequest;
import mofo.com.pestscout.scouting.dto.ScoutingPhotoDto;
import mofo.com.pestscout.scouting.dto.ScoutingSyncResponse;
import mofo.com.pestscout.scouting.service.ScoutingPhotoService;
import mofo.com.pestscout.scouting.service.ScoutingSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cloud/sync")
@RequiredArgsConstructor
@Slf4j
public class CloudSyncController {

    private final ScoutingSessionService sessionService;
    private final ScoutingPhotoService photoService;

    @PostMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScoutingSyncResponse> syncSessions(@Valid @RequestBody CloudSessionSyncRequest request) {
        log.info("POST /api/cloud/sync/sessions — farm {} since {}", request.farmId(), request.since());
        return ResponseEntity.ok(sessionService.syncChanges(request.farmId(), request.since(), request.includeDeleted()));
    }

    @PostMapping("/photos/register")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScoutingPhotoDto> registerPhoto(@Valid @RequestBody PhotoMetadataRequest request) {
        log.info("POST /api/cloud/sync/photos/register — session {}", request.sessionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(photoService.registerMetadata(request));
    }

    @PostMapping("/photos/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScoutingPhotoDto> confirmPhoto(@Valid @RequestBody PhotoUploadConfirmationRequest request) {
        log.info("POST /api/cloud/sync/photos/confirm — session {}", request.sessionId());
        return ResponseEntity.ok(photoService.confirmUpload(request));
    }
}


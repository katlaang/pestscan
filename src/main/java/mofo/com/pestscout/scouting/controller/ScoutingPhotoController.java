package mofo.com.pestscout.scouting.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.scouting.dto.PhotoMetadataRequest;
import mofo.com.pestscout.scouting.dto.PhotoUploadConfirmationRequest;
import mofo.com.pestscout.scouting.dto.ScoutingPhotoDto;
import mofo.com.pestscout.scouting.service.ScoutingPhotoService;
import mofo.com.pestscout.scouting.service.ScoutingSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/scouting/photos")
@RequiredArgsConstructor
@Slf4j
public class ScoutingPhotoController {

    private final ScoutingPhotoService photoService;
    private final ScoutingSessionService sessionService;

    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER','SCOUT')")
    public ResponseEntity<List<ScoutingPhotoDto>> listSessionPhotos(@PathVariable UUID sessionId) {
        log.info("GET /api/scouting/photos/session/{} - listing session photos", sessionId);
        sessionService.getSession(sessionId);
        return ResponseEntity.ok(photoService.listSessionPhotos(sessionId));
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('SCOUT')")
    public ResponseEntity<ScoutingPhotoDto> register(@Valid @RequestBody PhotoMetadataRequest request) {
        log.info("POST /api/scouting/photos/register - registering photo metadata for session {}", request.sessionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(photoService.registerMetadata(request));
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasRole('SCOUT')")
    public ResponseEntity<ScoutingPhotoDto> confirmUpload(@Valid @RequestBody PhotoUploadConfirmationRequest request) {
        log.info("POST /api/scouting/photos/confirm - confirming upload for session {}", request.sessionId());
        return ResponseEntity.ok(photoService.confirmUpload(request));
    }

    @DeleteMapping("/session/{sessionId}/{photoId}")
    @PreAuthorize("hasRole('SCOUT')")
    public ResponseEntity<Void> deletePhoto(@PathVariable UUID sessionId, @PathVariable UUID photoId) {
        log.info("DELETE /api/scouting/photos/session/{}/{} - deleting scouting photo", sessionId, photoId);
        photoService.deletePhoto(sessionId, photoId);
        return ResponseEntity.noContent().build();
    }
}

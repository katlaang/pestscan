package mofo.com.pestscout.scouting.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.scouting.dto.PhotoMetadataRequest;
import mofo.com.pestscout.scouting.dto.PhotoUploadConfirmationRequest;
import mofo.com.pestscout.scouting.dto.ScoutingPhotoDto;
import mofo.com.pestscout.scouting.service.ScoutingPhotoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scouting/photos")
@RequiredArgsConstructor
@Slf4j
public class ScoutingPhotoController {

    private final ScoutingPhotoService photoService;

    @PostMapping("/register")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScoutingPhotoDto> register(@Valid @RequestBody PhotoMetadataRequest request) {
        log.info("POST /api/scouting/photos/register — registering photo metadata for session {}", request.sessionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(photoService.registerMetadata(request));
    }

    @PostMapping("/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScoutingPhotoDto> confirmUpload(@Valid @RequestBody PhotoUploadConfirmationRequest request) {
        log.info("POST /api/scouting/photos/confirm — confirming upload for session {}", request.sessionId());
        return ResponseEntity.ok(photoService.confirmUpload(request));
    }
}


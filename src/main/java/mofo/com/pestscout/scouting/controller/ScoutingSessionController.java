package mofo.com.pestscout.scouting.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.scouting.dto.*;
import mofo.com.pestscout.scouting.service.ScoutingSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/scouting/sessions")
@RequiredArgsConstructor
public class ScoutingSessionController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScoutingSessionController.class);

    private final ScoutingSessionService sessionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<ScoutingSessionDetailDto> createSession(@Valid @RequestBody CreateScoutingSessionRequest request) {
        LOGGER.info("POST /api/scouting/sessions — creating session");
        ScoutingSessionDetailDto session = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PutMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<ScoutingSessionDetailDto> updateSession(@PathVariable UUID sessionId,
                                                                 @Valid @RequestBody UpdateScoutingSessionRequest request) {
        LOGGER.info("PUT /api/scouting/sessions/{} — updating session", sessionId);
        return ResponseEntity.ok(sessionService.updateSession(sessionId, request));
    }

    @PostMapping("/{sessionId}/start")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<ScoutingSessionDetailDto> startSession(@PathVariable UUID sessionId) {
        LOGGER.info("POST /api/scouting/sessions/{}/start — starting session", sessionId);
        return ResponseEntity.ok(sessionService.startSession(sessionId));
    }

    @PostMapping("/{sessionId}/complete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<ScoutingSessionDetailDto> completeSession(@PathVariable UUID sessionId,
                                                                   @Valid @RequestBody CompleteSessionRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/complete — completing session", sessionId);
        return ResponseEntity.ok(sessionService.completeSession(sessionId, request));
    }

    @PostMapping("/{sessionId}/reopen")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<ScoutingSessionDetailDto> reopenSession(@PathVariable UUID sessionId) {
        LOGGER.info("POST /api/scouting/sessions/{}/reopen — reopening session", sessionId);
        return ResponseEntity.ok(sessionService.reopenSession(sessionId));
    }

    @PostMapping("/{sessionId}/observations")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SCOUT')")
    public ResponseEntity<ScoutingObservationDto> upsertObservation(@PathVariable UUID sessionId,
                                                                    @Valid @RequestBody UpsertObservationRequest request) {
        LOGGER.info("POST /api/scouting/sessions/{}/observations — upserting observation", sessionId);
        ScoutingObservationDto observation = sessionService.upsertObservation(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(observation);
    }

    @DeleteMapping("/{sessionId}/observations/{observationId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SCOUT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteObservation(@PathVariable UUID sessionId,
                                  @PathVariable UUID observationId) {
        LOGGER.info("DELETE /api/scouting/sessions/{}/observations/{} — deleting observation", sessionId, observationId);
        sessionService.deleteObservation(sessionId, observationId);
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ScoutingSessionDetailDto> getSession(@PathVariable UUID sessionId) {
        LOGGER.info("GET /api/scouting/sessions/{} — loading session", sessionId);
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ScoutingSessionDetailDto>> listSessions(@RequestParam UUID farmId) {
        LOGGER.info("GET /api/scouting/sessions — listing sessions for farm {}", farmId);
        return ResponseEntity.ok(sessionService.listSessions(farmId));
    }
}

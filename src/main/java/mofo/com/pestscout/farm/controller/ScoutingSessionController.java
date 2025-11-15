package mofo.com.pestscout.farm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.farm.dto.ObservationRequest;
import mofo.com.pestscout.farm.dto.ObservationResponse;
import mofo.com.pestscout.farm.dto.ScoutingSessionRequest;
import mofo.com.pestscout.farm.dto.ScoutingSessionResponse;
import mofo.com.pestscout.farm.dto.SessionCompletionRequest;
import mofo.com.pestscout.farm.service.ScoutingSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/scouting/sessions")
@RequiredArgsConstructor
public class ScoutingSessionController {

    private final ScoutingSessionService sessionService;

    @PostMapping
    public ResponseEntity<ScoutingSessionResponse> createSession(@Valid @RequestBody ScoutingSessionRequest request) {
        ScoutingSessionResponse session = sessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PutMapping("/{sessionId}")
    public ResponseEntity<ScoutingSessionResponse> updateSession(@PathVariable UUID sessionId,
                                                                 @Valid @RequestBody ScoutingSessionRequest request) {
        return ResponseEntity.ok(sessionService.updateSession(sessionId, request));
    }

    @PostMapping("/{sessionId}/start")
    public ResponseEntity<ScoutingSessionResponse> startSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(sessionService.startSession(sessionId));
    }

    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<ScoutingSessionResponse> completeSession(@PathVariable UUID sessionId,
                                                                   @Valid @RequestBody SessionCompletionRequest request) {
        return ResponseEntity.ok(sessionService.completeSession(sessionId, request));
    }

    @PostMapping("/{sessionId}/reopen")
    public ResponseEntity<ScoutingSessionResponse> reopenSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(sessionService.reopenSession(sessionId));
    }

    @PostMapping("/{sessionId}/observations")
    public ResponseEntity<ObservationResponse> addObservation(@PathVariable UUID sessionId,
                                                              @Valid @RequestBody ObservationRequest request) {
        ObservationResponse observation = sessionService.addObservation(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(observation);
    }

    @PutMapping("/{sessionId}/observations/{observationId}")
    public ResponseEntity<ObservationResponse> updateObservation(@PathVariable UUID sessionId,
                                                                 @PathVariable UUID observationId,
                                                                 @Valid @RequestBody ObservationRequest request) {
        return ResponseEntity.ok(sessionService.updateObservation(sessionId, observationId, request));
    }

    @DeleteMapping("/{sessionId}/observations/{observationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteObservation(@PathVariable UUID sessionId,
                                  @PathVariable UUID observationId) {
        sessionService.deleteObservation(sessionId, observationId);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ScoutingSessionResponse> getSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @GetMapping
    public ResponseEntity<List<ScoutingSessionResponse>> listSessions(@RequestParam UUID farmId) {
        return ResponseEntity.ok(sessionService.listSessions(farmId));
    }
}

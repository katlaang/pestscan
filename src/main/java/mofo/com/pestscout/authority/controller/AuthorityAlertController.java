package mofo.com.pestscout.authority.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.authority.dto.AlertCoverageDto;
import mofo.com.pestscout.authority.dto.AuthorityAlertResponse;
import mofo.com.pestscout.authority.dto.AuthorityAlertUpsertRequest;
import mofo.com.pestscout.authority.service.AuthorityAlertService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/authority-alerts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AuthorityAlertController {

    private final AuthorityAlertService authorityAlertService;

    @PostMapping
    @Operation(summary = "Create authority alert")
    public ResponseEntity<AuthorityAlertResponse> createAlert(@Valid @RequestBody AuthorityAlertUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authorityAlertService.createAlert(request));
    }

    @PutMapping("/{alertId}")
    @Operation(summary = "Update authority alert")
    public ResponseEntity<AuthorityAlertResponse> updateAlert(
            @PathVariable UUID alertId,
            @Valid @RequestBody AuthorityAlertUpsertRequest request) {
        return ResponseEntity.ok(authorityAlertService.updateAlert(alertId, request));
    }

    @GetMapping("/regions")
    @Operation(summary = "List regional alerts")
    public ResponseEntity<List<AuthorityAlertResponse>> getRegionalAlerts(
            @RequestParam String country,
            @RequestParam(name = "state", required = false) List<String> states) {
        return ResponseEntity.ok(authorityAlertService.getRegionalAlerts(country, states));
    }

    @GetMapping("/emergency")
    @Operation(summary = "Emergency alert feed")
    public ResponseEntity<List<AuthorityAlertResponse>> getEmergencyFeed() {
        return ResponseEntity.ok(authorityAlertService.getEmergencyFeed());
    }

    @GetMapping("/farms/{farmId}")
    @Operation(summary = "List farm-relevant authority alerts")
    public ResponseEntity<List<AuthorityAlertResponse>> getFarmAlerts(@PathVariable UUID farmId) {
        return ResponseEntity.ok(authorityAlertService.getFarmAlerts(farmId));
    }

    @GetMapping("/map/countries")
    @Operation(summary = "North America country alert coverage")
    public ResponseEntity<List<AlertCoverageDto>> getCountryCoverage() {
        return ResponseEntity.ok(authorityAlertService.getCountryCoverage());
    }

    @GetMapping("/map/countries/{country}/states")
    @Operation(summary = "State or province alert coverage within a country")
    public ResponseEntity<List<AlertCoverageDto>> getStateCoverage(@PathVariable String country) {
        return ResponseEntity.ok(authorityAlertService.getStateCoverage(country));
    }
}

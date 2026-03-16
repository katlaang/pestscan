package mofo.com.pestscout.farm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.farm.dto.FarmLicenseHistoryResponse;
import mofo.com.pestscout.farm.dto.FarmLicenseResponse;
import mofo.com.pestscout.farm.dto.UpdateFarmLicenseRequest;
import mofo.com.pestscout.farm.service.FarmLicenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/farms/{farmId}/license")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class FarmLicenseController {

    private final FarmLicenseService farmLicenseService;

    @GetMapping
    public ResponseEntity<FarmLicenseResponse> getCurrentLicense(@PathVariable UUID farmId) {
        return ResponseEntity.ok(farmLicenseService.getCurrentLicense(farmId));
    }

    @PostMapping("/generate")
    public ResponseEntity<FarmLicenseResponse> generateLicense(@PathVariable UUID farmId) {
        return ResponseEntity.ok(farmLicenseService.generateLicense(farmId));
    }

    @PutMapping
    public ResponseEntity<FarmLicenseResponse> updateLicense(
            @PathVariable UUID farmId,
            @Valid @RequestBody UpdateFarmLicenseRequest request
    ) {
        return ResponseEntity.ok(farmLicenseService.updateLicense(farmId, request));
    }

    @GetMapping("/history")
    public ResponseEntity<List<FarmLicenseHistoryResponse>> getLicenseHistory(@PathVariable UUID farmId) {
        return ResponseEntity.ok(farmLicenseService.getLicenseHistory(farmId));
    }
}

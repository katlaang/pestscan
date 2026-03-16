package mofo.com.pestscout.farm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.service.RawDataPdfExportService;
import mofo.com.pestscout.farm.dto.FarmLicenseHistoryResponse;
import mofo.com.pestscout.farm.dto.FarmLicenseResponse;
import mofo.com.pestscout.farm.dto.UpdateFarmLicenseRequest;
import mofo.com.pestscout.farm.service.FarmLicenseService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/farms/{farmId}/license")
@RequiredArgsConstructor
public class FarmLicenseController {

    private final FarmLicenseService farmLicenseService;
    private final RawDataPdfExportService rawDataPdfExportService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<FarmLicenseResponse> getCurrentLicense(@PathVariable UUID farmId) {
        return ResponseEntity.ok(farmLicenseService.getCurrentLicense(farmId));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<FarmLicenseResponse> generateLicense(@PathVariable UUID farmId) {
        return ResponseEntity.ok(farmLicenseService.generateLicense(farmId));
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<FarmLicenseResponse> updateLicense(
            @PathVariable UUID farmId,
            @Valid @RequestBody UpdateFarmLicenseRequest request
    ) {
        return ResponseEntity.ok(farmLicenseService.updateLicense(farmId, request));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<FarmLicenseHistoryResponse>> getLicenseHistory(@PathVariable UUID farmId) {
        return ResponseEntity.ok(farmLicenseService.getLicenseHistory(farmId));
    }

    @GetMapping(value = "/raw-data-export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FARM_ADMIN','MANAGER')")
    public ResponseEntity<byte[]> downloadRawDataPdf(@PathVariable UUID farmId) {
        RawDataPdfExportService.GeneratedPdfDocument document = rawDataPdfExportService.exportFarmRawDataPdf(farmId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.fileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(document.content());
    }
}

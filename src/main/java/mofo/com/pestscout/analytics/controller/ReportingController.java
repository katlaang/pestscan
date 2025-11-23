package mofo.com.pestscout.analytics.controller;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.FarmMonthlyReportDto;
import mofo.com.pestscout.analytics.dto.ReportExportRequest;
import mofo.com.pestscout.analytics.dto.ReportExportResponse;
import mofo.com.pestscout.analytics.service.ReportExportService;
import mofo.com.pestscout.analytics.service.ReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/reports")
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;
    private final ReportExportService exportService;

    @GetMapping("/monthly")
    public ResponseEntity<FarmMonthlyReportDto> getMonthly(
            @RequestParam UUID farmId,
            @RequestParam int year,
            @RequestParam int month
    ) {
        return ResponseEntity.ok(reportingService.getMonthlyReport(farmId, year, month));
    }

    @PostMapping("/export")
    public ResponseEntity<ReportExportResponse> export(@RequestBody ReportExportRequest req) {
        return ResponseEntity.ok(exportService.export(req));
    }
}


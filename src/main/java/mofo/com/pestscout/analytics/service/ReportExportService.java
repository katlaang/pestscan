package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.ReportExportRequest;
import mofo.com.pestscout.analytics.dto.ReportExportResponse;
import mofo.com.pestscout.common.feature.FeatureAccessService;
import mofo.com.pestscout.common.feature.FeatureKey;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final ReportingService reportingService;
    private final FeatureAccessService featureAccessService;

    public ReportExportResponse export(ReportExportRequest request) {
        if (request.format() == ReportExportRequest.ExportFormat.PDF) {
            featureAccessService.assertEnabled(FeatureKey.AUTOMATED_PDF_REPORTS, request.farmId());
        }

        var report = reportingService.getMonthlyReport(
                request.farmId(),
                request.year(),
                request.month()
        );

        String fileName = "report-" + request.farmId() + "-" + request.year() + "-" + request.month();

        String url = "/downloads/" + fileName + "." + request.format().name().toLowerCase();

        return new ReportExportResponse(
                fileName,
                url
        );
    }
}

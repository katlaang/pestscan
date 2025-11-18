package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.ReportExportRequest;
import mofo.com.pestscout.analytics.dto.ReportExportResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final ReportingService reportingService;

    public ReportExportResponse export(ReportExportRequest request) {

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
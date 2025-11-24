package mofo.com.pestscout.unit.analytics.service;

import mofo.com.pestscout.analytics.dto.ReportExportRequest;
import mofo.com.pestscout.analytics.dto.ReportExportResponse;
import mofo.com.pestscout.analytics.service.ReportExportService;
import mofo.com.pestscout.analytics.service.ReportingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validates {@link ReportExportService} export metadata construction and delegation to
 * {@link ReportingService}.
 */
@ExtendWith(MockitoExtension.class)
class ReportExportServiceTest {

    @Mock
    private ReportingService reportingService;

    @InjectMocks
    private ReportExportService reportExportService;

    /**
     * Ensures the export call triggers monthly report generation and formats the response
     * filename/URL consistently with the requested format.
     */
    @Test
    void export_buildsFileNameAndUrl() {
        UUID farmId = UUID.randomUUID();
        ReportExportRequest request = new ReportExportRequest(farmId, 2024, 3, ReportExportRequest.ExportFormat.PDF);

        when(reportingService.getMonthlyReport(farmId, 2024, 3)).thenReturn(null);

        ReportExportResponse response = reportExportService.export(request);

        assertThat(response.fileName()).isEqualTo("report-" + farmId + "-2024-3");
        assertThat(response.downloadUrl()).endsWith("report-" + farmId + "-2024-3.pdf");
        verify(reportingService).getMonthlyReport(farmId, 2024, 3);
    }
}

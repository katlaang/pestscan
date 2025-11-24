package mofo.com.pestscout.analytics.service;

import mofo.com.pestscout.analytics.dto.ReportExportRequest;
import mofo.com.pestscout.analytics.dto.ReportExportResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportExportServiceTest {

    @Mock
    private ReportingService reportingService;

    @InjectMocks
    private ReportExportService reportExportService;

    @Test
    void export_buildsFileNameAndUrl() {
        UUID farmId = UUID.randomUUID();
        ReportExportRequest request = new ReportExportRequest(farmId, 2024, 3, ReportExportRequest.Format.PDF);

        when(reportingService.getMonthlyReport(farmId, 2024, 3)).thenReturn(null);

        ReportExportResponse response = reportExportService.export(request);

        assertThat(response.fileName()).isEqualTo("report-" + farmId + "-2024-3");
        assertThat(response.url()).endsWith("report-" + farmId + "-2024-3.pdf");
        verify(reportingService).getMonthlyReport(farmId, 2024, 3);
    }
}

package mofo.com.pestscout.analytics.dto;

import java.util.UUID;

public record ReportExportRequest(
        UUID farmId,
        int year,
        int month,
        ExportFormat format
) {

    public enum ExportFormat {
        PDF,
        EXCEL
    }
}

package mofo.com.pestscout.analytics.dto;

public record ReportExportResponse(
        String fileName,
        String downloadUrl
) {
}

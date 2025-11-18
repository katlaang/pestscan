package mofo.com.pestscout.analytics.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ReportSummaryDto(
        UUID farmId,
        int year,
        int month,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalSessions,
        double averageSeverity,
        int distinctPestsDetected
) {
}

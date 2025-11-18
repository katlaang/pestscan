package mofo.com.pestscout.analytics.dto;

import java.util.Map;
import java.util.UUID;

/**
 * High level weekly analytics for one farm.
 * Shared by ReportingService and AnalyticsService.
 */
public record FarmWeeklyAnalyticsDto(
        UUID farmId,
        String farmName,
        int week,
        int year,
        int bayCount,
        int benchesPerBay,
        long totalSessions,
        long completedSessions,
        long totalObservations,
        long pestObservations,
        long diseaseObservations,
        long beneficialObservations,
        Map<String, Long> severityBuckets
) {
    // severityBuckets maps SeverityLevel.name() -> count
}


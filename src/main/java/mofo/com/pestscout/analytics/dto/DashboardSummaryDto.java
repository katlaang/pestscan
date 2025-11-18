package mofo.com.pestscout.analytics.dto;

import java.util.List;
import java.util.UUID;

public record DashboardSummaryDto(
        UUID farmId,
        int totalSessions,
        int activeScouts,
        double averageSeverityThisWeek,
        double averageSeverityLastWeek,
        int pestsDetectedThisWeek,
        int treatmentsApplied,
        List<WeeklyHeatmapResponse> currentWeekHeatmap,
        List<TrendPointDto> severityTrend
) {
}


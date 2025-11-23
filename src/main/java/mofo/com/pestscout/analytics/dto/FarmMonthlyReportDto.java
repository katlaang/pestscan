package mofo.com.pestscout.analytics.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FarmMonthlyReportDto(
        UUID farmId,
        int year,
        int month,

        // Core analytics
        List<WeeklyHeatmapResponse> weeklyHeatmaps,
        List<TrendPointDto> severityTrend,
        List<PestTrendResponse> topPestTrends,

        // KPIs
        int totalSessions,
        int totalObservations,
        int activeScouts,
        double averageSeverity,
        double worstSeverity,
        int distinctPestsDetected,

        // Period info
        LocalDate periodStart,
        LocalDate periodEnd,

        // Legend
        SeverityLegendEntry legend
) {
}


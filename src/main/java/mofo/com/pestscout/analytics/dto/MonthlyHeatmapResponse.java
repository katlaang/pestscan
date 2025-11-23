package mofo.com.pestscout.analytics.dto;

import java.util.List;
import java.util.UUID;

public record MonthlyHeatmapResponse(
        UUID farmId,
        int year,
        int month,
        List<WeeklyHeatmapResponse> weeklyHeatmaps,
        List<SeverityLegendEntry> legend
) {
}



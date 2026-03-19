package mofo.com.pestscout.analytics.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HeatmapTimelineResponse(
        UUID farmId,
        String farmName,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        HeatmapRangeUnit rangeUnit,
        int rangeSize,
        List<WeeklyHeatmapResponse> weeklyHeatmaps,
        List<SeverityLegendEntry> legend
) {
}

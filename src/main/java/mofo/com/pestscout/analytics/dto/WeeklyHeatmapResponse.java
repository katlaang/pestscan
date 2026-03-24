package mofo.com.pestscout.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record WeeklyHeatmapResponse(
        int weekNumber,
        Integer year,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        List<HeatmapSectionResponse> sections
) {
    public WeeklyHeatmapResponse(
            int weekNumber,
            LocalDate rangeStart,
            LocalDate rangeEnd,
            List<HeatmapSectionResponse> sections
    ) {
        this(weekNumber, null, rangeStart, rangeEnd, sections);
    }
}


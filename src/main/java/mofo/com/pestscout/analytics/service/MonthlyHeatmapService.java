package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.MonthlyHeatmapResponse;
import mofo.com.pestscout.analytics.dto.WeeklyHeatmapResponse;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.repository.FarmRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MonthlyHeatmapService {

    private final HeatmapService heatmapService;
    private final FarmRepository farmRepository;

    /**
     * Builds a full monthly heatmap, broken down into ISO weeks.
     * For each week, a WeeklyHeatmapResponse is generated using HeatmapService.
     */
    public MonthlyHeatmapResponse getMonthlyHeatmap(UUID farmId, int year, int month) {

        // Validate farm exists
        farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        // First/last day of month
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        // ISO week fields
        WeekFields wf = WeekFields.ISO;
        int startWeek = start.get(wf.weekOfWeekBasedYear());
        int endWeek = end.get(wf.weekOfWeekBasedYear());

        List<WeeklyHeatmapResponse> weeks = new ArrayList<>();

        // Generate weekly heatmaps
        for (int week = startWeek; week <= endWeek; week++) {

            LocalDate weekStart = start.with(wf.weekOfWeekBasedYear(), week)
                    .with(wf.dayOfWeek(), 1);

            LocalDate weekEnd = weekStart.plusDays(6);

            // Clip week boundaries to month boundaries
            if (weekStart.isBefore(start)) weekStart = start;
            if (weekEnd.isAfter(end)) weekEnd = end;

            // We reuse your HeatmapService generateHeatmap(farmId, week, year)
            var weekHeatmap = heatmapService.generateHeatmap(farmId, week, year);

            // Append weekly response
            weeks.add(new WeeklyHeatmapResponse(
                    week,
                    weekStart,
                    weekEnd,
                    weekHeatmap.sections()
            ));
        }

        return new MonthlyHeatmapResponse(
                farmId,
                year,
                month,
                weeks,
                heatmapService.getSeverityLegend()
        );
    }
}

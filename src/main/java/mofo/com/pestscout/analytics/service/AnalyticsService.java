package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.FarmWeeklyAnalyticsDto;
import mofo.com.pestscout.analytics.dto.MonthlyHeatmapResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final HeatmapService heatmapService;

    /**
     * Compute weekly analytics for a farm.
     * Placeholder implementation for now.
     */
    @Transactional(readOnly = true)
    public FarmWeeklyAnalyticsDto computeWeeklyAnalytics(UUID farmId, int week, int year) {
        // TODO: compute real weekly analytics (averages, counts, etc.)
        return null;
    }

    /**
     * Compute a monthly heatmap for a farm.
     * Delegates to MonthlyHeatmapService via HeatmapService when implemented.
     */
    @Transactional(readOnly = true)
    public MonthlyHeatmapResponse computeMonthlyHeatmap(UUID farmId, int year, int month) {
        // TODO: wire to MonthlyHeatmapService or similar when ready
        return null;
    }

    /**
     * Compute weekly analytics entries for all weeks in a month.
     */
    @Transactional(readOnly = true)
    public List<FarmWeeklyAnalyticsDto> computeAllWeeklyAnalytics(UUID farmId, int year, int month) {
        // TODO: loop weeks in the month and call computeWeeklyAnalytics
        return List.of();
    }
}

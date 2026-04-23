package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.HeatmapLayerMode;
import mofo.com.pestscout.analytics.dto.HeatmapRangeUnit;
import mofo.com.pestscout.analytics.dto.HeatmapTimelineResponse;
import mofo.com.pestscout.analytics.dto.WeeklyHeatmapResponse;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HeatmapTimelineService {

    private final HeatmapService heatmapService;
    private final FarmRepository farmRepository;

    public HeatmapTimelineResponse getWeeklyTimeline(
            UUID farmId,
            HeatmapRangeUnit rangeUnit,
            int rangeSize,
            LocalDate endDate,
            HeatmapLayerMode layerMode
    ) {
        if (rangeSize < 1) {
            throw new BadRequestException("Heatmap range size must be at least 1.");
        }

        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();
        LocalDate rangeStart = resolveRangeStart(rangeUnit, rangeSize, effectiveEndDate);
        LocalDate rangeEnd = resolveRangeEnd(rangeUnit, effectiveEndDate);

        return new HeatmapTimelineResponse(
                farmId,
                farm.getName(),
                rangeStart,
                rangeEnd,
                rangeUnit,
                rangeSize,
                layerMode.apiValue(),
                buildWeeklyBuckets(farmId, rangeStart, rangeEnd, layerMode),
                heatmapService.getSeverityLegend()
        );
    }

    private LocalDate resolveRangeStart(HeatmapRangeUnit rangeUnit, int rangeSize, LocalDate endDate) {
        return switch (rangeUnit) {
            case WEEKS -> endDate.with(WeekFields.ISO.dayOfWeek(), 1).minusWeeks(rangeSize - 1L);
            case MONTHS -> endDate.withDayOfMonth(1).minusMonths(rangeSize - 1L);
        };
    }

    private LocalDate resolveRangeEnd(HeatmapRangeUnit rangeUnit, LocalDate endDate) {
        return switch (rangeUnit) {
            case WEEKS -> endDate.with(WeekFields.ISO.dayOfWeek(), 1).plusDays(6);
            case MONTHS -> endDate.withDayOfMonth(endDate.lengthOfMonth());
        };
    }

    private List<WeeklyHeatmapResponse> buildWeeklyBuckets(
            UUID farmId,
            LocalDate rangeStart,
            LocalDate rangeEnd,
            HeatmapLayerMode layerMode
    ) {
        WeekFields weekFields = WeekFields.ISO;
        LocalDate cursor = rangeStart.with(weekFields.dayOfWeek(), 1);
        List<WeeklyHeatmapResponse> weeklyHeatmaps = new ArrayList<>();

        while (!cursor.isAfter(rangeEnd)) {
            LocalDate weekStart = cursor;
            LocalDate weekEnd = weekStart.plusDays(6);
            int weekNumber = weekStart.get(weekFields.weekOfWeekBasedYear());
            int weekBasedYear = weekStart.get(weekFields.weekBasedYear());
            var weekHeatmap = heatmapService.generateHeatmap(farmId, weekNumber, weekBasedYear, layerMode);

            weeklyHeatmaps.add(new WeeklyHeatmapResponse(
                    weekNumber,
                    weekStart.isBefore(rangeStart) ? rangeStart : weekStart,
                    weekEnd.isAfter(rangeEnd) ? rangeEnd : weekEnd,
                    weekHeatmap.sections()
            ));

            cursor = cursor.plusWeeks(1);
        }

        return weeklyHeatmaps;
    }
}

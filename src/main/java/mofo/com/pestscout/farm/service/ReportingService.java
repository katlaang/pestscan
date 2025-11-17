package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.dto.FarmWeeklyAnalyticsDto;
import mofo.com.pestscout.farm.dto.HeatmapResponse;
import mofo.com.pestscout.farm.dto.ScoutingSessionDetailDto;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.ScoutingSession;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final FarmRepository farmRepository;
    private final ScoutingSessionRepository sessionRepository;
    private final HeatmapService heatmapService;
    private final AnalyticsService analyticsService;
    private final ScoutingSessionService scoutingSessionService;

    /**
     * Full report for a single session.
     * Combines the detailed session data and lets callers attach heatmap or exports.
     */
    @Transactional(readOnly = true)
    public ScoutingSessionDetailDto getSessionReport(UUID sessionId) {
        return scoutingSessionService.getSession(sessionId);
    }

    /**
     * Weekly report for a farm:
     * - sessions in the week
     * - weekly heatmap
     * - analytics summary
     */
    @Transactional(readOnly = true)
    public WeeklyFarmReportDto getWeeklyFarmReport(UUID farmId, int week, int year) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        LocalDate firstWeekReference = LocalDate.of(year, 1, 4);

        LocalDate weekStart = firstWeekReference
                .with(weekFields.weekOfWeekBasedYear(), week)
                .with(weekFields.dayOfWeek(), 1);

        LocalDate weekEnd = weekStart.plusDays(6);

        List<ScoutingSession> sessions = sessionRepository
                .findByFarmIdAndSessionDateBetween(farmId, weekStart, weekEnd);

        List<ScoutingSessionDetailDto> sessionDtos = sessions.stream()
                .map(s -> scoutingSessionService.getSession(s.getId()))
                .toList();

        HeatmapResponse heatmap = heatmapService.generateHeatmap(farmId, week, year);
        FarmWeeklyAnalyticsDto analytics = analyticsService.computeWeeklyAnalytics(farmId, week, year);

        return new WeeklyFarmReportDto(
                farm.getId(),
                farm.getName(),
                week,
                year,
                weekStart,
                weekEnd,
                sessionDtos,
                heatmap,
                analytics
        );
    }

    /**
     * Wrapper DTO for the weekly report.
     */
    public record WeeklyFarmReportDto(
            UUID farmId,
            String farmName,
            int week,
            int year,
            LocalDate weekStart,
            LocalDate weekEnd,
            List<ScoutingSessionDetailDto> sessions,
            HeatmapResponse heatmap,
            FarmWeeklyAnalyticsDto analytics
    ) {
    }
}


package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.DashboardSummaryDto;
import mofo.com.pestscout.analytics.dto.WeeklyHeatmapResponse;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ScoutingSessionRepository sessionRepo;
    private final FarmRepository farmRepo;
    private final HeatmapService heatmapService;
    private final TrendAnalysisService trendService;

    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboard(UUID farmId) {

        farmRepo.findById(farmId)
                .orElseThrow(() -> new RuntimeException("Farm not found"));

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);

        var allSessions = sessionRepo.findByFarmId(farmId);
        int totalSessions = allSessions.size();

        // Average severity comparisons
        double avgSeverityThisWeek = calculateAverageSeverity(farmId, weekStart, today);
        double avgSeverityLastWeek =
                calculateAverageSeverity(farmId, weekStart.minusDays(7), weekStart.minusDays(1));

        int activeScouts = (int) sessionRepo.findByFarmIdAndSessionDateBetween(farmId, weekStart, today).stream()
                .filter(s -> s.getScout() != null)
                .map(s -> s.getScout().getId())
                .distinct()
                .count();

        int treatmentsApplied = sessionRepo.findByFarmIdAndSessionDateBetween(farmId, weekStart, today).stream()
                .filter(s -> s.getRecommendations() != null)
                .mapToInt(s -> s.getRecommendations().size())
                .sum();

        // ISO week number
        int weekNumber = today.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = today.getYear();

        // Weekly heatmap generated using correct signature
        var weeklyHeatmap = heatmapService.generateHeatmap(farmId, weekNumber, year);

        // Last 30-day trend for default pest
        var trend = trendService.getPestTrend(
                farmId,
                "thrips",                 // default until UI selects species
                today.minusDays(30),
                today
        );

        return new DashboardSummaryDto(
                farmId,
                totalSessions,
                activeScouts,
                avgSeverityThisWeek,
                avgSeverityLastWeek,
                countPestsDetected(farmId, weekStart, today),
                treatmentsApplied,
                List.of(new WeeklyHeatmapResponse(
                        weekNumber,
                        weekStart,
                        today,
                        weeklyHeatmap.sections()
                )),
                trend.points()
        );
    }

    private int countPestsDetected(UUID farmId, LocalDate start, LocalDate end) {
        return sessionRepo.findByFarmIdAndSessionDateBetween(farmId, start, end).stream()
                .flatMap(s -> s.getObservations().stream())
                .mapToInt(o -> o.getCount() == null ? 0 : o.getCount())
                .sum();
    }

    private double calculateAverageSeverity(UUID farmId, LocalDate from, LocalDate to) {
        var sessions = sessionRepo.findByFarmIdAndSessionDateBetween(farmId, from, to);
        if (sessions.isEmpty()) return 0;

        int severitySum = sessions.stream()
                .flatMap(s -> s.getObservations().stream())
                .mapToInt(o -> o.getCount() == null ? 0 : o.getCount())
                .sum();

        return (double) severitySum / sessions.size();
    }
}

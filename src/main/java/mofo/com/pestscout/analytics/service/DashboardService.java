package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.analytics.dto.*;
import mofo.com.pestscout.farm.dto.FarmResponse;
import mofo.com.pestscout.farm.service.FarmService;
import mofo.com.pestscout.scouting.model.ScoutingSession;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ScoutingSessionRepository sessionRepo;
    private final AnalyticsAccessService analyticsAccessService;
    private final HeatmapService heatmapService;
    private final TrendAnalysisService trendService;
    private final FarmService farmService;

    @Transactional(readOnly = true)
    public DashboardOverviewDto getDashboardOverview() {
        LocalDate today = LocalDate.now();
        List<DashboardFarmCardDto> farms = farmService.listFarms().stream()
                .map(farm -> toDashboardFarmCard(farm, today))
                .toList();

        List<LicenseAlertSummaryDto> licenseAlerts = farms.stream()
                .filter(farm -> farm.licenseExpiryDate() != null)
                .filter(farm -> farm.daysUntilLicenseExpiry() != null)
                .filter(farm -> farm.daysUntilLicenseExpiry() <= 30)
                .map(farm -> new LicenseAlertSummaryDto(
                        farm.farmId(),
                        farm.farmName(),
                        farm.licenseExpiryDate(),
                        farm.daysUntilLicenseExpiry(),
                        farm.daysUntilLicenseExpiry() < 0 ? "EXPIRED" : "EXPIRING_SOON"
                ))
                .sorted(Comparator.comparingLong(LicenseAlertSummaryDto::daysUntilExpiry))
                .toList();

        return new DashboardOverviewDto(farms.size(), farms, licenseAlerts);
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboard(UUID farmId) {
        analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);

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

        var weeklyHeatmap = resolveDashboardHeatmap(farmId, allSessions, today);
        int weekNumber = weeklyHeatmap.week();

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
                        weeklyHeatmap.year(),
                        weekStart,
                        today,
                        weeklyHeatmap.sections()
                )),
                trend.points()
        );
    }

    @Transactional(readOnly = true)
    public HeatmapResponse getDashboardHeatmap(UUID farmId) {
        analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);
        return resolveDashboardHeatmap(farmId, sessionRepo.findByFarmId(farmId), LocalDate.now());
    }

    private HeatmapResponse resolveDashboardHeatmap(UUID farmId, List<ScoutingSession> sessions, LocalDate today) {
        WeekFields weekFields = WeekFields.ISO;
        int requestedWeek = today.get(weekFields.weekOfWeekBasedYear());
        int requestedYear = today.get(weekFields.weekBasedYear());

        HeatmapResponse heatmap = heatmapService.generateHeatmap(farmId, requestedWeek, requestedYear);
        if (!isEmptyHeatmap(heatmap)) {
            return heatmap;
        }

        return sessions.stream()
                .map(ScoutingSession::getSessionDate)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .map(date -> {
                    int week = date.get(weekFields.weekOfWeekBasedYear());
                    int year = date.get(weekFields.weekBasedYear());
                    return heatmapService.generateHeatmap(farmId, week, year);
                })
                .filter(candidate -> !isEmptyHeatmap(candidate))
                .findFirst()
                .orElse(heatmap);
    }

    private boolean isEmptyHeatmap(HeatmapResponse heatmap) {
        return heatmap.cells().isEmpty() && heatmap.sections().isEmpty();
    }

    private DashboardFarmCardDto toDashboardFarmCard(FarmResponse farm, LocalDate today) {
        Long daysUntilExpiry = farm.licenseExpiryDate() != null
                ? ChronoUnit.DAYS.between(today, farm.licenseExpiryDate())
                : null;

        return new DashboardFarmCardDto(
                farm.id(),
                farm.farmTag(),
                farm.name(),
                farm.licenseExpiryDate(),
                daysUntilExpiry,
                farm.accessLocked()
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

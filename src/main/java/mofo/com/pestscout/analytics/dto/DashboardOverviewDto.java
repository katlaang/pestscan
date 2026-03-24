package mofo.com.pestscout.analytics.dto;

import java.util.List;

public record DashboardOverviewDto(
        int farmCount,
        List<DashboardFarmCardDto> farms,
        List<LicenseAlertSummaryDto> licenseAlerts
) {
}

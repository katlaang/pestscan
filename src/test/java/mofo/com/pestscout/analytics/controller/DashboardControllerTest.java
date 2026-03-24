package mofo.com.pestscout.analytics.controller;

import mofo.com.pestscout.analytics.dto.*;
import mofo.com.pestscout.analytics.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @Test
    void getDashboardDelegatesToService() {
        UUID farmId = UUID.randomUUID();
        DashboardSummaryDto summary = new DashboardSummaryDto(
                farmId,
                1,
                1,
                1.0,
                0.5,
                2,
                0,
                List.of(new WeeklyHeatmapResponse(1, LocalDate.now(), LocalDate.now(), List.of())),
                List.of()
        );

        when(dashboardService.getDashboard(farmId)).thenReturn(summary);

        DashboardController controller = new DashboardController(dashboardService);

        DashboardSummaryDto result = controller.getDashboard(farmId);

        assertThat(result).isEqualTo(summary);
    }

    @Test
    void getDashboardOverviewDelegatesToService() {
        DashboardOverviewDto overview = new DashboardOverviewDto(
                1,
                List.of(new DashboardFarmCardDto(UUID.randomUUID(), "US-ALPHA", "Alpha", LocalDate.now().plusDays(7), 7L, false)),
                List.of(new LicenseAlertSummaryDto(UUID.randomUUID(), "Alpha", LocalDate.now().plusDays(7), 7L, "EXPIRING_SOON"))
        );

        when(dashboardService.getDashboardOverview()).thenReturn(overview);

        DashboardController controller = new DashboardController(dashboardService);

        DashboardOverviewDto result = controller.getDashboardOverview();

        assertThat(result).isEqualTo(overview);
    }
}

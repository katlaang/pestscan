package mofo.com.pestscout.analytics.controller;

import mofo.com.pestscout.analytics.dto.DashboardSummaryDto;
import mofo.com.pestscout.analytics.dto.WeeklyHeatmapResponse;
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
}

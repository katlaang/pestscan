package mofo.com.pestscout.analytics.controller;

import mofo.com.pestscout.analytics.dto.DashboardDto;
import mofo.com.pestscout.analytics.service.DashboardAggregatorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsDashboardControllerTest {

    @Mock
    private DashboardAggregatorService dashboardAggregatorService;

    @Test
    void getFullDashboardDelegatesToAggregator() {
        UUID farmId = UUID.randomUUID();
        DashboardDto dto = new DashboardDto(null, null, null, null, null, null, null, null, null, null);

        when(dashboardAggregatorService.getFullDashboard(farmId)).thenReturn(dto);

        AnalyticsDashboardController controller = new AnalyticsDashboardController(dashboardAggregatorService);

        DashboardDto result = controller.getFullDashboard(farmId);

        assertThat(result).isEqualTo(dto);
    }
}
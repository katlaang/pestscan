package mofo.com.pestscout.unit.analytics.service;

import mofo.com.pestscout.analytics.dto.HeatmapResponse;
import mofo.com.pestscout.analytics.dto.MonthlyHeatmapResponse;
import mofo.com.pestscout.analytics.dto.WeeklyHeatmapResponse;
import mofo.com.pestscout.analytics.service.HeatmapService;
import mofo.com.pestscout.analytics.service.MonthlyHeatmapService;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyHeatmapServiceTest {

    @Mock
    private HeatmapService heatmapService;

    @Mock
    private FarmRepository farmRepository;

    @Test
    void buildsWeeklyHeatmapsForMonth() {
        UUID farmId = UUID.randomUUID();
        Farm farm = Farm.builder().id(farmId).name("Test Farm").build();
        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));

        HeatmapResponse heatmap = HeatmapResponse.builder()
                .farmId(farmId)
                .farmName("Test Farm")
                .week(1)
                .year(2024)
                .bayCount(2)
                .benchesPerBay(2)
                .cells(List.of())
                .sections(List.of())
                .severityLegend(List.of())
                .build();

        when(heatmapService.generateHeatmap(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(heatmap);
        when(heatmapService.getSeverityLegend()).thenReturn(List.of());

        MonthlyHeatmapService service = new MonthlyHeatmapService(heatmapService, farmRepository);

        MonthlyHeatmapResponse response = service.getMonthlyHeatmap(farmId, 2024, 2);

        assertThat(response.farmId()).isEqualTo(farmId);
        assertThat(response.year()).isEqualTo(2024);
        assertThat(response.month()).isEqualTo(2);
        assertThat(response.weeklyHeatmaps()).isNotEmpty();

        WeeklyHeatmapResponse firstWeek = response.weeklyHeatmaps().getFirst();
        assertThat(firstWeek.weekNumber()).isEqualTo(5);                        // was .week()
        assertThat(firstWeek.rangeStart()).isEqualTo(LocalDate.of(2024, 2, 1)); // was .startDate()
        assertThat(firstWeek.rangeEnd()).isAfterOrEqualTo(firstWeek.rangeStart()); // was .endDate()
    }

}

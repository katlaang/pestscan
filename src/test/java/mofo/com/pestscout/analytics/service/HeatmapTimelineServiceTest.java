package mofo.com.pestscout.analytics.service;

import mofo.com.pestscout.analytics.dto.HeatmapLayerMode;
import mofo.com.pestscout.analytics.dto.HeatmapRangeUnit;
import mofo.com.pestscout.analytics.dto.HeatmapResponse;
import mofo.com.pestscout.analytics.dto.HeatmapTimelineResponse;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeatmapTimelineServiceTest {

    @Mock
    private HeatmapService heatmapService;

    @Mock
    private FarmRepository farmRepository;

    @Test
    void buildsWeeklyTimelineForLastFiveWeeks() {
        UUID farmId = UUID.randomUUID();
        Farm farm = Farm.builder().id(farmId).name("Test Farm").build();
        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));

        HeatmapResponse heatmap = HeatmapResponse.builder()
                .farmId(farmId)
                .farmName("Test Farm")
                .week(1)
                .year(2026)
                .bayCount(2)
                .benchesPerBay(2)
                .cells(List.of())
                .sections(List.of())
                .layerMode("pests")
                .severityLegend(List.of())
                .build();

        when(heatmapService.generateHeatmap(any(), anyInt(), anyInt(), any())).thenReturn(heatmap);
        when(heatmapService.getSeverityLegend()).thenReturn(List.of());

        HeatmapTimelineService service = new HeatmapTimelineService(heatmapService, farmRepository);
        HeatmapTimelineResponse response = service.getWeeklyTimeline(
                farmId,
                HeatmapRangeUnit.WEEKS,
                5,
                LocalDate.of(2026, 3, 18),
                HeatmapLayerMode.PESTS
        );

        assertThat(response.farmId()).isEqualTo(farmId);
        assertThat(response.rangeUnit()).isEqualTo(HeatmapRangeUnit.WEEKS);
        assertThat(response.rangeSize()).isEqualTo(5);
        assertThat(response.layerMode()).isEqualTo("pests");
        assertThat(response.weeklyHeatmaps()).hasSize(5);
        assertThat(response.rangeStart()).isEqualTo(LocalDate.of(2026, 2, 16));
        assertThat(response.rangeEnd()).isEqualTo(LocalDate.of(2026, 3, 22));
    }

    @Test
    void buildsWeeklyTimelineForThreeMonths() {
        UUID farmId = UUID.randomUUID();
        Farm farm = Farm.builder().id(farmId).name("Test Farm").build();
        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));

        HeatmapResponse heatmap = HeatmapResponse.builder()
                .farmId(farmId)
                .farmName("Test Farm")
                .week(1)
                .year(2026)
                .bayCount(2)
                .benchesPerBay(2)
                .cells(List.of())
                .sections(List.of())
                .layerMode("all")
                .severityLegend(List.of())
                .build();

        when(heatmapService.generateHeatmap(any(), anyInt(), anyInt(), any())).thenReturn(heatmap);
        when(heatmapService.getSeverityLegend()).thenReturn(List.of());

        HeatmapTimelineService service = new HeatmapTimelineService(heatmapService, farmRepository);
        HeatmapTimelineResponse response = service.getWeeklyTimeline(
                farmId,
                HeatmapRangeUnit.MONTHS,
                3,
                LocalDate.of(2026, 3, 18),
                HeatmapLayerMode.ALL
        );

        assertThat(response.rangeStart()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(response.rangeEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(response.weeklyHeatmaps()).isNotEmpty();
    }
}

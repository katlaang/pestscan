package mofo.com.pestscout.analytics.controller;

import mofo.com.pestscout.analytics.dto.HeatmapLayerMode;
import mofo.com.pestscout.analytics.dto.HeatmapRangeUnit;
import mofo.com.pestscout.analytics.dto.HeatmapTimelineResponse;
import mofo.com.pestscout.analytics.service.HeatmapTimelineService;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HeatmapTimelineController.class)
@AutoConfigureMockMvc(addFilters = false)
class HeatmapTimelineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HeatmapTimelineService heatmapTimelineService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void returnsWeeklyTimeline() throws Exception {
        UUID farmId = UUID.randomUUID();
        HeatmapTimelineResponse response = new HeatmapTimelineResponse(
                farmId,
                "Test Farm",
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 3, 22),
                HeatmapRangeUnit.WEEKS,
                5,
                "diseases",
                List.of(),
                List.of()
        );
        when(heatmapTimelineService.getWeeklyTimeline(
                farmId,
                HeatmapRangeUnit.WEEKS,
                5,
                LocalDate.of(2026, 3, 18),
                HeatmapLayerMode.DISEASES
        )).thenReturn(response);

        mockMvc.perform(get("/api/analytics/heatmap/timeline")
                        .param("farmId", farmId.toString())
                        .param("rangeUnit", "WEEKS")
                        .param("rangeSize", "5")
                        .param("endDate", "2026-03-18")
                        .param("mode", "diseases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farmId.toString()))
                .andExpect(jsonPath("$.rangeUnit").value("WEEKS"))
                .andExpect(jsonPath("$.rangeSize").value(5))
                .andExpect(jsonPath("$.layerMode").value("diseases"));
    }
}

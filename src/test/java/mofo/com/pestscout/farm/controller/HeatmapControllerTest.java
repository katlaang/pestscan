package mofo.com.pestscout.farm.controller;

import mofo.com.pestscout.analytics.dto.HeatmapResponse;
import mofo.com.pestscout.analytics.service.HeatmapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HeatmapController.class)
@AutoConfigureMockMvc(addFilters = false)
class HeatmapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HeatmapService heatmapService;

    @Test
    void returnsHeatmapFromService() throws Exception {
        UUID farmId = UUID.randomUUID();
        HeatmapResponse response = HeatmapResponse.builder()
                .farmId(farmId)
                .farmName("Farm")
                .week(12)
                .year(2024)
                .bayCount(1)
                .benchesPerBay(1)
                .cells(List.of())
                .sections(List.of())
                .severityLegend(List.of())
                .build();

        when(heatmapService.generateHeatmap(farmId, 12, 2024)).thenReturn(response);

        mockMvc.perform(get("/api/farms/" + farmId + "/heatmap")
                        .param("week", "12")
                        .param("year", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farmId.toString()))
                .andExpect(jsonPath("$.week").value(12));
    }
}

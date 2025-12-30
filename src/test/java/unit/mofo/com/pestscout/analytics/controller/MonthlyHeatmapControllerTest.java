package mofo.com.pestscout.analytics.controller;

import mofo.com.pestscout.analytics.dto.MonthlyHeatmapResponse;
import mofo.com.pestscout.analytics.service.MonthlyHeatmapService;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
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

@WebMvcTest(controllers = MonthlyHeatmapController.class)
@AutoConfigureMockMvc(addFilters = false)
class MonthlyHeatmapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthlyHeatmapService heatmapService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void returnsMonthlyHeatmap() throws Exception {
        UUID farmId = UUID.randomUUID();
        MonthlyHeatmapResponse response = new MonthlyHeatmapResponse(farmId, 2024, 4, List.of(), List.of());
        when(heatmapService.getMonthlyHeatmap(farmId, 2024, 4)).thenReturn(response);

        mockMvc.perform(get("/api/analytics/heatmap/monthly")
                        .param("farmId", farmId.toString())
                        .param("year", "2024")
                        .param("month", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farmId.toString()))
                .andExpect(jsonPath("$.year").value(2024));
    }
}

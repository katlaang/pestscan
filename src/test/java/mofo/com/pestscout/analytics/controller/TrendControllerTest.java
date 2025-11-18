package mofo.com.pestscout.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.analytics.dto.PestTrendResponse;
import mofo.com.pestscout.analytics.dto.TrendPointDto;
import mofo.com.pestscout.analytics.service.TrendAnalysisService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TrendController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TrendAnalysisService trendAnalysisService;

    @Test
    void returnsTrendResponseFromService() throws Exception {
        UUID farmId = UUID.randomUUID();
        PestTrendResponse response = new PestTrendResponse(
                farmId,
                "THRIPS",
                List.of(new TrendPointDto(LocalDate.of(2024, 1, 1), 4))
        );

        when(trendAnalysisService.getPestTrend(eq(farmId), eq("THRIPS"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/analytics/trend/pest")
                        .param("farmId", farmId.toString())
                        .param("species", "THRIPS")
                        .param("from", "2024-01-01")
                        .param("to", "2024-02-01"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));

        Mockito.verify(trendAnalysisService)
                .getPestTrend(eq(farmId), eq("THRIPS"), eq(LocalDate.parse("2024-01-01")), eq(LocalDate.parse("2024-02-01")));
    }
}

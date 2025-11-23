package mofo.com.pestscout.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.analytics.dto.FarmMonthlyReportDto;
import mofo.com.pestscout.analytics.dto.ReportExportRequest;
import mofo.com.pestscout.analytics.dto.ReportExportResponse;
import mofo.com.pestscout.analytics.service.ReportExportService;
import mofo.com.pestscout.analytics.service.ReportingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportingController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReportingService reportingService;

    @MockitoBean
    private ReportExportService exportService;

    @Test
    void returnsMonthlyReport() throws Exception {
        UUID farmId = UUID.randomUUID();
        FarmMonthlyReportDto dto = new FarmMonthlyReportDto(
                farmId,
                2024,
                3,
                List.of(),
                List.of(),
                List.of(),
                0,
                0,
                0,
                0.0,
                0,
                0,
                LocalDate.of(2024, 3, 1),
                LocalDate.of(2024, 3, 31),
                null
        );

        when(reportingService.getMonthlyReport(farmId, 2024, 3)).thenReturn(dto);

        mockMvc.perform(get("/api/analytics/reports/monthly")
                        .param("farmId", farmId.toString())
                        .param("year", "2024")
                        .param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farmId.toString()))
                .andExpect(jsonPath("$.month").value(3));
    }

    @Test
    void exportsReport() throws Exception {
        ReportExportRequest request = new ReportExportRequest(UUID.randomUUID(), 2024, 3, "pdf");
        ReportExportResponse response = new ReportExportResponse("url", "pdf", 1234L);
        when(exportService.export(any(ReportExportRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/analytics/reports/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("pdf"))
                .andExpect(jsonPath("$.downloadUrl").value("url"));
    }
}

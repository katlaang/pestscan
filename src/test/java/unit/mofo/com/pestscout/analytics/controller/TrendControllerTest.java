package mofo.com.pestscout.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.analytics.dto.PestTrendResponse;
import mofo.com.pestscout.analytics.dto.TrendPointDto;
import mofo.com.pestscout.analytics.dto.WeeklyPestTrendDto;
import mofo.com.pestscout.analytics.service.TrendAnalysisService;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TrendController.class)
@DisplayName("TrendController Unit Tests")
class TrendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TrendAnalysisService trendAnalysisService;


    private UUID farmId;
    private PestTrendResponse trendResponse;
    private List<WeeklyPestTrendDto> weeklyTrends;

    @BeforeEach
    void setUp() {
        farmId = UUID.randomUUID();

        // Setup trend response
        List<TrendPointDto> points = Arrays.asList(
                new TrendPointDto(LocalDate.of(2024, 1, 1), 5),
                new TrendPointDto(LocalDate.of(2024, 1, 8), 8),
                new TrendPointDto(LocalDate.of(2024, 1, 15), 3)
        );

        trendResponse = new PestTrendResponse(
                farmId,
                "THRIPS",
                points
        );

        // Setup weekly trends
        weeklyTrends = Arrays.asList(
                new WeeklyPestTrendDto("W1", 5, 3, 2, 1, 0, 0, 0),
                new WeeklyPestTrendDto("W2", 8, 4, 1, 2, 1, 0, 0)
        );
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/analytics/trend/pest - Success")
    void getPestTrend_WithValidParameters_ReturnsTrendData() throws Exception {
        // Arrange
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        when(trendAnalysisService.getPestTrend(
                eq(farmId),
                eq("THRIPS"),
                eq(from),
                eq(to)
        )).thenReturn(trendResponse);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/trend/pest")
                        .param("farmId", farmId.toString())
                        .param("species", "THRIPS")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.farmId").value(farmId.toString()))
                .andExpect(jsonPath("$.speciesCode").value("THRIPS"))
                .andExpect(jsonPath("$.points").isArray())
                .andExpect(jsonPath("$.points.length()").value(3))
                .andExpect(jsonPath("$.points[0].severity").value(5))
                .andExpect(jsonPath("$.points[1].severity").value(8))
                .andExpect(jsonPath("$.points[2].severity").value(3));

        verify(trendAnalysisService).getPestTrend(farmId, "THRIPS", from, to);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/analytics/trend/pest - Missing required parameters")
    void getPestTrend_WithMissingParameters_ReturnsBadRequest() throws Exception {
        // Act & Assert - Missing farmId
        mockMvc.perform(get("/api/analytics/trend/pest")
                        .param("species", "THRIPS")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isBadRequest());

        // Act & Assert - Missing species
        mockMvc.perform(get("/api/analytics/trend/pest")
                        .param("farmId", farmId.toString())
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isBadRequest());

        verify(trendAnalysisService, never()).getPestTrend(any(), any(), any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/analytics/trend/pest - Invalid date format")
    void getPestTrend_WithInvalidDateFormat_ReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/analytics/trend/pest")
                        .param("farmId", farmId.toString())
                        .param("species", "THRIPS")
                        .param("from", "invalid-date")
                        .param("to", "2024-01-31"))
                .andExpect(status().isBadRequest());

        verify(trendAnalysisService, never()).getPestTrend(any(), any(), any(), any());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/analytics/trend/pest - Empty results")
    void getPestTrend_WithNoData_ReturnsEmptyList() throws Exception {
        // Arrange
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        PestTrendResponse emptyResponse = new PestTrendResponse(
                farmId,
                "THRIPS",
                List.of()
        );

        when(trendAnalysisService.getPestTrend(
                eq(farmId),
                eq("THRIPS"),
                eq(from),
                eq(to)
        )).thenReturn(emptyResponse);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/trend/pest")
                        .param("farmId", farmId.toString())
                        .param("species", "THRIPS")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").isArray())
                .andExpect(jsonPath("$.points").isEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/analytics/trend/weekly - Success")
    void getWeeklyPestTrends_WithValidFarmId_ReturnsWeeklyData() throws Exception {
        // Arrange
        when(trendAnalysisService.getWeeklyPestTrends(farmId))
                .thenReturn(weeklyTrends);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/trend/weekly")
                        .param("farmId", farmId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].week").value("W1"))
                .andExpect(jsonPath("$[0].thrips").value(5))
                .andExpect(jsonPath("$[0].redSpider").value(3))
                .andExpect(jsonPath("$[1].week").value("W2"))
                .andExpect(jsonPath("$[1].thrips").value(8));

        verify(trendAnalysisService).getWeeklyPestTrends(farmId);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/analytics/trend/weekly - Missing farmId")
    void getWeeklyPestTrends_WithoutFarmId_ReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/analytics/trend/weekly"))
                .andExpect(status().isBadRequest());

        verify(trendAnalysisService, never()).getWeeklyPestTrends(any());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/analytics/trend/weekly - Invalid farmId format")
    void getWeeklyPestTrends_WithInvalidFarmIdFormat_ReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/analytics/trend/weekly")
                        .param("farmId", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verify(trendAnalysisService, never()).getWeeklyPestTrends(any());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/analytics/trend/pest - Different species codes")
    void getPestTrend_WithDifferentSpecies_ReturnsCorrectData() throws Exception {
        // Arrange
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        PestTrendResponse whiteflyResponse = new PestTrendResponse(
                farmId,
                "WHITEFLIES",
                List.of(new TrendPointDto(LocalDate.of(2024, 1, 1), 10))
        );

        when(trendAnalysisService.getPestTrend(
                eq(farmId),
                eq("WHITEFLIES"),
                eq(from),
                eq(to)
        )).thenReturn(whiteflyResponse);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/trend/pest")
                        .param("farmId", farmId.toString())
                        .param("species", "WHITEFLIES")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speciesCode").value("WHITEFLIES"))
                .andExpect(jsonPath("$.points[0].severity").value(10));

        verify(trendAnalysisService).getPestTrend(farmId, "WHITEFLIES", from, to);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/analytics/trend/pest - Date range validation")
    void getPestTrend_WithValidDateRange_ProcessesCorrectly() throws Exception {
        // Arrange
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);

        when(trendAnalysisService.getPestTrend(
                eq(farmId),
                eq("THRIPS"),
                eq(from),
                eq(to)
        )).thenReturn(trendResponse);

        // Act & Assert
        mockMvc.perform(get("/api/analytics/trend/pest")
                        .param("farmId", farmId.toString())
                        .param("species", "THRIPS")
                        .param("from", "2024-01-01")
                        .param("to", "2024-12-31"))
                .andExpect(status().isOk());

        verify(trendAnalysisService).getPestTrend(farmId, "THRIPS", from, to);
    }

    @Test
    @DisplayName("GET /api/analytics/trend/pest - Unauthorized without authentication")
    void getPestTrend_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/analytics/trend/pest")
                        .param("farmId", farmId.toString())
                        .param("species", "THRIPS")
                        .param("from", "2024-01-01")
                        .param("to", "2024-01-31"))
                .andExpect(status().isUnauthorized());

        verify(trendAnalysisService, never()).getPestTrend(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/analytics/trend/weekly - Unauthorized without authentication")
    void getWeeklyPestTrends_WithoutAuthentication_ReturnsUnauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/analytics/trend/weekly")
                        .param("farmId", farmId.toString()))
                .andExpect(status().isUnauthorized());

        verify(trendAnalysisService, never()).getWeeklyPestTrends(any());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/analytics/trend/pest - All pest species types")
    void getPestTrend_WithAllPestTypes_WorksForEach() throws Exception {
        // Test with different pest types
        String[] pestTypes = {"THRIPS", "WHITEFLIES", "RED_SPIDER_MITE", "MEALYBUGS"};
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        for (String pestType : pestTypes) {
            PestTrendResponse response = new PestTrendResponse(
                    farmId,
                    pestType,
                    List.of(new TrendPointDto(LocalDate.of(2024, 1, 1), 5))
            );

            when(trendAnalysisService.getPestTrend(eq(farmId), eq(pestType), eq(from), eq(to)))
                    .thenReturn(response);

            mockMvc.perform(get("/api/analytics/trend/pest")
                            .param("farmId", farmId.toString())
                            .param("species", pestType)
                            .param("from", "2024-01-01")
                            .param("to", "2024-01-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.speciesCode").value(pestType));
        }

        verify(trendAnalysisService, times(pestTypes.length))
                .getPestTrend(any(UUID.class), anyString(), any(LocalDate.class), any(LocalDate.class));
    }

    @TestConfiguration
    static class TrendTestConfig {
        @Bean
        JwtTokenProvider jwtTokenProvider() {
            // mock to satisfy JwtAuthenticationFilter dependency
            return Mockito.mock(JwtTokenProvider.class);
        }
    }
}

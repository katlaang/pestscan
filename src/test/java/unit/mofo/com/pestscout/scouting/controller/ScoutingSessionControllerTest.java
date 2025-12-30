package mofo.com.pestscout.scouting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.analytics.dto.SessionTargetRequest;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.scouting.dto.*;
import mofo.com.pestscout.scouting.model.SessionStatus;
import mofo.com.pestscout.scouting.service.ScoutingSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ScoutingSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScoutingSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ScoutingSessionService sessionService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void createsSession() throws Exception {
        UUID farmId = UUID.randomUUID();

        SessionTargetRequest targetRequest = new SessionTargetRequest(
                UUID.randomUUID(), // greenhouseId
                null,              // fieldBlockId
                true,
                true,
                List.of(),
                List.of()
        );

        CreateScoutingSessionRequest request = new CreateScoutingSessionRequest(
                farmId,
                List.of(targetRequest),
                LocalDate.of(2024, 3, 5),
                10,
                "Tomatoes",
                "Cherry",
                new BigDecimal("22.5"),
                new BigDecimal("65.0"),
                LocalTime.NOON,
                "Clear",
                "Notes"
        );

        ScoutingSessionDetailDto detail = new ScoutingSessionDetailDto(
                UUID.randomUUID(),
                1L,
                farmId,
                request.sessionDate(),
                request.weekNumber(),
                SessionStatus.DRAFT,
                null,
                null,
                request.crop(),
                request.variety(),
                request.temperatureCelsius(),
                request.relativeHumidityPercent(),
                request.observationTime(),
                request.weatherNotes(),
                request.notes(),
                null,
                null,
                LocalDateTime.now(),
                false,
                List.of(
                        new ScoutingSessionSectionDto(
                                UUID.randomUUID(),
                                targetRequest.greenhouseId(),
                                targetRequest.fieldBlockId(),
                                targetRequest.includeAllBays(),
                                targetRequest.includeAllBenches(),
                                List.copyOf(targetRequest.bayTags()),
                                List.copyOf(targetRequest.benchTags()),
                                List.<ScoutingObservationDto>of()
                        )
                ),
                List.<RecommendationEntryDto>of()
        );

        when(sessionService.createSession(any(CreateScoutingSessionRequest.class)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/scouting/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(farmId.toString()));
    }

    @Test
    void syncsSessionsWithParams() throws Exception {
        UUID farmId = UUID.randomUUID();
        LocalDateTime since = LocalDateTime.of(2024, 3, 1, 0, 0);
        ScoutingSyncResponse sync = new ScoutingSyncResponse(List.of(), List.of());

        when(sessionService.syncChanges(farmId, since, true)).thenReturn(sync);

        mockMvc.perform(get("/api/scouting/sessions/sync")
                        .param("farmId", farmId.toString())
                        .param("since", since.toString())
                        .param("includeDeleted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray());
    }
}

package mofo.com.pestscout.scouting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.scouting.dto.*;
import mofo.com.pestscout.scouting.model.ScoutingTargetType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
        CreateScoutingSessionRequest request = new CreateScoutingSessionRequest(
                UUID.randomUUID(),
                LocalDate.of(2024, 3, 5),
                "Tomatoes",
                "Cherry",
                "Notes",
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(new ScoutingSessionSectionDto(
                        UUID.randomUUID(),
                        ScoutingTargetType.GREENHOUSE,
                        "G1",
                        1,
                        1,
                        List.of()
                ))
        );

        ScoutingSessionDetailDto detail = new ScoutingSessionDetailDto(
                UUID.randomUUID(),
                1L,
                request.farmId(),
                request.sessionDate(),
                10,
                SessionStatus.SCHEDULED,
                request.managerId(),
                request.scoutId(),
                request.crop(),
                request.variety(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalTime.NOON,
                "Clear",
                request.notes(),
                null,
                null,
                LocalDateTime.now(),
                false,
                request.sections(),
                List.of()
        );

        when(sessionService.createSession(any(CreateScoutingSessionRequest.class))).thenReturn(detail);

        mockMvc.perform(post("/api/scouting/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(request.farmId().toString()));
    }

    @Test
    void syncsSessionsWithParams() throws Exception {
        UUID farmId = UUID.randomUUID();
        LocalDateTime since = LocalDateTime.of(2024, 3, 1, 0, 0);
        ScoutingSyncResponse sync = new ScoutingSyncResponse(List.of(), List.of(), List.of());
        when(sessionService.syncChanges(farmId, since, true)).thenReturn(sync);

        mockMvc.perform(get("/api/scouting/sessions/sync")
                        .param("farmId", farmId.toString())
                        .param("since", since.toString())
                        .param("includeDeleted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray());
    }
}

package mofo.com.pestscout.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.TestPestscoutApplication;
import mofo.com.pestscout.analytics.dto.SessionTargetRequest;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.farm.model.*;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.GreenhouseRepository;
import mofo.com.pestscout.scouting.dto.BulkUpsertObservationsRequest;
import mofo.com.pestscout.scouting.dto.CompleteSessionRequest;
import mofo.com.pestscout.scouting.dto.CreateScoutingSessionRequest;
import mofo.com.pestscout.scouting.dto.UpsertObservationRequest;
import mofo.com.pestscout.scouting.model.SpeciesCode;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionTargetRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = TestPestscoutApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ScoutingSessionIntegrationTest {

    private static final String ADMIN_EMAIL = "owner@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FarmRepository farmRepository;

    @Autowired
    private GreenhouseRepository greenhouseRepository;

    @Autowired
    private ScoutingSessionRepository sessionRepository;

    @Autowired
    private ScoutingSessionTargetRepository targetRepository;

    @Autowired
    private ScoutingObservationRepository observationRepository;

    private Farm farm;
    private Greenhouse greenhouse;

    @BeforeEach
    void setup() {
        observationRepository.deleteAll();
        targetRepository.deleteAll();
        sessionRepository.deleteAll();
        greenhouseRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();

        User adminUser = userRepository.save(User.builder()
                .email(ADMIN_EMAIL)
                .password("irrelevant")
                .firstName("Owner")
                .lastName("User")
                .phoneNumber("+15551234567")
                .role(Role.SUPER_ADMIN)
                .isEnabled(true)
                .build());

        farm = farmRepository.save(Farm.builder()
                .name("Test Farm")
                .owner(adminUser)
                .scout(adminUser)
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.BASIC)
                .licensedAreaHectares(BigDecimal.ONE)
                .licensedUnitQuota(1)
                .structureType(FarmStructureType.GREENHOUSE)
                .defaultBayCount(1)
                .defaultBenchesPerBay(1)
                .defaultSpotChecksPerBench(1)
                .build());

        greenhouse = greenhouseRepository.save(Greenhouse.builder()
                .farm(farm)
                .name("Main Greenhouse")
                .bayCount(1)
                .benchesPerBay(1)
                .spotChecksPerBench(1)
                .build());
    }

    @AfterEach
    void cleanup() {
        observationRepository.deleteAll();
        targetRepository.deleteAll();
        sessionRepository.deleteAll();
        greenhouseRepository.deleteAll();
        farmRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SUPER_ADMIN")
    void createSessionReturnsCreatedPayload() throws Exception {
        CreateScoutingSessionRequest request = buildSessionRequest();

        mockMvc.perform(post("/api/scouting/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.farmId").value(farm.getId().toString()))
                .andExpect(jsonPath("$.sections[0].greenhouseId").value(greenhouse.getId().toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SUPER_ADMIN")
    void completeSessionMovesStatusToCompleted() throws Exception {
        JsonNode createdSession = createSession();
        UUID sessionId = UUID.fromString(createdSession.get("id").asText());
        long version = createdSession.get("version").asLong();

        CompleteSessionRequest request = new CompleteSessionRequest(version, true);

        mockMvc.perform(post("/api/scouting/sessions/" + sessionId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.confirmationAcknowledged").value(true));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = "SUPER_ADMIN")
    void bulkUploadCreatesObservations() throws Exception {
        JsonNode createdSession = createSession();
        UUID sessionId = UUID.fromString(createdSession.get("id").asText());
        UUID targetId = UUID.fromString(createdSession.get("sections").get(0).get("targetId").asText());

        UpsertObservationRequest observation = new UpsertObservationRequest(
                sessionId,
                targetId,
                SpeciesCode.THRIPS,
                0,
                "Bay-1",
                0,
                "Bench-1",
                0,
                7,
                "Initial observation",
                UUID.randomUUID(),
                null
        );

        BulkUpsertObservationsRequest request = new BulkUpsertObservationsRequest(sessionId, List.of(observation));

        MvcResult result = mockMvc.perform(post("/api/scouting/sessions/" + sessionId + "/observations/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].sessionTargetId").value(targetId.toString()))
                .andExpect(jsonPath("$[0].count").value(7))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response).isNotNull();
        assertThat(response.get(0).get("speciesCode").asText()).isEqualTo(SpeciesCode.THRIPS.name());
    }

    private JsonNode createSession() throws Exception {
        CreateScoutingSessionRequest request = buildSessionRequest();

        MvcResult result = mockMvc.perform(post("/api/scouting/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private CreateScoutingSessionRequest buildSessionRequest() {
        SessionTargetRequest targetRequest = new SessionTargetRequest(
                greenhouse.getId(),
                null,
                true,
                true,
                List.of(),
                List.of()
        );

        return new CreateScoutingSessionRequest(
                farm.getId(),
                List.of(targetRequest),
                LocalDate.of(2024, 1, 1),
                1,
                "Tomato",
                "Cherry",
                BigDecimal.valueOf(22.5),
                BigDecimal.valueOf(60),
                LocalTime.of(9, 0),
                "Sunny morning",
                "Weekly scouting"
        );
    }
}

package mofo.com.pestscout.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.auth.dto.LoginRequest;
import mofo.com.pestscout.auth.dto.LoginResponse;
import mofo.com.pestscout.auth.dto.RefreshTokenRequest;
import mofo.com.pestscout.auth.dto.RegisterRequest;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private CacheManager cacheManager;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void registerAndLoginThroughHttpEndpoints() throws Exception {
        String uniqueEmail = "integration+" + UUID.randomUUID() + "@example.com";

        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(uniqueEmail)
                .password("password123")
                .firstName("Integration")
                .lastName("Tester")
                .phoneNumber("555-0100")
                .role(Role.MANAGER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(uniqueEmail))
                .andExpect(jsonPath("$.role").value(Role.MANAGER.name()));

        assertThat(userRepository.findByEmail(uniqueEmail)).isPresent();

        LoginRequest loginRequest = new LoginRequest(uniqueEmail, "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(uniqueEmail))
                .andExpect(jsonPath("$.user.role").value(Role.MANAGER.name()));
    }

    @Test
    void refreshEndpointReturnsNewTokenPair() throws Exception {
        String uniqueEmail = "integration+" + UUID.randomUUID() + "@example.com";

        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(uniqueEmail)
                .password("password123")
                .firstName("Integration")
                .lastName("Tester")
                .phoneNumber("555-0100")
                .role(Role.MANAGER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(uniqueEmail, "password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        LoginResponse loginResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                LoginResponse.class
        );

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(loginResponse.refreshToken());

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(uniqueEmail))
                .andReturn();

        LoginResponse refreshedResponse = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(),
                LoginResponse.class
        );

        assertThat(refreshedResponse.token())
                .isNotBlank()
                .isNotEqualTo(loginResponse.token());
        assertThat(refreshedResponse.refreshToken())
                .isNotBlank()
                .isNotEqualTo(loginResponse.refreshToken());
    }
}

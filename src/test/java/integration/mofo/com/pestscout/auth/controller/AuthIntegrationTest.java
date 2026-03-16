package integration.mofo.com.pestscout.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.PestscoutApplication;
import mofo.com.pestscout.auth.dto.*;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.repository.PasswordResetTokenRepository;
import mofo.com.pestscout.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest(classes = PestscoutApplication.class)
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

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    void registerAndLoginThroughHttpEndpoints() throws Exception {
        String uniqueEmail = "integration+" + UUID.randomUUID() + "@example.com";

        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(uniqueEmail)
                .password("password123")
                .firstName("Integration")
                .lastName("Tester")
                .phoneNumber("555-0100")
                .country("Kenya")
                .role(Role.MANAGER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(uniqueEmail))
                .andExpect(jsonPath("$.role").value(Role.MANAGER.name()))
                .andExpect(jsonPath("$.customerNumber").isNotEmpty());

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
    void registerSuperAdminReceivesZeroedCustomerNumber() throws Exception {
        String uniqueEmail = "superadmin+" + UUID.randomUUID() + "@example.com";

        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(uniqueEmail)
                .password("password123")
                .firstName("Integration")
                .lastName("Admin")
                .phoneNumber("555-0200")
                .country("Kenya")
                .role(Role.SUPER_ADMIN)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(uniqueEmail))
                .andExpect(jsonPath("$.role").value(Role.SUPER_ADMIN.name()))
                .andExpect(jsonPath("$.customerNumber").value("00000000"));
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
                .country("Kenya")
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

        assertThat(refreshedResponse.token()).isNotBlank();
        assertThat(refreshedResponse.refreshToken()).isNotBlank();
    }

    @Test
    void forgotPasswordPersistsResetTokenAndReturnsNeutralMessage() throws Exception {
        String uniqueEmail = "integration+" + UUID.randomUUID() + "@example.com";

        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(uniqueEmail)
                .password("password123")
                .firstName("Integration")
                .lastName("Tester")
                .phoneNumber("555-0100")
                .country("Kenya")
                .role(Role.MANAGER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        ForgotPasswordRequest forgotPasswordRequest = new ForgotPasswordRequest(uniqueEmail, null, "support note");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotPasswordRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("If an account exists for that email, the reset code has been sent and is valid for 5 minutes."));

        assertThat(passwordResetTokenRepository.findAll())
                .anyMatch(token -> token.getUser().getEmail().equals(uniqueEmail));
    }
}

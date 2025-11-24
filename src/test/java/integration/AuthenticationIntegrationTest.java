package integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.auth.dto.LoginRequest;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {

    private static final String TEST_EMAIL = "admin@example.com";
    private static final String TEST_PASSWORD = "P@ssw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
    }

    @Test
    void loginReturnsTokensForValidCredentials() throws Exception {
        userRepository.save(User.builder()
                .email(TEST_EMAIL)
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .firstName("Admin")
                .lastName("User")
                .phoneNumber("+10000000000")
                .role(Role.SUPER_ADMIN)
                .isEnabled(true)
                .build());

        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(TEST_EMAIL));
    }
}

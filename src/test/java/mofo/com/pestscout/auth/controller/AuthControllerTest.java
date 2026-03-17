package mofo.com.pestscout.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.auth.dto.*;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.service.AuthService;
import mofo.com.pestscout.auth.service.UserService;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Unit Tests")
class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AuthService authService;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private LoginRequest loginRequest;
    private RegisterRequest registerRequest;
    private UserDto userDto;
    private LoginResponse loginResponse;
    private UUID userId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new TestExceptionHandler())
                .build();

        userId = UUID.randomUUID();

        loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        registerRequest = RegisterRequest.builder()
                .email("newuser@example.com")
                .password("password123")
                .firstName("New")
                .lastName("User")
                .phoneNumber("1234567890")
                .country("Kenya")
                .role(Role.SCOUT)
                .build();

        userDto = UserDto.builder()
                .id(userId)
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .role(Role.MANAGER)
                .isEnabled(true)
                .lastLogin(LocalDateTime.now())
                .build();

        loginResponse = LoginResponse.builder()
                .token("accessToken")
                .refreshToken("refreshToken")
                .expiresIn(86400000L)
                .user(userDto)
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/login returns token payload")
    void login_WithValidCredentials_ReturnsLoginResponse() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("accessToken"))
                .andExpect(jsonPath("$.user.email").value(userDto.getEmail()));
    }

    @Test
    @DisplayName("POST /api/auth/register returns created user")
    void register_WithValidData_ReturnsCreated() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(userDto);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(userDto.getEmail()));
    }

    @Test
    @DisplayName("GET /api/auth/bootstrap/super-admin/status exposes bootstrap state")
    void getInitialSuperAdminStatus_ReturnsFlags() throws Exception {
        when(authService.getInitialSuperAdminStatus())
                .thenReturn(new InitialSuperAdminStatusResponse(false, true));

        mockMvc.perform(get("/api/auth/bootstrap/super-admin/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superAdminExists").value(false))
                .andExpect(jsonPath("$.bootstrapAllowed").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/bootstrap/super-admin creates first admin")
    void bootstrapInitialSuperAdmin_ReturnsCreated() throws Exception {
        RegisterRequest bootstrapRequest = RegisterRequest.builder()
                .email("first-admin@example.com")
                .password("password123")
                .firstName("First")
                .lastName("Admin")
                .phoneNumber("1234567890")
                .country("Kenya")
                .role(Role.SUPER_ADMIN)
                .build();

        UserDto bootstrapUser = UserDto.builder()
                .id(UUID.randomUUID())
                .email(bootstrapRequest.email())
                .role(Role.SUPER_ADMIN)
                .build();

        when(authService.bootstrapInitialSuperAdmin(any(RegisterRequest.class))).thenReturn(bootstrapUser);

        mockMvc.perform(post("/api/auth/bootstrap/super-admin")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bootstrapRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/auth/users creates profile for super admin")
    void createUserProfile_WithAuthenticatedAdmin_ReturnsCreated() throws Exception {
        RegisterRequest createRequest = RegisterRequest.builder()
                .email("second-admin@example.com")
                .password("password123")
                .firstName("Second")
                .lastName("Admin")
                .phoneNumber("1234567890")
                .country("Kenya")
                .role(Role.SUPER_ADMIN)
                .build();

        UserDto createdUser = UserDto.builder()
                .id(UUID.randomUUID())
                .email(createRequest.email())
                .role(Role.SUPER_ADMIN)
                .build();

        when(authService.createUserProfile(any(RegisterRequest.class), any(UUID.class))).thenReturn(createdUser);

        mockMvc.perform(post("/api/auth/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest))
                        .requestAttr("userId", userId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(createRequest.email()))
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/auth/users returns unauthorized for non-super-admin caller")
    void createUserProfile_WhenUnauthorized_ReturnsUnauthorized() throws Exception {
        doThrow(new UnauthorizedException("Only super admins can create user profiles"))
                .when(authService).createUserProfile(any(RegisterRequest.class), any(UUID.class));

        mockMvc.perform(post("/api/auth/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest))
                        .requestAttr("userId", userId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Only super admins can create user profiles"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/auth/users/{userId}/reactivate reactivates user profile")
    void reactivateUser_WithAuthenticatedAdmin_ReturnsOk() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        ReactivateUserRequest reactivateRequest = new ReactivateUserRequest("TempPass123");

        UserDto reactivatedUser = UserDto.builder()
                .id(targetUserId)
                .email("reactivated@example.com")
                .role(Role.SCOUT)
                .isEnabled(true)
                .passwordChangeRequired(true)
                .reactivationRequired(false)
                .build();

        when(authService.reactivateUser(any(UUID.class), any(ReactivateUserRequest.class), any(UUID.class)))
                .thenReturn(reactivatedUser);

        mockMvc.perform(post("/api/auth/users/{userId}/reactivate", targetUserId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reactivateRequest))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("reactivated@example.com"))
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/auth/me returns current profile")
    void getCurrentUser_WithValidToken_ReturnsUser() throws Exception {
        when(authService.getCurrentUser(any(UUID.class))).thenReturn(userDto);

        mockMvc.perform(get("/api/auth/me").requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(userDto.getEmail()))
                .andExpect(jsonPath("$.role").value(userDto.getRole().name()));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/auth/users works for super admin without farm request attribute")
    void getUsers_WithoutFarmContext_ReturnsGlobalList() throws Exception {
        when(userService.getUsersByFarm(null, userId)).thenReturn(List.of(userDto));

        mockMvc.perform(get("/api/auth/users").requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(userDto.getEmail()));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/auth/users/{userId} updates user profile")
    void updateUser_WithValidData_ReturnsUpdatedUser() throws Exception {
        UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                .firstName("Updated")
                .lastName("Name")
                .build();

        UserDto updatedUser = UserDto.builder()
                .id(userId)
                .email(userDto.getEmail())
                .firstName("Updated")
                .lastName("Name")
                .role(userDto.getRole())
                .isEnabled(true)
                .build();

        when(userService.updateUser(any(UUID.class), any(UpdateUserRequest.class), any(UUID.class)))
                .thenReturn(updatedUser);

        mockMvc.perform(put("/api/auth/users/{userId}", userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.lastName").value("Name"));
    }

    @ControllerAdvice
    @Profile("standalone-mockmvc")
    static class TestExceptionHandler {
        @ExceptionHandler(BadRequestException.class)
        public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
        }

        @ExceptionHandler(ConflictException.class)
        public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
        }

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
        }

        @ExceptionHandler(UnauthorizedException.class)
        public ResponseEntity<Map<String, String>> handleUnauthorized(UnauthorizedException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getMessage()));
        }
    }
}

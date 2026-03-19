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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

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
                .clientSessionId("client-session-1")
                .build();
    }

    @Test
    @DisplayName("POST /api/auth/login returns token payload")
    void login_WithValidCredentials_ReturnsLoginResponse() throws Exception {
        when(authService.login(any(LoginRequest.class), nullable(String.class))).thenReturn(loginResponse);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .header(mofo.com.pestscout.auth.security.ClientSessionHeaders.CLIENT_SESSION_ID, "client-session-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("accessToken"))
                .andExpect(jsonPath("$.user.email").value(userDto.getEmail()))
                .andExpect(jsonPath("$.clientSessionId").value("client-session-1"));
    }

    @Test
    @DisplayName("POST /api/auth/session/claim returns token payload")
    void claimSession_WithValidRefreshToken_ReturnsLoginResponse() throws Exception {
        ClaimSessionRequest claimSessionRequest = new ClaimSessionRequest("refreshToken");
        when(authService.claimSession(any(ClaimSessionRequest.class), eq("client-session-2"))).thenReturn(loginResponse);

        mockMvc.perform(post("/api/auth/session/claim")
                        .with(csrf())
                        .header(mofo.com.pestscout.auth.security.ClientSessionHeaders.CLIENT_SESSION_ID, "client-session-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(claimSessionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("accessToken"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/auth/session/events opens session event stream")
    void streamSessionEvents_WithAuthenticatedUser_StartsAsyncStream() throws Exception {
        when(authService.subscribeSessionEvents(userId, "client-session-1")).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/auth/session/events")
                        .header(mofo.com.pestscout.auth.security.ClientSessionHeaders.CLIENT_SESSION_ID, "client-session-1")
                        .requestAttr("userId", userId))
                .andExpect(request().asyncStarted());
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
    @DisplayName("POST /api/auth/users/{userId}/temporary-password resets a user password with a temporary password")
    void resetUserTemporaryPassword_WithAuthenticatedSuperAdmin_ReturnsOk() throws Exception {
        UUID targetUserId = UUID.randomUUID();
        AdminResetUserPasswordRequest request = new AdminResetUserPasswordRequest("TempPass123");

        UserDto updatedUser = UserDto.builder()
                .id(targetUserId)
                .email("target@example.com")
                .role(Role.SCOUT)
                .isEnabled(true)
                .passwordChangeRequired(true)
                .build();

        when(authService.resetUserTemporaryPassword(any(UUID.class), any(AdminResetUserPasswordRequest.class), any(UUID.class)))
                .thenReturn(updatedUser);

        mockMvc.perform(post("/api/auth/users/{userId}/temporary-password", targetUserId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("target@example.com"))
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/reset-password accepts compatibility payload")
    void resetPassword_WithCompatibilityPayload_ReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .with(csrf())
                        .queryParam("token", "reset-token-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", "newPassword123"))))
                .andExpect(status().isNoContent());

        verify(authService).resetPassword(
                argThat(request -> "reset-token-123".equals(request.token())
                        && "newPassword123".equals(request.password())
                        && request.verificationChannel() == null),
                isNull()
        );
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/auth/reset-password allows authenticated forced password change without token")
    void resetPassword_WithAuthenticatedUserAndNoToken_ReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", "newPassword123")))
                        .requestAttr("userId", userId))
                .andExpect(status().isNoContent());

        verify(authService).resetPassword(
                argThat(request -> request.token() == null
                        && "newPassword123".equals(request.password())
                        && request.verificationChannel() == null),
                eq(userId)
        );
    }

    @Test
    @DisplayName("POST /api/auth/reset-password requires token when unauthenticated")
    void resetPassword_WithoutTokenAndWithoutAuthenticatedUser_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", "newPassword123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Reset token is required"));

        verify(authService, never()).resetPassword(any(ResetPasswordRequest.class), any(UUID.class));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/auth/change-password forwards authenticated requests")
    void changePassword_WithAuthenticatedUser_ReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/auth/change-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "currentPassword123",
                                "newPassword", "newPassword123"
                        )))
                        .requestAttr("userId", userId))
                .andExpect(status().isNoContent());

        verify(authService).changePassword(
                argThat(request -> "currentPassword123".equals(request.currentPassword())
                        && "newPassword123".equals(request.newPassword())),
                eq(userId)
        );
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
    @DisplayName("PUT /api/auth/me updates the authenticated user's profile")
    void updateCurrentUser_WithValidData_ReturnsUpdatedUser() throws Exception {
        UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                .firstName("Updated")
                .phoneNumber("5551234")
                .build();

        UserDto updatedUser = UserDto.builder()
                .id(userId)
                .email(userDto.getEmail())
                .firstName("Updated")
                .phoneNumber("5551234")
                .role(userDto.getRole())
                .isEnabled(true)
                .build();

        when(userService.updateUser(eq(userId), any(UpdateUserRequest.class), eq(userId)))
                .thenReturn(updatedUser);

        mockMvc.perform(put("/api/auth/me")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.phoneNumber").value("5551234"));
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

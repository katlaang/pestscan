package mofo.com.pestscout.unit.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mofo.com.pestscout.auth.controller.AuthController;
import mofo.com.pestscout.auth.dto.*;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.service.AuthService;
import mofo.com.pestscout.auth.service.UserService;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController Unit Tests (standalone, no MockBean)")
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
    private UUID farmId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new TestExceptionHandler())
                .build();

        userId = UUID.randomUUID();
        farmId = UUID.randomUUID();

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
    @DisplayName("POST /api/auth/login - Success")
    void login_WithValidCredentials_ReturnsLoginResponse() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("accessToken"))
                .andExpect(jsonPath("$.refreshToken").value("refreshToken"))
                .andExpect(jsonPath("$.user.email").value(userDto.getEmail()));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/login - Invalid Credentials")
    void login_WithInvalidCredentials_ReturnsBadRequest() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadRequestException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login - Validation Error")
    void login_WithInvalidInput_ReturnsValidationError() throws Exception {
        LoginRequest invalidRequest = LoginRequest.builder()
                .email("invalid-email")
                .password("")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register - Success")
    void register_WithValidData_ReturnsCreated() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(userDto);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(userDto.getEmail()));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/register - Email Already Exists")
    void register_WithExistingEmail_ReturnsConflict() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ConflictException("Email already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Success")
    void refreshToken_WithValidToken_ReturnsNewTokens() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("validRefreshToken");
        when(authService.refreshToken(any(RefreshTokenRequest.class)))
                .thenReturn(loginResponse);

        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("accessToken"))
                .andExpect(jsonPath("$.refreshToken").value("refreshToken"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Invalid Token")
    void refreshToken_WithInvalidToken_ReturnsBadRequest() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("invalidToken");
        when(authService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new BadRequestException("Invalid or expired refresh token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/auth/me - Success")
    void getCurrentUser_WithValidToken_ReturnsUser() throws Exception {
        when(authService.getCurrentUser(any(UUID.class))).thenReturn(userDto);

        mockMvc.perform(get("/api/auth/me").requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(userDto.getEmail()))
                .andExpect(jsonPath("$.role").value(userDto.getRole().name()));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/auth/users/{userId} - Success")
    void getUserById_WithValidId_ReturnsUser() throws Exception {
        when(userService.getUserById(any(UUID.class), any(UUID.class))).thenReturn(userDto);

        mockMvc.perform(get("/api/auth/users/{userId}", userId).requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/auth/users/{userId} - Not Found")
    void getUserById_WithInvalidId_ReturnsNotFound() throws Exception {
        when(userService.getUserById(any(UUID.class), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("User", "id", userId));

        mockMvc.perform(get("/api/auth/users/{userId}", userId).requestAttr("userId", userId))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/auth/users - Success")
    void getUsers_WithValidFarm_ReturnsUsers() throws Exception {
        List<UserDto> users = Arrays.asList(userDto);
        when(userService.getUsersByFarm(any(UUID.class), any(UUID.class))).thenReturn(users);

        mockMvc.perform(get("/api/auth/users")
                        .requestAttr("farmId", farmId)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].email").value(userDto.getEmail()));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/auth/users/role/{role} - Success")
    void getUsersByRole_WithValidRole_ReturnsUsers() throws Exception {
        List<UserDto> users = Arrays.asList(userDto);
        when(userService.getUsersByFarmAndRole(any(UUID.class), any(Role.class), any(UUID.class)))
                .thenReturn(users);

        mockMvc.perform(get("/api/auth/users/role/{role}", "SCOUT")
                        .requestAttr("farmId", farmId)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/auth/users/search - Success")
    void searchUsers_WithQuery_ReturnsPagedResults() throws Exception {
        Page<UserDto> page = new PageImpl<>(Arrays.asList(userDto));
        when(userService.searchUsers(any(UUID.class), anyString(), any(), any(UUID.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/auth/users/search")
                        .param("q", "test")
                        .param("page", "0")
                        .param("size", "10")
                        .requestAttr("farmId", farmId)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].email").value(userDto.getEmail()));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /api/auth/users/{userId} - Success")
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

    @Test
    @WithMockUser
    @DisplayName("DELETE /api/auth/users/{userId} - Success")
    void deleteUser_WithValidId_ReturnsNoContent() throws Exception {
        doNothing().when(userService).deleteUser(any(UUID.class), any(UUID.class));

        mockMvc.perform(delete("/api/auth/users/{userId}", userId)
                        .with(csrf())
                        .requestAttr("userId", userId))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(any(UUID.class), any(UUID.class));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/auth/users/stats - Success")
    void getUserStats_WithValidFarm_ReturnsStats() throws Exception {
        when(userService.getUserCount(any(UUID.class), any(UUID.class))).thenReturn(10L);
        when(userService.getActiveUserCount(any(UUID.class), any(UUID.class))).thenReturn(8L);

        mockMvc.perform(get("/api/auth/users/stats")
                        .requestAttr("farmId", farmId)
                        .requestAttr("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.activeUsers").value(8))
                .andExpect(jsonPath("$.inactiveUsers").value(2));
    }

    @ControllerAdvice
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
    }
}

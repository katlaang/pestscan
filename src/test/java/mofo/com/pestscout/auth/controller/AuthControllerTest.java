package mofo.com.pestscout.auth.controller;

import mofo.com.pestscout.auth.dto.*;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.service.AuthService;
import mofo.com.pestscout.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private LoginRequest loginRequest;
    private LoginResponse loginResponse;
    private RegisterRequest registerRequest;
    private UserDto userDto;
    private UUID requestingUserId;
    private UUID farmId;

    @BeforeEach
    void setUp() {
        requestingUserId = UUID.randomUUID();
        farmId = UUID.randomUUID();

        loginRequest = new LoginRequest("test@example.com", "password123");
        userDto = UserDto.builder()
                .id(requestingUserId)
                .farmId(farmId)
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .role(Role.FARMER)
                .isEnabled(true)
                .lastLogin(LocalDateTime.now())
                .build();
        loginResponse = new LoginResponse("access-token", "refresh-token", 3600L, userDto);

        registerRequest = RegisterRequest.builder()
                .email("new@example.com")
                .password("password123")
                .firstName("New")
                .lastName("User")
                .phoneNumber("123456789")
                .role(Role.FARMER)
                .farmId(farmId)
                .build();
    }

    @Test
    void shouldLoginSuccessfully() {
        when(authService.login(loginRequest)).thenReturn(loginResponse);

        ResponseEntity<LoginResponse> response = authController.login(loginRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(loginResponse);
        verify(authService).login(loginRequest);
    }

    @Test
    void shouldRegisterUserSuccessfully() {
        when(authService.register(registerRequest)).thenReturn(userDto);

        ResponseEntity<UserDto> response = authController.register(registerRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(userDto);
        verify(authService).register(registerRequest);
    }

    @Test
    void shouldRefreshTokenSuccessfully() {
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest("refresh-token");
        when(authService.refreshToken(refreshRequest)).thenReturn(loginResponse);

        ResponseEntity<LoginResponse> response = authController.refreshToken(refreshRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(loginResponse);
        verify(authService).refreshToken(refreshRequest);
    }

    @Test
    void shouldGetCurrentUserSuccessfully() {
        when(authService.getCurrentUser(requestingUserId)).thenReturn(userDto);

        ResponseEntity<UserDto> response = authController.getCurrentUser(requestingUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(userDto);
        verify(authService).getCurrentUser(requestingUserId);
    }

    @Test
    void shouldGetUserByIdSuccessfully() {
        UUID targetUserId = UUID.randomUUID();
        when(userService.getUserById(targetUserId, requestingUserId)).thenReturn(userDto);

        ResponseEntity<UserDto> response = authController.getUserById(targetUserId, requestingUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(userDto);
        verify(userService).getUserById(targetUserId, requestingUserId);
    }

    @Test
    void shouldGetUsersByFarmSuccessfully() {
        List<UserDto> users = List.of(userDto);
        when(userService.getUsersByFarm(farmId, requestingUserId)).thenReturn(users);

        ResponseEntity<List<UserDto>> response = authController.getUsers(farmId, requestingUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(userDto);
        verify(userService).getUsersByFarm(farmId, requestingUserId);
    }

    @Test
    void shouldGetUsersByRoleSuccessfully() {
        List<UserDto> users = List.of(userDto);
        when(userService.getUsersByFarmAndRole(farmId, Role.FARMER, requestingUserId)).thenReturn(users);

        ResponseEntity<List<UserDto>> response = authController.getUsersByRole(Role.FARMER, farmId, requestingUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(userDto);
        verify(userService).getUsersByFarmAndRole(farmId, Role.FARMER, requestingUserId);
    }

    @Test
    void shouldSearchUsersSuccessfully() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<UserDto> usersPage = new PageImpl<>(List.of(userDto), pageable, 1);
        when(userService.searchUsers(farmId, "query", pageable, requestingUserId)).thenReturn(usersPage);

        ResponseEntity<Page<UserDto>> response = authController.searchUsers("query", farmId, requestingUserId, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(usersPage);
        verify(userService).searchUsers(farmId, "query", pageable, requestingUserId);
    }

    @Test
    void shouldUpdateUserSuccessfully() {
        UUID targetUserId = UUID.randomUUID();
        UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                .email("updated@example.com")
                .firstName("Updated")
                .build();
        when(userService.updateUser(targetUserId, updateRequest, requestingUserId)).thenReturn(userDto);

        ResponseEntity<UserDto> response = authController.updateUser(targetUserId, updateRequest, requestingUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(userDto);
        verify(userService).updateUser(targetUserId, updateRequest, requestingUserId);
    }

    @Test
    void shouldDeleteUserSuccessfully() {
        UUID targetUserId = UUID.randomUUID();

        ResponseEntity<Void> response = authController.deleteUser(targetUserId, requestingUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(userService).deleteUser(targetUserId, requestingUserId);
    }

    @Test
    void shouldGetUserStatsSuccessfully() {
        when(userService.getUserCount(farmId, requestingUserId)).thenReturn(10L);
        when(userService.getActiveUserCount(farmId, requestingUserId)).thenReturn(8L);

        ResponseEntity<UserStatsDto> response = authController.getUserStats(farmId, requestingUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new UserStatsDto(10L, 8L, 2L));
        verify(userService).getUserCount(farmId, requestingUserId);
        verify(userService).getActiveUserCount(farmId, requestingUserId);
    }
}

package mofo.com.pestscout.auth.service;

import mofo.com.pestscout.auth.dto.*;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private LoginRequest loginRequest;
    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("encodedPassword")
                .firstName("Test")
                .lastName("User")
                .phoneNumber("1234567890")
                .role(Role.MANAGER)
                .isEnabled(true)
                .lastLogin(LocalDateTime.now())
                .build();

        loginRequest = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        registerRequest = RegisterRequest.builder()
                .email("newuser@example.com")
                .password("password123")
                .firstName("New")
                .lastName("User")
                .phoneNumber("9876543210")
                .role(Role.SCOUT)
                .build();
    }

    @Test
    @DisplayName("Should successfully login with valid credentials")
    void login_WithValidCredentials_ReturnsLoginResponse() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.email()))
                .thenReturn(Optional.of(testUser));
        when(tokenProvider.generateToken(testUser))
                .thenReturn("accessToken");
        when(tokenProvider.generateRefreshToken(testUser))
                .thenReturn("refreshToken");
        when(userService.convertToDto(testUser))
                .thenReturn(UserDto.builder()
                        .id(testUser.getId())
                        .email(testUser.getEmail())
                        .build());

        // Act
        LoginResponse response = authService.login(loginRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("accessToken");
        assertThat(response.refreshToken()).isEqualTo("refreshToken");
        assertThat(response.user()).isNotNull();

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw BadRequestException when user is disabled")
    void login_WithDisabledUser_ThrowsBadRequestException() {
        // Arrange
        testUser.setIsEnabled(false);
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.email()))
                .thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("disabled");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user not found")
    void login_WithNonExistentUser_ThrowsResourceNotFoundException() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.email()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should successfully register new user")
    void register_WithValidData_ReturnsUserDto() {
        // Arrange
        User newUser = User.builder()
                .id(UUID.randomUUID())
                .email(registerRequest.email())
                .password("encodedPassword")
                .firstName(registerRequest.firstName())
                .lastName(registerRequest.lastName())
                .phoneNumber(registerRequest.phoneNumber())
                .role(registerRequest.role())
                .isEnabled(true)
                .build();

        when(userRepository.existsByEmail(registerRequest.email()))
                .thenReturn(false);
        when(passwordEncoder.encode(registerRequest.password()))
                .thenReturn("encodedPassword");
        when(userRepository.save(any(User.class)))
                .thenReturn(newUser);
        when(userService.convertToDto(any(User.class)))
                .thenReturn(UserDto.builder()
                        .id(newUser.getId())
                        .email(newUser.getEmail())
                        .build());

        // Act
        UserDto result = authService.register(registerRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(registerRequest.email());

        verify(userRepository).existsByEmail(registerRequest.email());
        verify(passwordEncoder).encode(registerRequest.password());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when email already exists")
    void register_WithExistingEmail_ThrowsConflictException() {
        // Arrange
        when(userRepository.existsByEmail(registerRequest.email()))
                .thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully refresh token")
    void refreshToken_WithValidToken_ReturnsNewTokens() {
        // Arrange
        String refreshToken = "validRefreshToken";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(tokenProvider.validateToken(refreshToken))
                .thenReturn(true);
        when(tokenProvider.isRefreshToken(refreshToken))
                .thenReturn(true);
        when(tokenProvider.getUserIdFromToken(refreshToken))
                .thenReturn(testUser.getId());
        when(userRepository.findById(testUser.getId()))
                .thenReturn(Optional.of(testUser));
        when(tokenProvider.generateToken(testUser))
                .thenReturn("newAccessToken");
        when(tokenProvider.generateRefreshToken(testUser))
                .thenReturn("newRefreshToken");
        when(userService.convertToDto(testUser))
                .thenReturn(UserDto.builder()
                        .id(testUser.getId())
                        .build());

        // Act
        LoginResponse response = authService.refreshToken(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("newAccessToken");
        assertThat(response.refreshToken()).isEqualTo("newRefreshToken");

        verify(tokenProvider).validateToken(refreshToken);
        verify(tokenProvider).isRefreshToken(refreshToken);
    }

    @Test
    @DisplayName("Should throw BadRequestException with invalid refresh token")
    void refreshToken_WithInvalidToken_ThrowsBadRequestException() {
        // Arrange
        String refreshToken = "invalidToken";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(tokenProvider.validateToken(refreshToken))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid or expired");

        verify(tokenProvider, never()).getUserIdFromToken(any());
    }

    @Test
    @DisplayName("Should throw BadRequestException when token is not a refresh token")
    void refreshToken_WithAccessToken_ThrowsBadRequestException() {
        // Arrange
        String accessToken = "validAccessToken";
        RefreshTokenRequest request = new RefreshTokenRequest(accessToken);

        when(tokenProvider.validateToken(accessToken))
                .thenReturn(true);
        when(tokenProvider.isRefreshToken(accessToken))
                .thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not a refresh token");
    }

    @Test
    @DisplayName("Should get current user successfully")
    void getCurrentUser_WithValidUserId_ReturnsUserDto() {
        // Arrange
        UUID userId = testUser.getId();
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(testUser));
        when(userService.convertToDto(testUser))
                .thenReturn(UserDto.builder()
                        .id(userId)
                        .email(testUser.getEmail())
                        .build());

        // Act
        UserDto result = authService.getCurrentUser(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo(testUser.getEmail());

        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user ID not found")
    void getCurrentUser_WithInvalidUserId_ThrowsResourceNotFoundException() {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.getCurrentUser(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

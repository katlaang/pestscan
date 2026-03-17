package mofo.com.pestscout.auth.service;

import mofo.com.pestscout.auth.dto.*;
import mofo.com.pestscout.auth.model.*;
import mofo.com.pestscout.auth.repository.PasswordResetTokenRepository;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.exception.UnauthorizedException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private CustomerNumberService customerNumberService;

    @Mock
    private UserFarmMembershipRepository membershipRepository;

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
                .country("Kenya")
                .role(Role.SCOUT)
                .build();
    }

    @Test
    @DisplayName("Should successfully login with valid credentials")
    void login_WithValidCredentials_ReturnsLoginResponse() {
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

        LoginResponse response = authService.login(loginRequest);

        assertThat(response.token()).isEqualTo("accessToken");
        assertThat(response.refreshToken()).isEqualTo("refreshToken");
        assertThat(response.user().getEmail()).isEqualTo(testUser.getEmail());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should successfully self-register a scout profile")
    void register_WithValidData_ReturnsUserDto() {
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

        when(userRepository.existsByEmail(registerRequest.email())).thenReturn(false);
        when(customerNumberService.normalizeCountryCode(anyString())).thenReturn("KE");
        when(customerNumberService.resolveCustomerNumber(registerRequest.customerNumber(), "KE"))
                .thenReturn("KE00000001");
        when(userRepository.existsByCustomerNumber("KE00000001")).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.password())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(newUser);
        when(userService.convertToDto(newUser)).thenReturn(UserDto.builder()
                .id(newUser.getId())
                .email(newUser.getEmail())
                .role(newUser.getRole())
                .build());

        UserDto result = authService.register(registerRequest);

        assertThat(result.getEmail()).isEqualTo(registerRequest.email());
        assertThat(result.getRole()).isEqualTo(Role.SCOUT);
        verify(membershipRepository, never()).save(any(UserFarmMembership.class));
    }

    @Test
    @DisplayName("Should reject self-registration for super admin")
    void register_WithSuperAdminRole_ThrowsUnauthorizedException() {
        RegisterRequest superAdminRequest = RegisterRequest.builder()
                .email("admin@example.com")
                .password("password123")
                .firstName("Super")
                .lastName("Admin")
                .phoneNumber("1112223333")
                .country("Kenya")
                .role(Role.SUPER_ADMIN)
                .build();

        assertThatThrownBy(() -> authService.register(superAdminRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Self-service registration");
    }

    @Test
    @DisplayName("Should bootstrap the first super admin when none exists")
    void bootstrapInitialSuperAdmin_WhenMissing_CreatesSuperAdmin() {
        RegisterRequest bootstrapRequest = RegisterRequest.builder()
                .email("first-admin@example.com")
                .password("password123")
                .firstName("First")
                .lastName("Admin")
                .phoneNumber("1112223333")
                .country("Kenya")
                .role(Role.SUPER_ADMIN)
                .build();

        User savedAdmin = User.builder()
                .id(UUID.randomUUID())
                .email(bootstrapRequest.email())
                .password("encodedPassword")
                .firstName(bootstrapRequest.firstName())
                .lastName(bootstrapRequest.lastName())
                .phoneNumber(bootstrapRequest.phoneNumber())
                .customerNumber("KE00000099")
                .role(Role.SUPER_ADMIN)
                .isEnabled(true)
                .build();

        when(userRepository.existsByRoleAndDeletedFalse(Role.SUPER_ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail(bootstrapRequest.email())).thenReturn(false);
        when(customerNumberService.normalizeCountryCode(anyString())).thenReturn("KE");
        when(customerNumberService.resolveCustomerNumber(bootstrapRequest.customerNumber(), "KE"))
                .thenReturn("KE00000099");
        when(userRepository.existsByCustomerNumber("KE00000099")).thenReturn(false);
        when(passwordEncoder.encode(bootstrapRequest.password())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedAdmin);
        when(userService.convertToDto(savedAdmin)).thenReturn(UserDto.builder()
                .id(savedAdmin.getId())
                .email(savedAdmin.getEmail())
                .role(savedAdmin.getRole())
                .customerNumber(savedAdmin.getCustomerNumber())
                .build());

        UserDto result = authService.bootstrapInitialSuperAdmin(bootstrapRequest);

        assertThat(result.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(result.getCustomerNumber()).isEqualTo("KE00000099");
        verify(membershipRepository, never()).save(any(UserFarmMembership.class));
    }

    @Test
    @DisplayName("Should reject initial bootstrap when a super admin already exists")
    void bootstrapInitialSuperAdmin_WhenExisting_ThrowsConflictException() {
        RegisterRequest bootstrapRequest = RegisterRequest.builder()
                .email("first-admin@example.com")
                .password("password123")
                .firstName("First")
                .lastName("Admin")
                .phoneNumber("1112223333")
                .country("Kenya")
                .role(Role.SUPER_ADMIN)
                .build();

        when(userRepository.existsByRoleAndDeletedFalse(Role.SUPER_ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> authService.bootstrapInitialSuperAdmin(bootstrapRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been created");
    }

    @Test
    @DisplayName("Should let a super admin create another super admin profile")
    void createUserProfile_WithSuperAdminRequester_CreatesAdditionalSuperAdmin() {
        UUID requesterId = UUID.randomUUID();
        RegisterRequest createRequest = RegisterRequest.builder()
                .email("second-admin@example.com")
                .password("password123")
                .firstName("Second")
                .lastName("Admin")
                .phoneNumber("1112224444")
                .country("Kenya")
                .role(Role.SUPER_ADMIN)
                .build();

        User requester = User.builder()
                .id(requesterId)
                .email("first-admin@example.com")
                .role(Role.SUPER_ADMIN)
                .isEnabled(true)
                .build();

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email(createRequest.email())
                .password("encodedPassword")
                .firstName(createRequest.firstName())
                .lastName(createRequest.lastName())
                .phoneNumber(createRequest.phoneNumber())
                .customerNumber("KE00000123")
                .role(Role.SUPER_ADMIN)
                .isEnabled(true)
                .build();

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(userRepository.existsByEmail(createRequest.email())).thenReturn(false);
        when(customerNumberService.normalizeCountryCode(anyString())).thenReturn("KE");
        when(customerNumberService.resolveCustomerNumber(createRequest.customerNumber(), "KE"))
                .thenReturn("KE00000123");
        when(userRepository.existsByCustomerNumber("KE00000123")).thenReturn(false);
        when(passwordEncoder.encode(createRequest.password())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userService.convertToDto(savedUser)).thenReturn(UserDto.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .customerNumber(savedUser.getCustomerNumber())
                .build());

        UserDto result = authService.createUserProfile(createRequest, requesterId);

        assertThat(result.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(result.getEmail()).isEqualTo(createRequest.email());
        verify(membershipRepository, never()).save(any(UserFarmMembership.class));
    }

    @Test
    @DisplayName("Should create a farm-linked profile when requested by super admin")
    void createUserProfile_WithFarmScopedRole_CreatesMembership() {
        UUID requesterId = UUID.randomUUID();
        UUID farmId = UUID.randomUUID();

        RegisterRequest createRequest = RegisterRequest.builder()
                .email("farmadmin@example.com")
                .password("password123")
                .firstName("Farm")
                .lastName("Admin")
                .phoneNumber("1112223333")
                .country("Kenya")
                .role(Role.FARM_ADMIN)
                .farmId(farmId)
                .build();

        User requester = User.builder()
                .id(requesterId)
                .email("first-admin@example.com")
                .role(Role.SUPER_ADMIN)
                .isEnabled(true)
                .build();

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email(createRequest.email())
                .password("encodedPassword")
                .firstName(createRequest.firstName())
                .lastName(createRequest.lastName())
                .phoneNumber(createRequest.phoneNumber())
                .customerNumber("KE00000222")
                .role(createRequest.role())
                .isEnabled(true)
                .build();

        Farm farm = Farm.builder().id(farmId).name("Farm A").build();

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(userRepository.existsByEmail(createRequest.email())).thenReturn(false);
        when(customerNumberService.normalizeCountryCode(anyString())).thenReturn("KE");
        when(customerNumberService.resolveCustomerNumber(createRequest.customerNumber(), "KE"))
                .thenReturn("KE00000222");
        when(userRepository.existsByCustomerNumber("KE00000222")).thenReturn(false);
        when(passwordEncoder.encode(createRequest.password())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(userService.convertToDto(savedUser)).thenReturn(UserDto.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .build());

        UserDto result = authService.createUserProfile(createRequest, requesterId);

        assertThat(result.getFarmId()).isEqualTo(farmId);
        verify(membershipRepository).save(any(UserFarmMembership.class));
    }

    @Test
    @DisplayName("Should reject admin-managed profile creation from non-super-admin")
    void createUserProfile_WithNonSuperAdmin_ThrowsUnauthorizedException() {
        UUID requesterId = UUID.randomUUID();
        User requester = User.builder()
                .id(requesterId)
                .email("manager@example.com")
                .role(Role.MANAGER)
                .isEnabled(true)
                .build();

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> authService.createUserProfile(registerRequest, requesterId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Only super admins");
    }

    @Test
    @DisplayName("Should successfully refresh token")
    void refreshToken_WithValidToken_ReturnsNewTokens() {
        String refreshToken = "validRefreshToken";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(tokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(tokenProvider.isRefreshToken(refreshToken)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(refreshToken)).thenReturn(testUser.getId());
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(tokenProvider.generateToken(testUser)).thenReturn("newAccessToken");
        when(tokenProvider.generateRefreshToken(testUser)).thenReturn("newRefreshToken");
        when(userService.convertToDto(testUser)).thenReturn(UserDto.builder().id(testUser.getId()).build());

        LoginResponse response = authService.refreshToken(request);

        assertThat(response.token()).isEqualTo("newAccessToken");
        assertThat(response.refreshToken()).isEqualTo("newRefreshToken");
    }

    @Test
    @DisplayName("Should get current user successfully")
    void getCurrentUser_WithValidUserId_ReturnsUserDto() {
        UUID userId = testUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userService.convertToDto(testUser)).thenReturn(UserDto.builder()
                .id(userId)
                .email(testUser.getEmail())
                .build());

        UserDto result = authService.getCurrentUser(userId);

        assertThat(result.getEmail()).isEqualTo(testUser.getEmail());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user ID not found")
    void getCurrentUser_WithInvalidUserId_ThrowsResourceNotFoundException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should store reset token for existing active user")
    void requestPasswordReset_ForExistingUser_SavesToken() {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ForgotPasswordRequest request = new ForgotPasswordRequest(testUser.getEmail(), ResetChannel.EMAIL, "note");

        authService.requestPasswordReset(request);

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }
}

package mofo.com.pestscout.auth.service;

import mofo.com.pestscout.auth.dto.*;
import mofo.com.pestscout.auth.model.*;
import mofo.com.pestscout.auth.repository.PasswordResetTokenRepository;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.exception.UnauthorizedException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

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

    @Mock
    private UserOnboardingService userOnboardingService;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private PasswordPolicyService passwordPolicyService;

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

        lenient().doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            String rawPassword = invocation.getArgument(1);
            user.applyPassword("encoded:" + rawPassword, LocalDateTime.now().plusDays(90));
            return null;
        }).when(passwordPolicyService).validateAndApplyPassword(any(User.class), anyString());
    }

    @Test
    @DisplayName("Should successfully login with valid credentials")
    void login_WithValidCredentials_ReturnsLoginResponse() {
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.email()))
                .thenReturn(Optional.of(testUser));
        when(tokenProvider.generateToken(eq(testUser), anyLong()))
                .thenReturn("accessToken");
        when(tokenProvider.generateRefreshToken(eq(testUser), anyLong()))
                .thenReturn("refreshToken");
        when(tokenProvider.getAccessTokenExpirationMillis()).thenReturn(300000L);
        when(tokenProvider.getRefreshTokenExpirationMillis()).thenReturn(600000L);
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
        when(userOnboardingService.calculateTemporaryPasswordExpiry()).thenReturn(LocalDateTime.now().plusDays(5));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userService.convertToDto(savedUser)).thenReturn(UserDto.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .customerNumber(savedUser.getCustomerNumber())
                .passwordChangeRequired(true)
                .build());

        UserDto result = authService.createUserProfile(createRequest, requesterId);

        assertThat(result.getRole()).isEqualTo(Role.SUPER_ADMIN);
        assertThat(result.getEmail()).isEqualTo(createRequest.email());
        verify(userOnboardingService).issueSetupInvitation(savedUser, false);
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
        when(userOnboardingService.calculateTemporaryPasswordExpiry()).thenReturn(LocalDateTime.now().plusDays(5));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(userService.convertToDto(savedUser)).thenReturn(UserDto.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .passwordChangeRequired(true)
                .build());

        UserDto result = authService.createUserProfile(createRequest, requesterId);

        assertThat(result.getFarmId()).isEqualTo(farmId);
        verify(userOnboardingService).issueSetupInvitation(savedUser, false);
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
        when(userSessionService.isIdleExpired(testUser)).thenReturn(false);
        when(userSessionService.isPasswordChangeSessionExpired(testUser)).thenReturn(false);
        when(tokenProvider.generateToken(eq(testUser), anyLong())).thenReturn("newAccessToken");
        when(tokenProvider.generateRefreshToken(eq(testUser), anyLong())).thenReturn("newRefreshToken");
        when(tokenProvider.getAccessTokenExpirationMillis()).thenReturn(300000L);
        when(tokenProvider.getRefreshTokenExpirationMillis()).thenReturn(600000L);
        when(userService.convertToDto(testUser)).thenReturn(UserDto.builder().id(testUser.getId()).build());

        LoginResponse response = authService.refreshToken(request);

        assertThat(response.token()).isEqualTo("newAccessToken");
        assertThat(response.refreshToken()).isEqualTo("newRefreshToken");
    }

    @Test
    @DisplayName("Should reject refresh token after 5-minute inactivity")
    void refreshToken_WhenIdleExpired_ThrowsBadRequestException() {
        String refreshToken = "validRefreshToken";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);

        when(tokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(tokenProvider.isRefreshToken(refreshToken)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(refreshToken)).thenReturn(testUser.getId());
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(userSessionService.isIdleExpired(testUser)).thenReturn(true);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Session expired due to inactivity");
    }

    @Test
    @DisplayName("Should allow login with expired password and issue a change-required session")
    void login_WithExpiredPassword_ReturnsShortLivedResetSession() {
        User expiredUser = User.builder()
                .id(UUID.randomUUID())
                .email("expired-password@example.com")
                .password("encodedPassword")
                .phoneNumber("1234567890")
                .country("KE")
                .customerNumber("KE00000999")
                .role(Role.MANAGER)
                .isEnabled(true)
                .passwordExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        Authentication authentication = mock(Authentication.class);
        when(userRepository.findByEmail(expiredUser.getEmail())).thenReturn(Optional.of(expiredUser));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tokenProvider.getAccessTokenExpirationMillis()).thenReturn(3600000L);
        when(tokenProvider.getRefreshTokenExpirationMillis()).thenReturn(7200000L);
        when(tokenProvider.generateToken(eq(expiredUser), anyLong())).thenReturn("expiredAccessToken");
        when(tokenProvider.generateRefreshToken(eq(expiredUser), anyLong())).thenReturn("expiredRefreshToken");
        when(userService.convertToDto(expiredUser)).thenReturn(UserDto.builder()
                .id(expiredUser.getId())
                .email(expiredUser.getEmail())
                .passwordExpired(true)
                .passwordChangeRequired(true)
                .build());

        LoginResponse response = authService.login(new LoginRequest(expiredUser.getEmail(), "password123"));

        assertThat(response.token()).isEqualTo("expiredAccessToken");
        assertThat(response.refreshToken()).isEqualTo("expiredRefreshToken");
        assertThat(response.expiresIn()).isBetween(1L, 300000L);
        assertThat(response.user().getPasswordExpired()).isTrue();
        assertThat(response.user().getPasswordChangeRequired()).isTrue();
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

    @Test
    @DisplayName("Should soft-delete expired temporary-password user before login")
    void login_WithExpiredTemporaryPassword_ThrowsBadRequestException() {
        User expiredUser = User.builder()
                .id(UUID.randomUUID())
                .email("expired@example.com")
                .password("encodedPassword")
                .phoneNumber("1234567890")
                .country("KE")
                .customerNumber("KE00000888")
                .role(Role.MANAGER)
                .isEnabled(true)
                .passwordChangeRequired(true)
                .temporaryPasswordExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(userRepository.findByEmail(expiredUser.getEmail())).thenReturn(Optional.of(expiredUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest(expiredUser.getEmail(), "password123")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Temporary password has expired");

        verify(userRepository).save(expiredUser);
        verify(userOnboardingService).invalidateActiveTokens(expiredUser);
    }

    @Test
    @DisplayName("Should clear temporary-password requirement when reset completes")
    void resetPassword_WithInvitationToken_ClearsTemporaryPasswordFlags() {
        User invitedUser = User.builder()
                .id(UUID.randomUUID())
                .email("invited@example.com")
                .password("oldPassword")
                .phoneNumber("1234567890")
                .country("KE")
                .customerNumber("KE00000555")
                .role(Role.MANAGER)
                .isEnabled(true)
                .passwordChangeRequired(true)
                .temporaryPasswordExpiresAt(LocalDateTime.now().plusDays(5))
                .build();

        PasswordResetToken token = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(invitedUser)
                .token("setup-token")
                .expiresAt(LocalDateTime.now().plusDays(5))
                .verificationChannel(ResetChannel.EMAIL)
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest(
                "setup-token",
                "newPassword123",
                ResetChannel.EMAIL,
                null,
                null,
                invitedUser.getEmail(),
                null,
                null,
                null,
                null,
                null
        );

        when(passwordResetTokenRepository.findByToken("setup-token")).thenReturn(Optional.of(token));
        when(passwordResetTokenRepository.findByUserAndUsedAtIsNull(invitedUser)).thenReturn(List.of(token));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.resetPassword(request, null);

        assertThat(invitedUser.getPassword()).isEqualTo("encoded:newPassword123");
        assertThat(invitedUser.getPasswordChangeRequired()).isFalse();
        assertThat(invitedUser.getPasswordExpiresAt()).isNotNull();
        assertThat(invitedUser.getTemporaryPasswordExpiresAt()).isNull();
        assertThat(invitedUser.getReactivationRequired()).isFalse();
        verify(userRepository).save(invitedUser);
    }

    @Test
    @DisplayName("Should allow authenticated temporary-password change without reset token")
    void resetPassword_WithAuthenticatedUserAndNoToken_ClearsTemporaryPasswordFlags() {
        User invitedUser = User.builder()
                .id(UUID.randomUUID())
                .email("invited@example.com")
                .password("oldPassword")
                .phoneNumber("1234567890")
                .country("KE")
                .customerNumber("KE00000555")
                .role(Role.MANAGER)
                .isEnabled(true)
                .passwordChangeRequired(true)
                .temporaryPasswordExpiresAt(LocalDateTime.now().plusDays(5))
                .build();

        PasswordResetToken staleToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(invitedUser)
                .token("stale-token")
                .expiresAt(LocalDateTime.now().plusDays(5))
                .verificationChannel(ResetChannel.EMAIL)
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest(
                null,
                "newPassword123",
                null,
                null,
                null,
                invitedUser.getEmail(),
                null,
                null,
                null,
                null,
                null
        );

        when(userRepository.findById(invitedUser.getId())).thenReturn(Optional.of(invitedUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordResetTokenRepository.findByUserAndUsedAtIsNull(invitedUser)).thenReturn(List.of(staleToken));

        authService.resetPassword(request, invitedUser.getId());

        assertThat(invitedUser.getPassword()).isEqualTo("encoded:newPassword123");
        assertThat(invitedUser.getPasswordChangeRequired()).isFalse();
        assertThat(invitedUser.getPasswordExpiresAt()).isNotNull();
        assertThat(invitedUser.getTemporaryPasswordExpiresAt()).isNull();
        assertThat(staleToken.getUsedAt()).isNotNull();
        verify(passwordResetTokenRepository, never()).findByToken(anyString());
        verify(userRepository).save(invitedUser);
        assertThat(invitedUser.getSessionValidAfter()).isNotNull();
    }

    @Test
    @DisplayName("Should change password for an authenticated user before expiry")
    void changePassword_WithAuthenticatedUser_UpdatesPasswordAndInvalidatesSessions() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .password("oldEncodedPassword")
                .phoneNumber("1234567890")
                .country("KE")
                .customerNumber("KE00000999")
                .role(Role.MANAGER)
                .isEnabled(true)
                .passwordExpiresAt(LocalDateTime.now().plusDays(30))
                .build();
        Authentication authentication = mock(Authentication.class);
        ChangePasswordRequest request = new ChangePasswordRequest("currentPassword123", "NewSecurePass123!");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.changePassword(request, userId);

        assertThat(user.getPassword()).isEqualTo("encoded:NewSecurePass123!");
        assertThat(user.getSessionValidAfter()).isNotNull();
        verify(passwordPolicyService).recordPassword(user, "NewSecurePass123!");
    }

    @Test
    @DisplayName("Should reject authenticated password change when current password is incorrect")
    void changePassword_WithIncorrectCurrentPassword_ThrowsBadRequestException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@example.com")
                .password("oldEncodedPassword")
                .phoneNumber("1234567890")
                .country("KE")
                .customerNumber("KE00000999")
                .role(Role.MANAGER)
                .isEnabled(true)
                .passwordExpiresAt(LocalDateTime.now().plusDays(30))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.changePassword(new ChangePasswordRequest("wrongPassword", "NewSecurePass123!"), userId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(userRepository, never()).save(user);
    }

    @Test
    @DisplayName("Should require a token for authenticated users without a forced password change")
    void resetPassword_WithoutTokenForRegularAuthenticatedUser_ThrowsBadRequestException() {
        User activeUser = User.builder()
                .id(UUID.randomUUID())
                .email("active@example.com")
                .password("oldPassword")
                .phoneNumber("1234567890")
                .country("KE")
                .customerNumber("KE00000888")
                .role(Role.SCOUT)
                .isEnabled(true)
                .passwordChangeRequired(false)
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest(
                null,
                "newPassword123",
                null,
                null,
                null,
                activeUser.getEmail(),
                null,
                null,
                null,
                null,
                null
        );

        when(userRepository.findById(activeUser.getId())).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.resetPassword(request, activeUser.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Reset token is required");

        verify(passwordResetTokenRepository, never()).findByToken(anyString());
        verify(userRepository, never()).save(activeUser);
    }

    @Test
    @DisplayName("Should issue a 5-minute token window for temporary-password login")
    void login_WithTemporaryPassword_ReturnsShortLivedTokens() {
        Authentication authentication = mock(Authentication.class);
        User invitedUser = User.builder()
                .id(UUID.randomUUID())
                .email("invited@example.com")
                .password("encodedPassword")
                .firstName("Invited")
                .lastName("User")
                .phoneNumber("1234567890")
                .country("KE")
                .customerNumber("KE00000555")
                .role(Role.SCOUT)
                .isEnabled(true)
                .passwordChangeRequired(true)
                .temporaryPasswordExpiresAt(LocalDateTime.now().plusDays(5))
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByEmail(invitedUser.getEmail()))
                .thenReturn(Optional.of(invitedUser));
        when(tokenProvider.getAccessTokenExpirationMillis()).thenReturn(3600000L);
        when(tokenProvider.getRefreshTokenExpirationMillis()).thenReturn(7200000L);
        when(tokenProvider.generateToken(eq(invitedUser), anyLong())).thenReturn("shortAccessToken");
        when(tokenProvider.generateRefreshToken(eq(invitedUser), anyLong())).thenReturn("shortRefreshToken");
        when(userService.convertToDto(invitedUser)).thenReturn(UserDto.builder().id(invitedUser.getId()).build());

        LoginResponse response = authService.login(new LoginRequest(invitedUser.getEmail(), "password123"));

        ArgumentCaptor<Long> accessExpiryCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> refreshExpiryCaptor = ArgumentCaptor.forClass(Long.class);

        assertThat(response.token()).isEqualTo("shortAccessToken");
        assertThat(response.refreshToken()).isEqualTo("shortRefreshToken");
        assertThat(response.expiresIn()).isBetween(1L, 300000L);
        verify(tokenProvider).generateToken(eq(invitedUser), accessExpiryCaptor.capture());
        verify(tokenProvider).generateRefreshToken(eq(invitedUser), refreshExpiryCaptor.capture());
        assertThat(accessExpiryCaptor.getValue()).isBetween(1L, 300000L);
        assertThat(refreshExpiryCaptor.getValue()).isBetween(1L, 300000L);
    }

    @Test
    @DisplayName("Should reject refresh when temporary-password session timed out")
    void refreshToken_WhenTemporaryPasswordSessionExpired_ThrowsBadRequestException() {
        String refreshToken = "validRefreshToken";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        User invitedUser = User.builder()
                .id(UUID.randomUUID())
                .email("invited@example.com")
                .password("encodedPassword")
                .phoneNumber("1234567890")
                .country("KE")
                .customerNumber("KE00000555")
                .role(Role.SCOUT)
                .isEnabled(true)
                .passwordChangeRequired(true)
                .temporaryPasswordExpiresAt(LocalDateTime.now().plusDays(5))
                .lastLogin(LocalDateTime.now().minusMinutes(6))
                .lastActivityAt(LocalDateTime.now())
                .build();

        when(tokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(tokenProvider.isRefreshToken(refreshToken)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(refreshToken)).thenReturn(invitedUser.getId());
        when(userRepository.findById(invitedUser.getId())).thenReturn(Optional.of(invitedUser));
        when(userSessionService.isIdleExpired(invitedUser)).thenReturn(false);
        when(userSessionService.isPasswordChangeSessionExpired(invitedUser)).thenReturn(true);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Temporary password session expired");
    }

    @Test
    @DisplayName("Should reject refresh token issued before session invalidation cutoff")
    void refreshToken_WhenRefreshTokenWasRevoked_ThrowsBadRequestException() {
        String refreshToken = "validRefreshToken";
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .password("encodedPassword")
                .phoneNumber("1234567890")
                .country("KE")
                .customerNumber("KE00000123")
                .role(Role.MANAGER)
                .isEnabled(true)
                .sessionValidAfter(LocalDateTime.now())
                .build();

        when(tokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(tokenProvider.isRefreshToken(refreshToken)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(refreshToken)).thenReturn(user.getId());
        when(tokenProvider.getIssuedAtFromToken(refreshToken))
                .thenReturn(java.util.Date.from(LocalDateTime.now().minusMinutes(1).atZone(java.time.ZoneId.systemDefault()).toInstant()));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Session is no longer valid");
    }

    @Test
    @DisplayName("Should let super admin reactivate a soft-deleted profile")
    void reactivateUser_WithSuperAdminRequester_RestoresProfile() {
        UUID requesterId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        User requester = User.builder()
                .id(requesterId)
                .email("admin@example.com")
                .role(Role.SUPER_ADMIN)
                .isEnabled(true)
                .build();

        User targetUser = User.builder()
                .id(targetUserId)
                .email("disabled@example.com")
                .password("oldPassword")
                .phoneNumber("1112223333")
                .country("KE")
                .customerNumber("KE00000111")
                .role(Role.SCOUT)
                .isEnabled(false)
                .passwordChangeRequired(true)
                .reactivationRequired(true)
                .build();
        targetUser.markDeleted();

        UserDto reactivatedUser = UserDto.builder()
                .id(targetUserId)
                .email(targetUser.getEmail())
                .role(targetUser.getRole())
                .passwordChangeRequired(true)
                .reactivationRequired(false)
                .build();

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
        when(userOnboardingService.calculateTemporaryPasswordExpiry()).thenReturn(LocalDateTime.now().plusDays(5));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.convertToDto(targetUser)).thenReturn(reactivatedUser);

        UserDto result = authService.reactivateUser(targetUserId, new ReactivateUserRequest("TempPass123"), requesterId);

        assertThat(result.getEmail()).isEqualTo(targetUser.getEmail());
        assertThat(targetUser.isDeleted()).isFalse();
        assertThat(targetUser.getIsEnabled()).isTrue();
        assertThat(targetUser.getPassword()).isEqualTo("encoded:TempPass123");
        assertThat(targetUser.getPasswordExpiresAt()).isNotNull();
        assertThat(targetUser.getPasswordChangeRequired()).isTrue();
        verify(userOnboardingService).issueSetupInvitation(targetUser, true);
    }
}

package mofo.com.pestscout.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.auth.dto.*;
import mofo.com.pestscout.auth.model.*;
import mofo.com.pestscout.auth.repository.PasswordResetTokenRepository;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.auth.security.ClientSessionHeaders;
import mofo.com.pestscout.auth.security.JwtTokenProvider;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.exception.UnauthorizedException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * Authentication Service
 * Handles login, registration, token refresh
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final FarmRepository farmRepository;
    private final CustomerNumberService customerNumberService;
    private final UserFarmMembershipRepository membershipRepository;
    private final UserOnboardingService userOnboardingService;
    private final UserSessionService userSessionService;
    private final PasswordPolicyService passwordPolicyService;
    private final ClientSessionEventService clientSessionEventService;

    /**
     * Authenticate user and generate JWT tokens
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        return login(request, null);
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String clientSessionId) {
        userRepository.findByEmail(request.email()).ifPresent(this::assertLoginAllowed);

        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );

            // Load user
            User user = userRepository.findByEmailForUpdate(request.email())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.email()));

            // Check if user is enabled
            if (!user.isActive()) {
                throw new BadRequestException("User account is disabled");
            }

            String resolvedClientSessionId = activateExclusiveClientSession(user, clientSessionId, true);

            user.recordActivity();
            userRepository.save(user);

            // Generate tokens
            long accessTokenExpirationMillis = resolveAccessTokenExpirationMillis(user);
            long refreshTokenExpirationMillis = resolveRefreshTokenExpirationMillis(user);
            String accessToken = tokenProvider.generateToken(user, accessTokenExpirationMillis);
            String refreshToken = tokenProvider.generateRefreshToken(user, refreshTokenExpirationMillis);

            log.info("User logged in successfully: {}", user.getEmail());

            return LoginResponse.builder()
                    .token(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(accessTokenExpirationMillis)
                    .user(userService.convertToDto(user))
                    .clientSessionId(resolvedClientSessionId)
                    .build();

        } catch (AuthenticationException e) {
            log.warn("Failed login attempt for email: {}", request.email());
            throw new BadRequestException("Invalid email or password");
        }
    }

    /**
     * Register new user
     */
    @Transactional
    public UserDto register(RegisterRequest request) {
        validateSelfServiceRegistration(request);
        return createUser(request, false);
    }

    /**
     * Create the very first super admin profile before any other super admin exists.
     */
    @Transactional
    public UserDto bootstrapInitialSuperAdmin(RegisterRequest request) {
        validateInitialSuperAdminBootstrap(request);
        return createUser(request, false);
    }

    /**
     * Create a user profile on behalf of someone else. Reserved for the existing super admin.
     */
    @Transactional
    public UserDto createUserProfile(RegisterRequest request, UUID requestingUserId) {
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        if (requester.getRole() != Role.SUPER_ADMIN) {
            throw new UnauthorizedException("Only super admins can create user profiles");
        }

        validateSuperAdminManagedRegistration(request);
        return createUser(request, true);
    }

    @Transactional
    public UserDto reactivateUser(UUID userId, ReactivateUserRequest request, UUID requestingUserId) {
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        if (requester.getRole() != Role.SUPER_ADMIN) {
            throw new UnauthorizedException("Only super admins can reactivate user profiles");
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!targetUser.isDeleted() && Boolean.TRUE.equals(targetUser.getIsEnabled()) && !Boolean.TRUE.equals(targetUser.getReactivationRequired())) {
            throw new BadRequestException("User does not require reactivation");
        }

        targetUser.restore();
        targetUser.setIsEnabled(true);
        passwordPolicyService.validateAndApplyPassword(targetUser, request.temporaryPassword());
        targetUser.beginTemporaryPasswordWindow(userOnboardingService.calculateTemporaryPasswordExpiry());
        targetUser.invalidateSessions();

        User savedUser = userRepository.save(targetUser);
        passwordPolicyService.recordPassword(savedUser, request.temporaryPassword());
        userOnboardingService.issueSetupInvitation(savedUser, true);

        log.info("User profile reactivated: {} by {}", savedUser.getEmail(), requester.getEmail());

        return userService.convertToDto(savedUser);
    }

    @Transactional
    public UserDto resetUserTemporaryPassword(UUID userId, AdminResetUserPasswordRequest request, UUID requestingUserId) {
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        if (requester.getRole() != Role.SUPER_ADMIN) {
            throw new UnauthorizedException("Only super admins can reset user passwords");
        }

        User targetUser = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (targetUser.isDeleted()
                || Boolean.TRUE.equals(targetUser.getReactivationRequired())
                || !Boolean.TRUE.equals(targetUser.getIsEnabled())) {
            throw new BadRequestException("Disabled or deleted users must be reactivated instead of resetting the password.");
        }

        String previousClientSessionId = targetUser.getActiveClientSessionId();
        invalidateExistingTokens(targetUser);
        passwordPolicyService.validateAndApplyPassword(targetUser, request.temporaryPassword());
        targetUser.beginTemporaryPasswordWindow(userOnboardingService.calculateTemporaryPasswordExpiry());
        targetUser.invalidateSessions();
        targetUser.setActiveClientSessionId(null);
        targetUser.setActiveSessionStartedAt(null);

        User savedUser = userRepository.save(targetUser);
        passwordPolicyService.recordPassword(savedUser, request.temporaryPassword());
        clientSessionEventService.notifySessionReplacedAfterCommit(savedUser.getId(), previousClientSessionId, null);

        log.info("Temporary password reset for {} by {}", savedUser.getEmail(), requester.getEmail());
        return userService.convertToDto(savedUser);
    }

    @Transactional(readOnly = true)
    public InitialSuperAdminStatusResponse getInitialSuperAdminStatus() {
        boolean exists = userRepository.existsByRoleAndDeletedFalse(Role.SUPER_ADMIN);
        return new InitialSuperAdminStatusResponse(exists, !exists);
    }

    /**
     * Refresh access token using refresh token
     */
    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        return refreshToken(request, null);
    }

    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request, String clientSessionId) {
        String refreshToken = request.refreshToken();

        // Validate refresh token
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("Invalid or expired refresh token");
        }

        // Check if it's actually a refresh token
        if (!tokenProvider.isRefreshToken(refreshToken)) {
            throw new BadRequestException("Token is not a refresh token");
        }

        // Get user from refresh token
        UUID userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        expirePendingInvitationIfNeeded(user);
        assertTokenIsCurrent(user, refreshToken);
        assertClientSessionIsCurrent(user, refreshToken, clientSessionId);
        if (userSessionService.isIdleExpired(user)) {
            throw new BadRequestException("Session expired due to inactivity. Please log in again.");
        }
        if (userSessionService.isPasswordChangeSessionExpired(user)) {
            throw new BadRequestException("Temporary password session expired. Please log in again.");
        }

        // Check if user is enabled
        if (!user.isActive()) {
            throw new BadRequestException("User account is disabled");
        }

        if (!requiresCredentialChange(user)) {
            passwordPolicyService.assertPasswordIsCurrent(user);
        }

        // Generate new tokens
        long accessTokenExpirationMillis = resolveAccessTokenExpirationMillis(user);
        long refreshTokenExpirationMillis = resolveRefreshTokenExpirationMillis(user);
        String newAccessToken = tokenProvider.generateToken(user, accessTokenExpirationMillis);
        String newRefreshToken = tokenProvider.generateRefreshToken(user, refreshTokenExpirationMillis);

        log.info("Tokens refreshed for user: {}", user.getEmail());

        return LoginResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(accessTokenExpirationMillis)
                .user(userService.convertToDto(user))
                .clientSessionId(resolveClientSessionIdForResponse(user, clientSessionId, refreshToken))
                .build();
    }

    @Transactional
    public LoginResponse claimSession(ClaimSessionRequest request, String clientSessionId) {
        if (clientSessionId == null || clientSessionId.isBlank()) {
            throw new BadRequestException(ClientSessionHeaders.CLIENT_SESSION_ID + " header is required");
        }

        String refreshToken = request.refreshToken();
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
        if (!tokenProvider.isRefreshToken(refreshToken)) {
            throw new BadRequestException("Token is not a refresh token");
        }

        UUID userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        expirePendingInvitationIfNeeded(user);
        assertTokenIsCurrent(user, refreshToken);

        if (!user.isActive()) {
            throw new BadRequestException("User account is disabled");
        }

        if (!requiresCredentialChange(user)) {
            passwordPolicyService.assertPasswordIsCurrent(user);
        }

        String resolvedClientSessionId = activateExclusiveClientSession(user, clientSessionId, false);
        user.recordActivity();
        userRepository.save(user);

        long accessTokenExpirationMillis = resolveAccessTokenExpirationMillis(user);
        long refreshTokenExpirationMillis = resolveRefreshTokenExpirationMillis(user);
        String newAccessToken = tokenProvider.generateToken(user, accessTokenExpirationMillis);
        String newRefreshToken = tokenProvider.generateRefreshToken(user, refreshTokenExpirationMillis);

        return LoginResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(accessTokenExpirationMillis)
                .user(userService.convertToDto(user))
                .clientSessionId(resolvedClientSessionId)
                .build();
    }

    /**
     * Get current authenticated user
     */
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        return userService.convertToDto(user);
    }

    @Transactional(readOnly = true)
    public SseEmitter subscribeSessionEvents(UUID userId, String clientSessionId) {
        if (clientSessionId == null || clientSessionId.isBlank()) {
            throw new BadRequestException(ClientSessionHeaders.CLIENT_SESSION_ID + " header is required");
        }
        return clientSessionEventService.subscribe(userId, clientSessionId.trim());
    }

    /**
     * Generate a password reset token without revealing whether the account exists.
     * If the account exists and is active, existing tokens are invalidated and a new one is created.
     */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email())
                .filter(User::isActive)
                .ifPresent(user -> {
                    invalidateExistingTokens(user);

                    PasswordResetToken token = PasswordResetToken.builder()
                            .user(user)
                            .token(UUID.randomUUID().toString())
                            .expiresAt(LocalDateTime.now().plusMinutes(5))
                            .verificationChannel(request.resolvedChannel())
                            .verificationNotes(request.requestNotes())
                            .build();

                    passwordResetTokenRepository.save(token);
                    log.info("Password reset token created for {} via {}", user.getEmail(), token.getVerificationChannel());
                });
    }

    /**
     * Complete a password reset using a one-time token with extra checks for phone-based requests.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request, UUID actingUserId) {
        if (request.token() == null || request.token().isBlank()) {
            resetAuthenticatedUserPassword(request, actingUserId);
            return;
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.token())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (resetToken.isUsed()) {
            throw new BadRequestException("Reset token has already been used");
        }

        if (resetToken.isExpired()) {
            throw new BadRequestException("Reset token has expired");
        }

        User user = resetToken.getUser();
        expirePendingInvitationIfNeeded(user);

        if (!user.isActive()) {
            throw new BadRequestException("User account is disabled");
        }

        ResetChannel channel = request.resolvedVerificationChannel(resetToken.getVerificationChannel());
        resetToken.setVerificationChannel(channel);

        if (channel == ResetChannel.PHONE_CALL) {
            validatePhoneReset(request, user, resetToken, actingUserId);
        } else if (request.verificationNotes() != null) {
            resetToken.setVerificationNotes(request.verificationNotes());
        }

        passwordPolicyService.validateAndApplyPassword(user, request.password());
        user.completePasswordReset();
        user.invalidateSessions();
        User savedUser = userRepository.save(user);
        passwordPolicyService.recordPassword(savedUser, request.password());

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        passwordResetTokenRepository.findByUserAndUsedAtIsNull(user).stream()
                .filter(token -> !token.getId().equals(resetToken.getId()))
                .forEach(token -> {
                    token.setUsedAt(LocalDateTime.now());
                    passwordResetTokenRepository.save(token);
                });

        log.info("Password reset completed for {} via {}", user.getEmail(), channel);
    }

    private void resetAuthenticatedUserPassword(ResetPasswordRequest request, UUID actingUserId) {
        if (actingUserId == null) {
            throw new BadRequestException("Reset token is required");
        }

        User user = userRepository.findById(actingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actingUserId));

        expirePendingInvitationIfNeeded(user);

        if (!user.isActive()) {
            throw new BadRequestException("User account is disabled");
        }

        if (!requiresCredentialChange(user)) {
            throw new BadRequestException("Reset token is required");
        }

        passwordPolicyService.validateAndApplyPassword(user, request.password());
        user.completePasswordReset();
        user.invalidateSessions();
        User savedUser = userRepository.save(user);
        passwordPolicyService.recordPassword(savedUser, request.password());
        invalidateExistingTokens(user);

        log.info("Authenticated password change completed for {}", user.getEmail());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, UUID actingUserId) {
        User user = userRepository.findById(actingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actingUserId));

        expirePendingInvitationIfNeeded(user);

        if (!user.isActive()) {
            throw new BadRequestException("User account is disabled");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), request.currentPassword())
            );
        } catch (AuthenticationException ex) {
            throw new BadRequestException("Current password is incorrect");
        }

        passwordPolicyService.validateAndApplyPassword(user, request.newPassword());
        user.completePasswordReset();
        user.invalidateSessions();
        User savedUser = userRepository.save(user);
        passwordPolicyService.recordPassword(savedUser, request.newPassword());

        log.info("Authenticated password change completed for {}", user.getEmail());
    }

    private void invalidateExistingTokens(User user) {
        passwordResetTokenRepository.findByUserAndUsedAtIsNull(user)
                .forEach(token -> {
                    token.setUsedAt(LocalDateTime.now());
                    passwordResetTokenRepository.save(token);
                });
    }

    private void assertTokenIsCurrent(User user, String token) {
        if (user == null || user.getSessionValidAfter() == null) {
            return;
        }

        Date issuedAt = tokenProvider.getIssuedAtFromToken(token);
        if (issuedAt == null) {
            return;
        }

        LocalDateTime issuedAtDateTime = LocalDateTime.ofInstant(issuedAt.toInstant(), ZoneId.systemDefault());
        if (issuedAtDateTime.isBefore(user.getSessionValidAfter())) {
            throw new BadRequestException("Session is no longer valid. Please log in again.");
        }
    }

    private void assertClientSessionIsCurrent(User user, String token, String requestClientSessionId) {
        if (user == null || user.getActiveClientSessionId() == null || user.getActiveClientSessionId().isBlank()) {
            return;
        }

        String tokenSessionId = tokenProvider.getSessionIdFromToken(token);
        if (tokenSessionId == null || !user.getActiveClientSessionId().equals(tokenSessionId)) {
            throw new BadRequestException("Session is no longer valid. Please log in again.");
        }

        String expectedClientSessionId = requestClientSessionId;
        if (expectedClientSessionId == null || expectedClientSessionId.isBlank()) {
            expectedClientSessionId = tokenSessionId;
        }
        if (!user.getActiveClientSessionId().equals(expectedClientSessionId)) {
            throw new BadRequestException("Session is active in another tab or device. Please log in again.");
        }
    }

    private String activateExclusiveClientSession(User user, String requestedClientSessionId, boolean updateLastLogin) {
        String resolvedClientSessionId = normalizeClientSessionId(requestedClientSessionId);
        String previousClientSessionId = user.getActiveClientSessionId();
        user.activateExclusiveSession(resolvedClientSessionId);
        if (updateLastLogin) {
            user.updateLastLogin();
        }
        if (previousClientSessionId != null
                && !previousClientSessionId.isBlank()
                && !previousClientSessionId.equals(resolvedClientSessionId)) {
            clientSessionEventService.notifySessionReplacedAfterCommit(
                    user.getId(),
                    previousClientSessionId,
                    resolvedClientSessionId
            );
        }
        return resolvedClientSessionId;
    }

    private String normalizeClientSessionId(String requestedClientSessionId) {
        if (requestedClientSessionId == null || requestedClientSessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestedClientSessionId.trim();
    }

    private String resolveClientSessionIdForResponse(User user, String requestClientSessionId, String token) {
        if (user.getActiveClientSessionId() != null && !user.getActiveClientSessionId().isBlank()) {
            return user.getActiveClientSessionId();
        }

        if (requestClientSessionId != null && !requestClientSessionId.isBlank()) {
            return requestClientSessionId.trim();
        }

        return tokenProvider.getSessionIdFromToken(token);
    }

    private long resolveAccessTokenExpirationMillis(User user) {
        return resolveTokenExpirationMillis(user, tokenProvider.getAccessTokenExpirationMillis());
    }

    private long resolveRefreshTokenExpirationMillis(User user) {
        return resolveTokenExpirationMillis(user, tokenProvider.getRefreshTokenExpirationMillis());
    }

    private long resolveTokenExpirationMillis(User user, long defaultExpirationMillis) {
        if (user == null || !requiresCredentialChange(user)) {
            return defaultExpirationMillis;
        }

        long remainingMillis = userSessionService.getRemainingPasswordChangeSessionMillis(user);
        return remainingMillis == Long.MAX_VALUE ? defaultExpirationMillis : Math.max(1L, Math.min(defaultExpirationMillis, remainingMillis));
    }

    private void validatePhoneReset(ResetPasswordRequest request, User user, PasswordResetToken resetToken, UUID actingUserId) {
        if (actingUserId == null) {
            throw new BadRequestException("Authenticated support user is required to perform phone resets");
        }

        User supportUser = userRepository.findById(actingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actingUserId));

        if (!supportUser.isActive()) {
            throw new BadRequestException("Support user account is disabled");
        }

        if (request.callerName() == null || request.callerName().isBlank()) {
            throw new BadRequestException("Caller name is required for phone resets");
        }

        if (request.callbackNumber() == null || request.callbackNumber().isBlank()) {
            throw new BadRequestException("Callback number is required for phone resets");
        }

        if (request.verificationNotes() == null || request.verificationNotes().isBlank()) {
            throw new BadRequestException("Verification notes are required for phone resets");
        }

        if (request.firstName() == null || request.firstName().isBlank()) {
            throw new BadRequestException("First name confirmation is required for phone resets");
        }

        if (request.lastName() == null || request.lastName().isBlank()) {
            throw new BadRequestException("Last name confirmation is required for phone resets");
        }

        if (request.email() == null || request.email().isBlank()) {
            throw new BadRequestException("Email confirmation is required for phone resets");
        }

        if (request.lastLoginDate() == null) {
            throw new BadRequestException("Last login date is required for phone resets");
        }

        if (request.customerNumber() == null || request.customerNumber().isBlank()) {
            throw new BadRequestException("Customer number is required for phone resets");
        }

        String normalizedCallback = normalizePhone(request.callbackNumber());
        String normalizedUserPhone = normalizePhone(user.getPhoneNumber());

        if (!normalizedCallback.equals(normalizedUserPhone)) {
            throw new BadRequestException("Callback number must match the phone number on file");
        }

        if (user.getEmail() == null || !request.email().trim().equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("Email confirmation does not match our records");
        }

        if (user.getFirstName() == null || !request.firstName().trim().equalsIgnoreCase(user.getFirstName())) {
            throw new BadRequestException("First name confirmation does not match our records");
        }

        if (user.getLastName() == null || !request.lastName().trim().equalsIgnoreCase(user.getLastName())) {
            throw new BadRequestException("Last name confirmation does not match our records");
        }

        if (user.getLastLogin() == null || !user.getLastLogin().toLocalDate().isEqual(request.lastLoginDate())) {
            throw new BadRequestException("Last login answer does not match our records");
        }

        if (user.getCustomerNumber() == null || !user.getCustomerNumber().equalsIgnoreCase(request.customerNumber().trim())) {
            throw new BadRequestException("Customer number does not match our records");
        }

        resetToken.setCallerName(request.callerName().trim());
        resetToken.setCallbackNumber(request.callbackNumber().trim());
        resetToken.setVerificationNotes(request.verificationNotes());
        resetToken.setFirstNameConfirmation(request.firstName().trim());
        resetToken.setLastNameConfirmation(request.lastName().trim());
        resetToken.setEmailConfirmation(request.email().trim());
        resetToken.setLastLoginVerifiedOn(request.lastLoginDate());
        resetToken.setCustomerNumberConfirmation(request.customerNumber().trim());
        resetToken.setPerformedBy(supportUser);
    }

    private String resolveCountryCode(String country, UUID farmId) {
        if (country != null && !country.isBlank()) {
            return customerNumberService.normalizeCountryCode(country);
        }
        if (farmId == null) {
            throw new BadRequestException("Country is required to register a user without a farm");
        }

        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        return customerNumberService.normalizeCountryCode(farm.getCountry());
    }

    private String normalizePhone(String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        return phoneNumber.replaceAll("[^0-9]", "");
    }

    private void validateSelfServiceRegistration(RegisterRequest request) {
        if (request.role() == Role.FARM_ADMIN || request.role() == Role.SUPER_ADMIN || request.role() == Role.EDGE_SYNC) {
            throw new UnauthorizedException("Self-service registration is only available for scout and manager profiles");
        }
        validateSuperAdminFarmScope(request);
    }

    private void validateSuperAdminManagedRegistration(RegisterRequest request) {
        if (request.role() == Role.EDGE_SYNC) {
            throw new BadRequestException("EDGE_SYNC accounts must be provisioned through sync configuration");
        }
        validateSuperAdminFarmScope(request);
    }

    private void validateInitialSuperAdminBootstrap(RegisterRequest request) {
        if (request.role() != Role.SUPER_ADMIN) {
            throw new BadRequestException("Initial bootstrap must create a SUPER_ADMIN profile");
        }
        if (userRepository.existsByRoleAndDeletedFalse(Role.SUPER_ADMIN)) {
            throw new ConflictException("Initial super admin has already been created");
        }
        validateSuperAdminFarmScope(request);
    }

    private void validateSuperAdminFarmScope(RegisterRequest request) {
        if (request.role() == Role.SUPER_ADMIN && request.farmId() != null) {
            throw new BadRequestException("Super admin profiles cannot be scoped to a farm");
        }
    }

    private UserDto createUser(RegisterRequest request, boolean adminManaged) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }

        String normalizedCountry = customerNumberService.normalizeCountryCode(request.country());
        String customerNumber = resolveCustomerNumber(request, normalizedCountry);

        LocalDateTime temporaryPasswordExpiresAt = adminManaged
                ? userOnboardingService.calculateTemporaryPasswordExpiry()
                : null;

        User user = User.builder()
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phoneNumber(request.phoneNumber())
                .country(normalizedCountry)
                .customerNumber(customerNumber)
                .role(request.role())
                .isEnabled(true)
                .passwordChangeRequired(adminManaged)
                .temporaryPasswordExpiresAt(temporaryPasswordExpiresAt)
                .reactivationRequired(false)
                .build();

        passwordPolicyService.validateAndApplyPassword(user, request.password());
        User savedUser = userRepository.save(user);
        attachFarmMembership(savedUser, request);

        if (adminManaged) {
            savedUser.beginTemporaryPasswordWindow(temporaryPasswordExpiresAt);
            savedUser = userRepository.save(savedUser);
            userOnboardingService.issueSetupInvitation(savedUser, false);
        }

        passwordPolicyService.recordPassword(savedUser, request.password());

        log.info("New user profile created: {} ({})", savedUser.getEmail(), savedUser.getRole());

        UserDto userDto = userService.convertToDto(savedUser);
        userDto.setFarmId(request.farmId());
        return userDto;
    }

    private String resolveCustomerNumber(RegisterRequest request, String normalizedCountry) {
        String countryCode = resolveCountryCode(normalizedCountry, request.farmId());
        String customerNumber = customerNumberService.resolveCustomerNumber(request.customerNumber(), countryCode);

        if (userRepository.existsByCustomerNumber(customerNumber)) {
            throw new ConflictException("Customer number already registered");
        }

        return customerNumber;
    }

    private void attachFarmMembership(User user, RegisterRequest request) {
        if (request.role() == Role.SUPER_ADMIN) {
            return;
        }
        if (request.farmId() == null) {
            return;
        }

        Farm farm = farmRepository.findById(request.farmId())
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", request.farmId()));

        UserFarmMembership membership = UserFarmMembership.builder()
                .user(user)
                .farm(farm)
                .role(request.role())
                .isActive(true)
                .build();

        membershipRepository.save(membership);
    }

    private void assertLoginAllowed(User user) {
        expirePendingInvitationIfNeeded(user);

        if (!user.isActive()) {
            throw new BadRequestException("User account is disabled");
        }
    }

    private void expirePendingInvitationIfNeeded(User user) {
        if (!user.isTemporaryPasswordExpired()) {
            return;
        }

        user.markTemporaryPasswordExpired();
        userRepository.save(user);
        userOnboardingService.invalidateActiveTokens(user);
        throw new BadRequestException("Temporary password has expired. Contact a super admin to reactivate your profile.");
    }

    private boolean requiresCredentialChange(User user) {
        return user != null && user.requiresPasswordChange();
    }
}

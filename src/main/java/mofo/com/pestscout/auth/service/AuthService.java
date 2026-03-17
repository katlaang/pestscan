package mofo.com.pestscout.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final FarmRepository farmRepository;
    private final CustomerNumberService customerNumberService;
    private final UserFarmMembershipRepository membershipRepository;

    /**
     * Authenticate user and generate JWT tokens
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );

            // Load user
            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.email()));

            // Check if user is enabled
            if (!user.isActive()) {
                throw new BadRequestException("User account is disabled");
            }

            // Update last login
            user.updateLastLogin();
            userRepository.save(user);

            // Generate tokens
            String accessToken = tokenProvider.generateToken(user);
            String refreshToken = tokenProvider.generateRefreshToken(user);

            log.info("User logged in successfully: {}", user.getEmail());

            return LoginResponse.builder()
                    .token(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(86400000L)  // 24 hours
                    .user(userService.convertToDto(user))
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
        return createUser(request);
    }

    /**
     * Create the very first super admin profile before any other super admin exists.
     */
    @Transactional
    public UserDto bootstrapInitialSuperAdmin(RegisterRequest request) {
        validateInitialSuperAdminBootstrap(request);
        return createUser(request);
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
        return createUser(request);
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Check if user is enabled
        if (!user.isActive()) {
            throw new BadRequestException("User account is disabled");
        }

        // Generate new tokens
        String newAccessToken = tokenProvider.generateToken(user);
        String newRefreshToken = tokenProvider.generateRefreshToken(user);

        log.info("Tokens refreshed for user: {}", user.getEmail());

        return LoginResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(86400000L)
                .user(userService.convertToDto(user))
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
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.token())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (resetToken.isUsed()) {
            throw new BadRequestException("Reset token has already been used");
        }

        if (resetToken.isExpired()) {
            throw new BadRequestException("Reset token has expired");
        }

        User user = resetToken.getUser();
        if (!user.isActive()) {
            throw new BadRequestException("User account is disabled");
        }

        ResetChannel channel = request.verificationChannel();
        resetToken.setVerificationChannel(channel);

        if (channel == ResetChannel.PHONE_CALL) {
            validatePhoneReset(request, user, resetToken, actingUserId);
        } else if (request.verificationNotes() != null) {
            resetToken.setVerificationNotes(request.verificationNotes());
        }

        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        resetToken.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        log.info("Password reset completed for {} via {}", user.getEmail(), channel);
    }

    private void invalidateExistingTokens(User user) {
        passwordResetTokenRepository.findByUserAndUsedAtIsNull(user)
                .forEach(token -> {
                    token.setUsedAt(LocalDateTime.now());
                    passwordResetTokenRepository.save(token);
                });
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

    private UserDto createUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already registered");
        }

        String normalizedCountry = customerNumberService.normalizeCountryCode(request.country());
        String customerNumber = resolveCustomerNumber(request, normalizedCountry);

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phoneNumber(request.phoneNumber())
                .country(normalizedCountry)
                .customerNumber(customerNumber)
                .role(request.role())
                .isEnabled(true)
                .build();

        User savedUser = userRepository.save(user);
        attachFarmMembership(savedUser, request);

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
}

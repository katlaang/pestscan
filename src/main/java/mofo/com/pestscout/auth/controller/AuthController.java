package mofo.com.pestscout.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.dto.*;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.service.AuthService;
import mofo.com.pestscout.auth.service.UserService;
import mofo.com.pestscout.common.dto.ErrorResponse;
import mofo.com.pestscout.common.dto.PagedResponse;
import mofo.com.pestscout.common.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * Authentication Controller.
 * Exposes endpoints for:
 *     User authentication (login, refresh, current user)
 *     User management within the multi-tenant farm context
 *  */

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and user management APIs")
public class AuthController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final UserService userService;

    /**
     * Authenticate user with email and password.
     */
    @PostMapping("/login")
    @Operation(
            summary = "User login",
            description = "Authenticate user with email and password. Returns JWT access token and refresh token.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "Login successful",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LoginResponse.class)
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400",
                            description = "Invalid credentials or account disabled",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "401",
                            description = "Authentication failed",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)
                            )
                    )
            }
    )
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = mofo.com.pestscout.auth.security.ClientSessionHeaders.CLIENT_SESSION_ID, required = false) String clientSessionId) {
        LOGGER.info("Login request for email: {}", request.email());
        LoginResponse response = authService.login(request, clientSessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Register a new user.
     */
    @PostMapping("/register")
    @Operation(
            summary = "Self-service user registration",
            description = "Register a new user in the system. Public registration is limited to scout and manager profiles.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "201",
                            description = "User registered successfully",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UserDto.class)
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400",
                            description = "Invalid input data",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "409",
                            description = "Email already registered",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)
                            )
                    )
            }
    )
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        LOGGER.info("Self-registration request for email: {} (role: {})", request.email(), request.role());
        UserDto user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @GetMapping("/bootstrap/super-admin/status")
    @Operation(
            summary = "Get initial super admin bootstrap status",
            description = "Indicates whether the one-time public super admin bootstrap endpoint is still available."
    )
    public ResponseEntity<InitialSuperAdminStatusResponse> getInitialSuperAdminStatus() {
        return ResponseEntity.ok(authService.getInitialSuperAdminStatus());
    }

    @PostMapping("/bootstrap/super-admin")
    @Operation(
            summary = "Create initial super admin",
            description = "Create the first super admin. This endpoint works only when no super admin exists yet."
    )
    public ResponseEntity<UserDto> bootstrapInitialSuperAdmin(@Valid @RequestBody RegisterRequest request) {
        LOGGER.info("Initial super admin bootstrap request for email: {}", request.email());
        UserDto user = authService.bootstrapInitialSuperAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    /**
     * Create a new user profile as the active super admin.
     */
    @PostMapping("/users")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Create user profile",
            description = "Create a new user profile as a super admin. The supplied password is treated as a temporary password that expires after 5 days unless the user resets it from the email invitation."
    )
    public ResponseEntity<UserDto> createUserProfile(
            @Valid @RequestBody RegisterRequest request,
            @RequestAttribute("userId") UUID requestingUserId) {

        LOGGER.info("Admin profile creation request for email: {} by {}", request.email(), requestingUserId);
        UserDto user = authService.createUserProfile(request, requestingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/users/{userId}/reactivate")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Reactivate user profile",
            description = "Reactivate a soft-deleted or disabled profile as a super admin. The supplied password becomes a new temporary password and a new password reset email is queued."
    )
    public ResponseEntity<UserDto> reactivateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody ReactivateUserRequest request,
            @RequestAttribute("userId") UUID requestingUserId) {

        LOGGER.info("Reactivation request for user {} by {}", userId, requestingUserId);
        UserDto user = authService.reactivateUser(userId, request, requestingUserId);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/users/{userId}/temporary-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Assign temporary password",
            description = "Assign a new temporary password to an existing user as a super admin. The user must change it after the next login."
    )
    public ResponseEntity<UserDto> resetUserTemporaryPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminResetUserPasswordRequest request,
            @RequestAttribute("userId") UUID requestingUserId) {

        LOGGER.info("Temporary password reset request for user {} by {}", userId, requestingUserId);
        UserDto user = authService.resetUserTemporaryPassword(userId, request, requestingUserId);
        return ResponseEntity.ok(user);
    }

    /**
     * Begin the password reset process.
     */
    @PostMapping("/forgot-password")
    @Operation(
            summary = "Request password reset",
            description = "Generate a time-bound password reset token without revealing account existence.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "202",
                            description = "Reset request accepted",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ResetAcknowledgementResponse.class)
                            )
                    )
            }
    )
    public ResponseEntity<ResetAcknowledgementResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        LOGGER.info("Password reset requested for: {} via {}", request.email(), request.resolvedChannel());
        authService.requestPasswordReset(request);
        return ResponseEntity.accepted()
                .body(new ResetAcknowledgementResponse("If an account exists for that email, the reset code has been sent and is valid for 5 minutes."));
    }

    /**
     * Complete a password reset using a one-time token.
     */
    @PostMapping("/reset-password")
    @Operation(
            summary = "Reset password",
            description = "Reset password using a token. Requires additional validation when handled over the phone.",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "204",
                            description = "Password reset successful"
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400",
                            description = "Invalid or expired token",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)
                            )
                    )
            }
    )
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            @RequestParam(value = "token", required = false) String token,
            @RequestAttribute(value = "userId", required = false) UUID requestingUserId) {
        ResetPasswordRequest normalizedRequest = request.withResolvedToken(token);
        if ((normalizedRequest.token() == null || normalizedRequest.token().isBlank()) && requestingUserId == null) {
            throw new BadRequestException("Reset token is required");
        }

        LOGGER.info("Reset password attempt via {} by user {}",
                normalizedRequest.verificationChannel() == null ? "AUTO" : normalizedRequest.verificationChannel(),
                requestingUserId);
        authService.resetPassword(normalizedRequest, requestingUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Change password",
            description = "Change the authenticated user's password using the current password. This works for voluntary password updates and for authenticated expired-password sessions."
    )
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestAttribute("userId") UUID requestingUserId) {
        LOGGER.info("Change password request by {}", requestingUserId);
        authService.changePassword(request, requestingUserId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Refresh JWT access token using a refresh token.
     */
    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh token",
            description = "Refresh JWT access token using a refresh token",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "Token refreshed successfully",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LoginResponse.class)
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "400",
                            description = "Invalid refresh token",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ErrorResponse.class)
                            )
                    )
            }
    )
    public ResponseEntity<LoginResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            @RequestHeader(value = mofo.com.pestscout.auth.security.ClientSessionHeaders.CLIENT_SESSION_ID, required = false) String clientSessionId) {
        LOGGER.info("Token refresh request");
        LoginResponse response = authService.refreshToken(request, clientSessionId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/session/claim")
    @Operation(
            summary = "Claim active session",
            description = "Activate this tab or device as the sole live client session for the user. Requires a refresh token."
    )
    public ResponseEntity<LoginResponse> claimSession(
            @Valid @RequestBody ClaimSessionRequest request,
            @RequestHeader(value = mofo.com.pestscout.auth.security.ClientSessionHeaders.CLIENT_SESSION_ID, required = false) String clientSessionId) {
        LOGGER.info("Session claim request");
        return ResponseEntity.ok(authService.claimSession(request, clientSessionId));
    }

    @GetMapping(value = "/session/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Stream session events",
            description = "Open a live stream for session lifecycle events such as session replacement."
    )
    public SseEmitter streamSessionEvents(
            @RequestAttribute("userId") UUID userId,
            @RequestHeader(value = mofo.com.pestscout.auth.security.ClientSessionHeaders.CLIENT_SESSION_ID, required = false) String clientSessionId) {
        LOGGER.debug("Opening session event stream for user {}", userId);
        return authService.subscribeSessionEvents(userId, clientSessionId);
    }

    /**
     * Get the authenticated user's profile.
     */
    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get current user",
            description = "Get authenticated user's information",
            responses = {
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "200",
                            description = "User found",
                            content = @io.swagger.v3.oas.annotations.media.Content(
                                    mediaType = "application/json",
                                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = UserDto.class)
                            )
                    ),
                    @io.swagger.v3.oas.annotations.responses.ApiResponse(
                            responseCode = "401",
                            description = "Unauthorized"
                    )
            }
    )
    public ResponseEntity<UserDto> getCurrentUser(@RequestAttribute("userId") UUID userId) {
        LOGGER.info("Get current user request: {}", userId);
        UserDto user = authService.getCurrentUser(userId);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Update current user profile",
            description = "Update the authenticated user's profile details such as name, email, phone number, and country."
    )
    public ResponseEntity<UserDto> updateCurrentUser(
            @Valid @RequestBody UpdateUserRequest request,
            @RequestAttribute("userId") UUID userId) {
        LOGGER.info("Update current user profile request: {}", userId);
        return ResponseEntity.ok(userService.updateUser(userId, request, userId));
    }

    /**
     * Get a specific user by ID, enforcing authorization rules.
     */
    @GetMapping("/users/{userId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get user by ID",
            description = "Get user details by ID. Access is controlled based on the caller's role and farm memberships."
    )
    public ResponseEntity<UserDto> getUserById(
            @PathVariable UUID userId,
            @RequestAttribute("userId") UUID requestingUserId) {

        LOGGER.info("Get user request: {} by requester: {}", userId, requestingUserId);
        UserDto user = userService.getUserById(userId, requestingUserId);
        return ResponseEntity.ok(user);
    }

    /**
     * Get all users for the current farm.
     */
    @GetMapping("/users")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get all users",
            description = "Get all users for the current farm. " +
                    "Managers/Farm Admins must belong to the farm; Super Admin can access any farm."
    )
    public ResponseEntity<List<UserDto>> getUsers(
            @RequestParam(required = false) UUID farmId,
            @RequestAttribute(value = "farmId", required = false) UUID contextFarmId,
            @RequestAttribute("userId") UUID requestingUserId) {

        UUID effectiveFarmId = farmId != null ? farmId : contextFarmId;
        LOGGER.info("Get users request for farm {} by requester {}", effectiveFarmId, requestingUserId);
        List<UserDto> users = userService.getUsersByFarm(effectiveFarmId, requestingUserId);
        return ResponseEntity.ok(users);
    }

    /**
     * Get users by role within the current farm.
     */
    @GetMapping("/users/role/{role}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get users by role",
            description = "Get all users with a specific role within the current farm."
    )
    public ResponseEntity<List<UserDto>> getUsersByRole(
            @PathVariable Role role,
            @RequestParam(required = false) UUID farmId,
            @RequestAttribute(value = "farmId", required = false) UUID contextFarmId,
            @RequestAttribute("userId") UUID requestingUserId) {

        UUID effectiveFarmId = farmId != null ? farmId : contextFarmId;
        LOGGER.info("Get users by role {} for farm {} by requester {}", role, effectiveFarmId, requestingUserId);
        List<UserDto> users = userService.getUsersByFarmAndRole(effectiveFarmId, role, requestingUserId);
        return ResponseEntity.ok(users);
    }

    /**
     * Search users by name or email within the current farm.
     */
    @GetMapping("/users/search")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Search users", description = "Search users by name or email")
    public ResponseEntity<PagedResponse<UserDto>> searchUsers(
            @RequestParam String q,
            @RequestParam(required = false) UUID farmId,
            @RequestAttribute(value = "farmId", required = false) UUID contextFarmId,
            @RequestAttribute("userId") UUID requestingUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID effectiveFarmId = farmId != null ? farmId : contextFarmId;
        LOGGER.info("Search users request: '{}' for farm: {} by user: {}", q, effectiveFarmId, requestingUserId);

        Pageable pageable = PageRequest.of(page, size);
        Page<UserDto> users = userService.searchUsers(effectiveFarmId, q, pageable, requestingUserId);

        PagedResponse<UserDto> body = new PagedResponse<>(
                users.getContent(),
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages(),
                users.isLast()
        );

        return ResponseEntity.ok(body);
    }


    /**
     * Update a user's details.
     */
    @PutMapping("/users/{userId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Update user",
            description = "Update user information. Only authorized roles may update other users."
    )
    public ResponseEntity<UserDto> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request,
            @RequestAttribute("userId") UUID requestingUserId) {

        LOGGER.info("Update user {} by requester {}", userId, requestingUserId);
        UserDto user = userService.updateUser(userId, request, requestingUserId);
        return ResponseEntity.ok(user);
    }

    /**
     * Soft-delete (disable) a user.
     */
    @DeleteMapping("/users/{userId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Delete user",
            description = "Soft delete (disable) a user. Actual data is retained for audit purposes."
    )
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID userId,
            @RequestAttribute("userId") UUID requestingUserId) {

        LOGGER.info("Delete user {} by requester {}", userId, requestingUserId);
        userService.deleteUser(userId, requestingUserId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Aggregate statistics for users in the current farm.
     */
    @GetMapping("/users/stats")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get user statistics",
            description = "Get basic statistics (total/active/inactive users) for the current farm."
    )
    public ResponseEntity<UserStatsDto> getUserStats(
            @RequestParam(required = false) UUID farmId,
            @RequestAttribute(value = "farmId", required = false) UUID contextFarmId,
            @RequestAttribute("userId") UUID requestingUserId) {

        UUID effectiveFarmId = farmId != null ? farmId : contextFarmId;
        LOGGER.info("Get user stats for farm {} by requester {}", effectiveFarmId, requestingUserId);

        long totalUsers = userService.getUserCount(effectiveFarmId, requestingUserId);
        long activeUsers = userService.getActiveUserCount(effectiveFarmId, requestingUserId);

        UserStatsDto stats = new UserStatsDto(
                totalUsers,
                activeUsers,
                totalUsers - activeUsers
        );

        return ResponseEntity.ok(stats);
    }
}

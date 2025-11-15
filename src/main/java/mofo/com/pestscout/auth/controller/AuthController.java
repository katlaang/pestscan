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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Authentication Controller.
 * <p>
 * Exposes endpoints for:
 * <ul>
 *     <li>User authentication (login, refresh, current user)</li>
 *     <li>User management within the multi-tenant farm context</li>
 * </ul>
 */
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
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LOGGER.info("Login request for email: {}", request.email());
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Register a new user.
     */
    @PostMapping("/register")
    @Operation(
            summary = "User registration",
            description = "Register a new user in the system. Email must be unique.",
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
        LOGGER.info("Registration request for email: {} (role: {})", request.email(), request.role());
        UserDto user = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
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
    public ResponseEntity<LoginResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        LOGGER.info("Token refresh request");
        LoginResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
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
            @RequestAttribute("farmId") UUID farmId,
            @RequestAttribute("userId") UUID requestingUserId) {

        LOGGER.info("Get users request for farm {} by requester {}", farmId, requestingUserId);
        List<UserDto> users = userService.getUsersByFarm(farmId, requestingUserId);
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
            @RequestAttribute("farmId") UUID farmId,
            @RequestAttribute("userId") UUID requestingUserId) {

        LOGGER.info("Get users by role {} for farm {} by requester {}", role, farmId, requestingUserId);
        List<UserDto> users = userService.getUsersByFarmAndRole(farmId, role, requestingUserId);
        return ResponseEntity.ok(users);
    }

    /**
     * Search users by name or email within the current farm.
     */
    @GetMapping("/users/search")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Search users",
            description = "Search users by name or email within the current farm."
    )
    public ResponseEntity<Page<UserDto>> searchUsers(
            @RequestParam String q,
            @RequestAttribute("farmId") UUID farmId,
            @RequestAttribute("userId") UUID requestingUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LOGGER.info("Search users '{}' for farm {} by requester {}", q, farmId, requestingUserId);
        Pageable pageable = PageRequest.of(page, size);
        Page<UserDto> users = userService.searchUsers(farmId, q, pageable, requestingUserId);
        return ResponseEntity.ok(users);
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
            @RequestAttribute("farmId") UUID farmId,
            @RequestAttribute("userId") UUID requestingUserId) {

        LOGGER.info("Get user stats for farm {} by requester {}", farmId, requestingUserId);

        long totalUsers = userService.getUserCount(farmId, requestingUserId);
        long activeUsers = userService.getActiveUserCount(farmId, requestingUserId);

        UserStatsDto stats = new UserStatsDto(
                totalUsers,
                activeUsers,
                totalUsers - activeUsers
        );

        return ResponseEntity.ok(stats);
    }
}

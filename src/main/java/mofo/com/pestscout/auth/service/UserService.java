
package mofo.com.pestscout.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.auth.dto.UpdateUserRequest;
import mofo.com.pestscout.auth.dto.UserDto;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.model.UserFarmMembership;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.exception.UnauthorizedException;
import mofo.com.pestscout.common.service.CacheService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User Management Service.
 * <p>
 * Handles CRUD operations for users with authorization rules:
 * - SUPER_ADMIN: full access to all users and farms.
 * - MANAGER / FARM_ADMIN: can access users attached to the same farm.
 * - SCOUT: can only access their own user record.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@RequestMapping("api/")
public class UserService {

    private final UserRepository userRepository;
    private final CustomerNumberService customerNumberService;
    private final UserFarmMembershipRepository membershipRepository;
    private final PasswordPolicyService passwordPolicyService;
    private final CacheService cacheService;

    /**
     * Validate if the requesting user is allowed to access the target user.
     * <p>
     * This does not know which farm is in context. It only checks:
     * - SUPER_ADMIN: always allowed.
     * - SCOUT: only self.
     * - MANAGER / FARM_ADMIN: allowed if they are attached to the same farm.
     */
    private void validateAccess(User requester, User targetUser) {
        Role requesterRole = requester.getRole();

        // SUPER_ADMIN: full access
        if (requesterRole == Role.SUPER_ADMIN) {
            return;
        }

        // SCOUT: can only access themselves
        if (requesterRole == Role.SCOUT) {
            if (!requester.getId().equals(targetUser.getId())) {
                log.warn("Unauthorized SCOUT access attempt. requester={}, target={}",
                        requester.getId(), targetUser.getId());
                throw new UnauthorizedException("Scouts can only access their own profile");
            }
            return;
        }

        // MANAGER / FARM_ADMIN: must share at least one farm with the target user
        if (requesterRole == Role.MANAGER || requesterRole == Role.FARM_ADMIN) {
            List<UUID> requesterFarmIds = membershipRepository.findByUser_Id(requester.getId())
                    .stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                    .map(m -> m.getFarm().getId())
                    .toList();

            List<UUID> targetFarmIds = membershipRepository.findByUser_Id(targetUser.getId())
                    .stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                    .map(m -> m.getFarm().getId())
                    .toList();

            boolean sharedFarm = targetFarmIds.stream()
                    .anyMatch(requesterFarmIds::contains);

            if (!sharedFarm) {
                log.warn("User {} attempted to access user {} with no shared farm",
                        requester.getId(), targetUser.getId());
                throw new UnauthorizedException("You do not manage any farm for this user");
            }
        }
    }

    /**
     * Get a single user by ID with authorization.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = "users",
            keyGenerator = "tenantAwareKeyGenerator",
            unless = "#result == null"
    )
    public UserDto getUserById(UUID userId, UUID requestingUserId) {
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validateAccess(requester, targetUser);
        log.info("User {} accessed user {}", requestingUserId, userId);

        return convertToDto(targetUser);
    }

    /**
     * Get all users for a given farm.
     * <p>
     * SUPER_ADMIN: can list any farm.
     * MANAGER / FARM_ADMIN: must have membership in that farm.
     * SCOUT: normally would not list users - you can decide to block or allow.
     */
    @Transactional(readOnly = true)
    public List<UserDto> getUsersByFarm(UUID farmId, UUID requestingUserId) {
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        Role requesterRole = requester.getRole();

        if (farmId == null) {
            if (requesterRole != Role.SUPER_ADMIN) {
                throw new BadRequestException("Farm context is required to list users");
            }
            return convertToDtosWithPrimaryFarm(userRepository.findAll());
        }

        if (requesterRole != Role.SUPER_ADMIN) {
            boolean hasMembership = membershipRepository.existsByUser_IdAndFarmIdAndIsActiveTrue(requester.getId(), farmId);
            if (!hasMembership) {
                log.warn("User {} attempted to list users for unauthorized farm {}",
                        requestingUserId, farmId);
                throw new UnauthorizedException("You do not have access to this farm");
            }
        }

        // Fetch all memberships for this farm and map to distinct users
        List<UserFarmMembership> memberships = membershipRepository.findByFarmId(farmId);
        return memberships.stream()
                .map(UserFarmMembership::getUser)
                .distinct()
                .map(user -> convertToDto(user, farmId))
                .collect(Collectors.toList());
    }

    /**
     * Update user details with authorization.
     */
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public UserDto updateUser(UUID userId, UpdateUserRequest request, UUID requestingUserId) {
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validateAccess(requester, targetUser);

        log.info("User {} updating user {}", requestingUserId, userId);

        // Email update
        if (request.getEmail() != null && !request.getEmail().equals(targetUser.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("Email already in use");
            }
            targetUser.setEmail(request.getEmail());
        }

        // Basic fields
        if (request.getFirstName() != null) {
            targetUser.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            targetUser.setLastName(request.getLastName());
        }
        if (request.getPhoneNumber() != null) {
            targetUser.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getCountry() != null) {
            targetUser.setCountry(customerNumberService.normalizeCountryCode(request.getCountry()));
        }
        if (request.getRole() != null) {
            targetUser.setRole(request.getRole());
        }
        if (request.getIsEnabled() != null) {
            targetUser.setIsEnabled(request.getIsEnabled());
        }

        if (request.getPassword() != null) {
            passwordPolicyService.validateAndApplyPassword(targetUser, request.getPassword());
        }

        User updated = userRepository.save(targetUser);
        if (request.getPassword() != null) {
            passwordPolicyService.recordPassword(updated, request.getPassword());
        }
        log.info("User updated successfully: {}", updated.getEmail());

        cacheService.evictUserCache(userId);

        return convertToDto(updated);
    }

    /**
     * Soft delete a user by disabling them.
     */
    @Transactional
    @CacheEvict(value = "users", allEntries = true)
    public void deleteUser(UUID userId, UUID requestingUserId) {
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validateAccess(requester, targetUser);

        targetUser.setIsEnabled(false);
        userRepository.save(targetUser);

        log.info("User {} disabled user {}", requestingUserId, userId);

        cacheService.evictUserCache(userId);
    }

    /**
     * Get all users for a given farm and role.
     */
    @Transactional(readOnly = true)
    public List<UserDto> getUsersByFarmAndRole(UUID farmId, Role role, UUID requestingUserId) {
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        Role requesterRole = requester.getRole();

        if (farmId == null) {
            if (requesterRole != Role.SUPER_ADMIN) {
                throw new BadRequestException("Farm context is required to list users by role");
            }
            return convertToDtosWithPrimaryFarm(userRepository.findByRole(role));
        }

        if (requesterRole != Role.SUPER_ADMIN) {
            boolean hasMembership = membershipRepository.existsByUser_IdAndFarmIdAndIsActiveTrue(requester.getId(), farmId);
            if (!hasMembership) {
                log.warn("User {} attempted to list users by role for unauthorized farm {}",
                        requestingUserId, farmId);
                throw new UnauthorizedException("You do not have access to this farm");
            }
        }

        List<UserFarmMembership> memberships =
                membershipRepository.findByFarmIdAndRole(farmId, role);

        return memberships.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .map(UserFarmMembership::getUser)
                .distinct()
                .map(user -> convertToDto(user, farmId))
                .collect(Collectors.toList());
    }

    /**
     * Search users by name or email within a farm.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UserDto> searchUsers(
            UUID farmId,
            String query,
            org.springframework.data.domain.Pageable pageable,
            UUID requestingUserId) {

        // Resolve and validate the requesting user
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        Role requesterRole = requester.getRole();

        if (farmId == null) {
            if (requesterRole != Role.SUPER_ADMIN) {
                throw new BadRequestException("Farm context is required to search users");
            }

            org.springframework.data.domain.Page<User> page =
                    userRepository.findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                            query,
                            query,
                            query,
                            pageable
                    );

            List<UserDto> content = convertToDtosWithPrimaryFarm(page.getContent());
            return new PageImpl<>(content, pageable, page.getTotalElements());
        }

        // SUPER_ADMIN can search any farm
        if (requesterRole != Role.SUPER_ADMIN) {
            boolean hasMembership = membershipRepository.existsByUser_IdAndFarmIdAndIsActiveTrue(requester.getId(), farmId);
            if (!hasMembership) {
                log.warn("User {} attempted to search users for unauthorized farm {}",
                        requestingUserId, farmId);
                throw new UnauthorizedException("You do not have access to this farm");
            }
        }

        // Use membership-based search instead of userRepository.searchUsers(...)
        org.springframework.data.domain.Page<User> page =
                membershipRepository.searchActiveUsersInFarm(farmId, query, pageable);

        return page.map(this::convertToDto);
    }


    /**
     * Count distinct users attached to a farm.
     */
    @Transactional(readOnly = true)
    public long getUserCount(UUID farmId, UUID requestingUserId) {
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        Role requesterRole = requester.getRole();

        if (farmId == null) {
            if (requesterRole != Role.SUPER_ADMIN) {
                throw new BadRequestException("Farm context is required to view user stats");
            }
            return userRepository.findAll().stream()
                    .distinct()
                    .count();
        }

        if (requesterRole != Role.SUPER_ADMIN) {
            boolean hasMembership = membershipRepository.existsByUser_IdAndFarmIdAndIsActiveTrue(requester.getId(), farmId);
            if (!hasMembership) {
                log.warn("User {} attempted to view stats for unauthorized farm {}",
                        requestingUserId, farmId);
                throw new UnauthorizedException("You do not have access to this farm");
            }
        }

        return membershipRepository.findByFarmId(farmId).stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .map(UserFarmMembership::getUser)
                .distinct()
                .count();
    }

    /**
     * Count distinct active users attached to a farm.
     */
    @Transactional(readOnly = true)
    public long getActiveUserCount(UUID farmId, UUID requestingUserId) {
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        Role requesterRole = requester.getRole();

        if (farmId == null) {
            if (requesterRole != Role.SUPER_ADMIN) {
                throw new BadRequestException("Farm context is required to view active-user stats");
            }
            return userRepository.findAll().stream()
                    .filter(User::isActive)
                    .distinct()
                    .count();
        }

        if (requesterRole != Role.SUPER_ADMIN) {
            boolean hasMembership = membershipRepository.existsByUser_IdAndFarmIdAndIsActiveTrue(requester.getId(), farmId);
            if (!hasMembership) {
                log.warn("User {} attempted to view active-user stats for unauthorized farm {}",
                        requestingUserId, farmId);
                throw new UnauthorizedException("You do not have access to this farm");
            }
        }

        return membershipRepository.findByFarmId(farmId).stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                .map(UserFarmMembership::getUser)
                .filter(user -> Boolean.TRUE.equals(user.getIsEnabled()))
                .distinct()
                .count();
    }


    /**
     * Convert User entity to UserDto.
     * This stays flat and does not expose farm membership directly.
     * Farm memberships can be fetched separately when needed.
     */
    public UserDto convertToDto(User user) {
        return convertToDto(user, resolvePrimaryFarmId(user.getId()));
    }

    public UserDto convertToDto(User user, UUID farmId) {
        Long passwordExpiryWarningDaysRemaining = passwordPolicyService.getPasswordExpiryWarningDaysRemaining(user);
        return UserDto.builder()
                .id(user.getId())
                .farmId(farmId)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .country(user.getCountry())
                .customerNumber(user.getCustomerNumber())
                .role(user.getRole())
                .isEnabled(user.getIsEnabled())
                .active(user.isActive())
                .deleted(user.isDeleted())
                .passwordChangeRequired(user.requiresPasswordChange())
                .reactivationRequired(user.getReactivationRequired())
                .passwordExpired(user.isPasswordExpired())
                .passwordExpiresAt(user.getPasswordExpiresAt())
                .passwordExpiryWarningRequired(passwordExpiryWarningDaysRemaining != null)
                .passwordExpiryWarningDaysRemaining(passwordExpiryWarningDaysRemaining)
                .passwordExpiryWarningMessage(passwordPolicyService.getPasswordExpiryWarningMessage(user))
                .temporaryPasswordExpiresAt(user.getTemporaryPasswordExpiresAt())
                .deletedAt(user.getDeletedAt())
                .lastLogin(user.getLastLogin())
                .lastActivityAt(user.getLastActivityAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private List<UserDto> convertToDtosWithPrimaryFarm(List<User> users) {
        if (users.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds = users.stream()
                .map(User::getId)
                .toList();

        Map<UUID, UUID> primaryFarmIdsByUserId = membershipRepository.findByUser_IdInAndIsActiveTrue(userIds).stream()
                .filter(membership -> membership.getFarm() != null && membership.getUser() != null)
                .collect(Collectors.toMap(
                        membership -> membership.getUser().getId(),
                        membership -> membership.getFarm().getId(),
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));

        return users.stream()
                .map(user -> convertToDto(user, primaryFarmIdsByUserId.get(user.getId())))
                .collect(Collectors.toList());
    }

    private UUID resolvePrimaryFarmId(UUID userId) {
        if (userId == null) {
            return null;
        }

        return membershipRepository.findByUser_Id(userId).stream()
                .filter(membership -> Boolean.TRUE.equals(membership.getIsActive()))
                .map(membership -> membership.getFarm().getId())
                .findFirst()
                .orElse(null);
    }
}

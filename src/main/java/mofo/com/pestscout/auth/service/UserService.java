
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
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.exception.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User Management Service.
 * <p>
 * Handles CRUD operations for users with authorization rules:
 * - SUPER_ADMIN: full access to all users and farms.
 * - MANAGER / FARM_ADMIN: can access users that share at least one farm membership.
 * - SCOUT: can only access their own user record.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserFarmMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Validate if the requesting user is allowed to access the target user.
     * <p>
     * This does not know which farm is in context. It only checks:
     * - SUPER_ADMIN: always allowed.
     * - SCOUT: only self.
     * - MANAGER / FARM_ADMIN: allowed if they share at least one farm membership.
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

        if (requesterRole != Role.SUPER_ADMIN) {
            boolean hasMembership = membershipRepository.existsByUser_IdAndFarmId(requester.getId(), farmId);
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
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Update user details with authorization.
     */
    @Transactional
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

        // Password update
        if (request.getPassword() != null) {
            targetUser.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // Basic fields
        if (request.getFirstName() != null) {
            targetUser.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            targetUser.setLastName(request.getLastName());
        }
        if (request.getRole() != null) {
            targetUser.setRole(request.getRole());
        }
        if (request.getIsEnabled() != null) {
            targetUser.setIsEnabled(request.getIsEnabled());
        }

        User updated = userRepository.save(targetUser);
        log.info("User updated successfully: {}", updated.getEmail());

        return convertToDto(updated);
    }

    /**
     * Soft delete a user by disabling them.
     */
    @Transactional
    public void deleteUser(UUID userId, UUID requestingUserId) {
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        validateAccess(requester, targetUser);

        targetUser.setIsEnabled(false);
        userRepository.save(targetUser);

        log.info("User {} disabled user {}", requestingUserId, userId);
    }

    /**
     * Get all users for a given farm and role.
     */
    @Transactional(readOnly = true)
    public List<UserDto> getUsersByFarmAndRole(UUID farmId, Role role, UUID requestingUserId) {
        User requester = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new UnauthorizedException("Invalid requesting user"));

        Role requesterRole = requester.getRole();

        if (requesterRole != Role.SUPER_ADMIN) {
            boolean hasMembership = membershipRepository.existsByUser_IdAndFarmId(requester.getId(), farmId);
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
                .map(this::convertToDto)
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

        // SUPER_ADMIN can search any farm
        if (requesterRole != Role.SUPER_ADMIN) {
            boolean hasMembership = membershipRepository.existsByUser_IdAndFarmId(requester.getId(), farmId);
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

        if (requesterRole != Role.SUPER_ADMIN) {
            boolean hasMembership = membershipRepository.existsByUser_IdAndFarmId(requester.getId(), farmId);
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

        if (requesterRole != Role.SUPER_ADMIN) {
            boolean hasMembership = membershipRepository.existsByUser_IdAndFarmId(requester.getId(), farmId);
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
    UserDto convertToDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .isEnabled(user.getIsEnabled())
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

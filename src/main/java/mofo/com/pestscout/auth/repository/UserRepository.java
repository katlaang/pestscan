package mofo.com.pestscout.auth.repository;

import jakarta.persistence.LockModeType;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity.
 *
 * This repository is now farm agnostic. All farm scoped logic
 * is handled via {@link mofo.com.pestscout.auth.model.UserFarmMembership}
 * and {@link mofo.com.pestscout.auth.repository.UserFarmMembershipRepository}.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email (for authentication).
     */
    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Check if email exists globally.
     */
    boolean existsByEmail(String email);

    /**
     * Check if a customer number is already in use.
     */
    boolean existsByCustomerNumber(String customerNumber);

    /**
     * Find all users with a given role (not farm filtered).
     * Farm scoping belongs to UserFarmMembership.
     */
    List<User> findByRole(Role role);

    /**
     * Check whether a non-deleted user exists for the requested role.
     */
    boolean existsByRoleAndDeletedFalse(Role role);

    List<User> findByDeletedFalseAndPasswordChangeRequiredTrueAndTemporaryPasswordExpiresAtBefore(LocalDateTime cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update User u
               set u.lastActivityAt = :activityAt
             where u.id = :userId
               and (u.lastActivityAt is null or u.lastActivityAt < :minimumPreviousActivityAt)
            """)
    int updateLastActivityAtIfStale(@Param("userId") UUID userId,
                                    @Param("activityAt") LocalDateTime activityAt,
                                    @Param("minimumPreviousActivityAt") LocalDateTime minimumPreviousActivityAt);

    /**
     * Basic text search over user fields (no farm filter).
     * If you need farm scoped search, use UserFarmMembershipRepository
     * and join on the user.
     */
    Page<User> findByEmailContainingIgnoreCaseOrFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String emailPart,
            String firstNamePart,
            String lastNamePart,
            Pageable pageable
    );
}

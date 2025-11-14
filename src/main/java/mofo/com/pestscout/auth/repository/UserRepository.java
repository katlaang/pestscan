package mofo.com.pestscout.auth.repository;


import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity
 * Provides database access methods with multi-tenant filtering
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email (for authentication)
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by email and farm (multi-tenant check)
     */
    Optional<User> findByEmailAndFarmId(String email, UUID farmId);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Find all users by farm (multi-tenant)
     */
    List<User> findByFarmId(UUID farmId);

    /**
     * Find users by farm with pagination
     */
    Page<User> findByFarmId(UUID farmId, Pageable pageable);

    /**
     * Find users by farm and role
     */
    List<User> findByFarmIdAndRole(UUID farmId, Role role);

    /**
     * Find enabled users by farm
     */
    List<User> findByFarmIdAndIsEnabled(UUID farmId, Boolean isEnabled);

    /**
     * Count users by farm
     */
    long countByFarmId(UUID farmId);

    /**
     * Count active users by farm
     */
    long countByFarmIdAndIsEnabled(UUID farmId, Boolean isEnabled);

    /**
     * Search users by name or email (for admin search)
     */
    @Query("SELECT u FROM User u WHERE u.farmId = :farmId AND " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<User> searchUsers(@Param("farmId") UUID farmId,
                           @Param("searchTerm") String searchTerm,
                           Pageable pageable);

    /**
     * Find all super admins (no farm filtering)
     */
    List<User> findByRole(Role role);
}

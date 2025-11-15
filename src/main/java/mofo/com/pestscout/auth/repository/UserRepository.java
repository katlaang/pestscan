package mofo.com.pestscout.auth.repository;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

    /**
     * Check if email exists globally.
     */
    boolean existsByEmail(String email);

    /**
     * Find all users with a given role (not farm filtered).
     * Farm scoping belongs to UserFarmMembership.
     */
    List<User> findByRole(Role role);

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

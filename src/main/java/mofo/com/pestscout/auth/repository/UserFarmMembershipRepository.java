package mofo.com.pestscout.auth.repository;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.UserFarmMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFarmMembershipRepository extends JpaRepository<UserFarmMembership, UUID> {

    /**
     * All memberships for a given user (used after login to show farm list).
     */
    List<UserFarmMembership> findByUser_Id(UUID userId);

    /**
     * All memberships for a given farm.
     * Used when you want to list all users attached to a farm.
     */
    List<UserFarmMembership> findByFarmId(UUID farmId);

    /**
     * Membership for a given user and farm, if any.
     * Used to authorize a request in a specific farm context.
     */
    Optional<UserFarmMembership> findByUser_IdAndFarmId(UUID userId, UUID farmId);

    /**
     * All users with a given role in a farm (for admin screens).
     */
    List<UserFarmMembership> findByFarmIdAndRole(UUID farmId, Role role);

    /**
     * Quick check if user belongs to a farm at all.
     */
    boolean existsByUser_IdAndFarmId(UUID userId, UUID farmId);
}

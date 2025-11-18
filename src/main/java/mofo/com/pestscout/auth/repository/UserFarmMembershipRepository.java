package mofo.com.pestscout.auth.repository;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.model.UserFarmMembership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Search active users in a given farm by name or email.
     */
    @Query("""
            select u
            from UserFarmMembership m
            join m.user u
            where m.farm.id = :farmId
              and m.isActive = true
              and (
                   lower(u.firstName) like lower(concat('%', :searchTerm, '%'))
                or lower(u.lastName)  like lower(concat('%', :searchTerm, '%'))
                or lower(u.email)     like lower(concat('%', :searchTerm, '%'))
              )
            """)
    Page<User> searchActiveUsersInFarm(
            @Param("farmId") UUID farmId,
            @Param("searchTerm") String searchTerm,
            Pageable pageable
    );

}

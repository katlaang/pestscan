package mofo.com.pestscout.auth.model;

import jakarta.persistence.*;
import lombok.*;
import mofo.com.pestscout.common.model.BaseEntity;

import java.util.UUID;

/**
 * Links a user to a specific farm with a specific role.
 * <p>
 * Examples:
 * - Scout working only on Farm A   -> 1 row (user, farmA, SCOUT)
 * - Manager across Farm A and B   -> 2 rows (user, farmA, MANAGER), (user, farmB, MANAGER)
 */
@Entity
@Table(
        name = "user_farm_memberships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_farm",
                        columnNames = {"user_id", "farm_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFarmMembership extends BaseEntity {

    /**
     * Global user identity.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Farm (tenant) this membership applies to.
     * <p>
     * NOTE: using UUID here so we do not depend on a Farm entity yet.
     * Later you can refactor this to a @ManyToOne Farm relation.
     */
    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    /**
     * Role of the user in this specific farm.
     * Role enum is reused: SCOUT, MANAGER, FARM_ADMIN, SUPER_ADMIN.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private Role role;

    /**
     * Whether this membership is active.
     */
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}


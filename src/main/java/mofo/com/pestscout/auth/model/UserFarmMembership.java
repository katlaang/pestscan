package mofo.com.pestscout.auth.model;

import jakarta.persistence.*;
import lombok.*;
import mofo.com.pestscout.common.model.BaseEntity;
import mofo.com.pestscout.farm.model.Farm;

/**
 * Links a user to a single farm with a specific role.
 */
@Entity
@Table(
        name = "user_farm_memberships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_membership",
                        columnNames = {"user_id"}
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
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

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


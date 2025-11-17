package mofo.com.pestscout.farm.model;

import jakarta.persistence.*;
import lombok.*;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.model.BaseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One farm represents a commercial operation. Managers can own multiple farms.
 */
@Entity
@Table(
        name = "farms",
        indexes = {
                @Index(name = "idx_farms_name", columnList = "name"),
                @Index(name = "idx_farms_subscription_status", columnList = "subscription_status"),
                @Index(name = "idx_farms_farm_tag", columnList = "farm_tag", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Farm extends BaseEntity {

    /**
     * Optional short human friendly identifier for the farm.
     * Example: PS-0001 or FARM-123. This is what you can show in the UI.
     */
    @Column(name = "farm_tag", length = 32, unique = true)
    private String farmTag;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String description;

    /**
     * Optional external reference used for imports or integration
     * with third party systems or legacy farm identifiers.
     * If you do not use external systems, keep this null.
     */
    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(length = 255)
    private String address;

    /**
     * Latitude coordinate for the farm headquarters.
     * Stored with high precision to support location specific alerts.
     */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    /**
     * Longitude coordinate for the farm headquarters.
     */
    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String province;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 100)
    private String country;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scout_id")
    private User scout;

    @Column(name = "contact_name", length = 255)
    private String contactName;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 20)
    private SubscriptionStatus subscriptionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, length = 50)
    private SubscriptionTier subscriptionTier;

    @Column(name = "billing_email", length = 255)
    private String billingEmail;

    /**
     * Total licensed area for quota calculations (in hectares).
     */
    @Column(name = "licensed_area_hectares", precision = 10, scale = 2, nullable = false)
    private BigDecimal licensedAreaHectares;

    /**
     * Number of licensed production units tied to the subscription.
     */
    @Column(name = "licensed_unit_quota")
    private Integer licensedUnitQuota;

    /**
     * Discount percentage applied to the farm quota based on covered area.
     */
    @Column(name = "quota_discount_percentage", precision = 5, scale = 2)
    private BigDecimal quotaDiscountPercentage;

    /**
     * High level profile for the farm.
     * You can keep GREENHOUSE, FIELD, MIXED, OTHER.
     * Detailed layouts live in Greenhouse and FieldBlock.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "structure_type", nullable = false, length = 20)
    private FarmStructureType structureType;

    @Column(name = "timezone", length = 100)
    private String timezone;

    /**
     * Optional default layout for this farm.
     * Individual greenhouses or field blocks can override these values.
     */
    @Column(name = "default_bay_count")
    private Integer defaultBayCount;

    @Column(name = "default_benches_per_bay")
    private Integer defaultBenchesPerBay;

    @Column(name = "default_spot_checks_per_bench")
    private Integer defaultSpotChecksPerBench;


    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;

    /**
     * Greenhouse units belonging to this farm.
     */
    @Builder.Default
    @OneToMany(mappedBy = "farm", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Greenhouse> greenhouses = new ArrayList<>();

    /**
     * Open field blocks belonging to this farm.
     */
    @Builder.Default
    @OneToMany(mappedBy = "farm", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FieldBlock> fieldBlocks = new ArrayList<>();

    /**
     * Ensure safe defaults for new farms.
     * Activation must be done explicitly by billing or super admin.
     */
    @Override
    protected void applyPrePersistDefaults() {
        if (subscriptionStatus == null) {
            subscriptionStatus = SubscriptionStatus.PENDING_ACTIVATION;
        }
        if (subscriptionTier == null) {
            subscriptionTier = SubscriptionTier.BASIC;
        }
        if (country == null) {
            country = "Canada";
        }
        if (structureType == null) {
            structureType = FarmStructureType.GREENHOUSE;
        }
    }

    /**
     * Convenience method for subscription checks inside domain logic.
     */
    @Transient
    public boolean isActive() {
        return subscriptionStatus == SubscriptionStatus.ACTIVE;
    }

    /**
     * Resolve the default number of bays for this farm.
     * Greenhouses call this when they do not have a bayCount set explicitly.
     */
    @Transient
    public int resolveBayCount() {
        // Farm level default, can be configured per farm
        return defaultBayCount != null ? defaultBayCount : 1;
    }

    /**
     * Resolve the default number of benches per bay for this farm.
     */
    @Transient
    public int resolveBenchesPerBay() {
        return defaultBenchesPerBay != null ? defaultBenchesPerBay : 0;
    }

    /**
     * Resolve the default number of spot checks per bench for this farm.
     */
    @Transient
    public int resolveSpotChecksPerBench() {
        return defaultSpotChecksPerBench != null ? defaultSpotChecksPerBench : 1;
    }


    @Override
    public String toString() {
        return "Farm{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", city='" + city + '\'' +
                ", province='" + province + '\'' +
                ", structureType=" + structureType +
                ", subscriptionStatus=" + subscriptionStatus +
                ", subscriptionTier=" + subscriptionTier +
                '}';
    }
}

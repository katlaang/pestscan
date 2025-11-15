package mofo.com.pestscout.farm.model;

import jakarta.persistence.*;
import lombok.*;
import mofo.com.pestscout.common.model.BaseEntity;

import java.math.BigDecimal;

/**
 * One farm represents a commercial operation. Managers can own multiple farms.
 */
@Entity
@Table(
        name = "farms",
        indexes = {
                @Index(name = "idx_farms_name", columnList = "name"),
                @Index(name = "idx_farms_subscription_status", columnList = "subscription_status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Farm extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String description;

    /**
     * Optional external reference used for imports or integration
     * with third party systems or legacy farm identifiers.
     */
    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(length = 255)
    private String address;

    /**
     * Optional latitude coordinate for the farm headquarters.
     * Stored with high precision to support location-specific alerts.
     */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    /**
     * Optional longitude coordinate for the farm headquarters.
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
    @Column(name = "licensed_area_hectares", precision = 10, scale = 2)
    private BigDecimal licensedAreaHectares;

    /**
     * Number of licensed production units tied to the subscription.
     */
    @Column(name = "licensed_unit_quota")
    private Integer licensedUnitQuota;

    /**
     * Discount percentage applied to the farm's quota based on covered area.
     */
    @Column(name = "quota_discount_percentage", precision = 5, scale = 2)
    private BigDecimal quotaDiscountPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "structure_type", nullable = false, length = 20)
    private FarmStructureType structureType;

    @Column(name = "bay_count")
    private Integer bayCount;

    @Column(name = "benches_per_bay")
    private Integer benchesPerBay;

    @Column(name = "spot_checks_per_bench")
    private Integer spotChecksPerBench;

    @Column(name = "timezone", length = 100)
    private String timezone;

    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;

    /**
     * Ensure safe defaults for new farms.
     * Activation must be done explicitly by billing or super admin.
     */
//    @PrePersist
//    public void prePersist() {
//        if (subscriptionStatus == null) {
//            subscriptionStatus = SubscriptionStatus.PENDING_ACTIVATION;
//        }
//        if (subscriptionTier == null) {
//            subscriptionTier = SubscriptionTier.BASIC;
//        }
//        if (country == null) {
//            country = "Canada";
//        }
//    }

    /**
     * Convenience method for subscription checks inside domain logic.
     */
    @Transient
    public boolean isActive() {
        return subscriptionStatus == SubscriptionStatus.ACTIVE;
    }

    public int resolveBayCount() {
        return bayCount != null ? bayCount : 6;
    }

    public int resolveBenchesPerBay() {
        return benchesPerBay != null ? benchesPerBay : 5;
    }

    public int resolveSpotChecksPerBench() {
        return spotChecksPerBench != null ? spotChecksPerBench : 3;
    }

    @Override
    public String toString() {
        return "Farm{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", city='" + city + '\'' +
                ", province='" + province + '\'' +
                ", structureType=" + structureType +
                ", bayCount=" + bayCount +
                ", subscriptionStatus=" + subscriptionStatus +
                ", subscriptionTier=" + subscriptionTier +
                '}';
    }
}


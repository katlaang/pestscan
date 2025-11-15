package mofo.com.pestscout.farm.model;

import jakarta.persistence.*;
import lombok.*;
import mofo.com.pestscout.common.model.BaseEntity;

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

    @Override
    public String toString() {
        return "Farm{" +
                "id=" + getId() +
                ", name='" + name + '\'' +
                ", city='" + city + '\'' +
                ", province='" + province + '\'' +
                ", subscriptionStatus=" + subscriptionStatus +
                ", subscriptionTier=" + subscriptionTier +
                '}';
    }
}


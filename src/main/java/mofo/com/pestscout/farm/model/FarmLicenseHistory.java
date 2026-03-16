package mofo.com.pestscout.farm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "farm_license_history")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FarmLicenseHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @Column(name = "license_reference", nullable = false, length = 64)
    private String licenseReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private FarmLicenseAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 20)
    private SubscriptionStatus subscriptionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, length = 50)
    private SubscriptionTier subscriptionTier;

    @Column(name = "billing_email", length = 255)
    private String billingEmail;

    @Column(name = "licensed_area_hectares", nullable = false, precision = 10, scale = 2)
    private BigDecimal licensedAreaHectares;

    @Column(name = "quota_discount_percentage", precision = 5, scale = 2)
    private BigDecimal quotaDiscountPercentage;

    @Column(name = "effective_licensed_area_hectares", nullable = false, precision = 10, scale = 2)
    private BigDecimal effectiveLicensedAreaHectares;

    @Column(name = "license_expiry_date")
    private LocalDate licenseExpiryDate;

    @Column(name = "license_grace_period_end")
    private LocalDate licenseGracePeriodEnd;

    @Column(name = "license_archived_date")
    private LocalDate licenseArchivedDate;

    @Column(name = "auto_renew_enabled")
    private Boolean autoRenewEnabled;

    @Column(name = "is_archived")
    private Boolean archived;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name = "notes", length = 2000)
    private String notes;
}

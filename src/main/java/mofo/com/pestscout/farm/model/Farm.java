package mofo.com.pestscout.farm.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a licensed farm operation.
 * Licensing, subscription, ownership, and assigned scout are all enforced here.
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
@Slf4j
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Farm extends BaseEntity {

    // ─────────────────────────────────────────────────────────────
    // General Info
    // ─────────────────────────────────────────────────────────────

    @Column(name = "farm_tag", length = 32, unique = true)
    private String farmTag;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(length = 255)
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

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

    // ─────────────────────────────────────────────────────────────
    // Ownership & Assigned Staff
    // ─────────────────────────────────────────────────────────────

    /**
     * The farm owner (manager/farm admin).
     * Has full operational permissions.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * The primary scout assigned to this farm (optional).
     * Used for all session assignments.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scout_id")
    private User scout;

    // ─────────────────────────────────────────────────────────────
    // Contact Info
    // ─────────────────────────────────────────────────────────────

    @Column(name = "contact_name", length = 255)
    private String contactName;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    // ─────────────────────────────────────────────────────────────
    // Subscription & License Data
    // ─────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 20)
    private SubscriptionStatus subscriptionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, length = 50)
    private SubscriptionTier subscriptionTier;

    @Column(name = "billing_email", length = 255)
    private String billingEmail;

    /**
     * Licensed area (hectares). Only super admin can modify this.
     */
    @Column(name = "licensed_area_hectares", precision = 10, scale = 2, nullable = false)
    private BigDecimal licensedAreaHectares;

    /**
     * Licensed quota units. Only super admin can modify this.
     */
    @Column(name = "licensed_unit_quota")
    private Integer licensedUnitQuota;

    @Column(name = "quota_discount_percentage", precision = 5, scale = 2)
    private BigDecimal quotaDiscountPercentage;

    // ─────────────────────────────────────────────────────────────
    // LICENSE LIFE CYCLE
    // ─────────────────────────────────────────────────────────────

    /**
     * The subscription expiry date.
     */
    @Column(name = "license_expiry_date")
    private LocalDate licenseExpiryDate;

    /**
     * Expiry + 30 days: read-only mode.
     * After this, farm owner loses ALL access.
     */
    @Column(name = "license_grace_period_end")
    private LocalDate licenseGracePeriodEnd;

    /**
     * Grace period + 30 days: data is archived.
     */
    @Column(name = "license_archived_date")
    private LocalDate licenseArchivedDate;

    /**
     * Whether the farm is fully archived and inaccessible.
     */
    @Column(name = "is_archived")
    private Boolean isArchived;

    /**
     * If enabled, renewals happen automatically when Stripe confirms payment.
     * Super admin can toggle this.
     */
    @Column(name = "auto_renew_enabled")
    private Boolean autoRenewEnabled;

    // ─────────────────────────────────────────────────────────────
    // Structural Configuration
    // ─────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "structure_type", nullable = false, length = 20)
    private FarmStructureType structureType;

    @Column(name = "timezone", length = 100)
    private String timezone;

    @Column(name = "default_bay_count")
    private Integer defaultBayCount;

    @Column(name = "default_benches_per_bay")
    private Integer defaultBenchesPerBay;

    @Column(name = "default_spot_checks_per_bench")
    private Integer defaultSpotChecksPerBench;

    // Stripe Integration
    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 255)
    private String stripeSubscriptionId;

    // ─────────────────────────────────────────────────────────────
    // Child Structures
    // ─────────────────────────────────────────────────────────────

    @Builder.Default
    @OneToMany(mappedBy = "farm", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Greenhouse> greenhouses = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "farm", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FieldBlock> fieldBlocks = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────
    // DEFAULTS & UTILITIES
    // ─────────────────────────────────────────────────────────────

    @Override
    protected void applyPrePersistDefaults() {
        if (subscriptionStatus == null) subscriptionStatus = SubscriptionStatus.PENDING_ACTIVATION;
        if (subscriptionTier == null) subscriptionTier = SubscriptionTier.BASIC;
        if (country == null) country = "Canada";
        if (structureType == null) structureType = FarmStructureType.GREENHOUSE;
        if (isArchived == null) isArchived = false;
        if (autoRenewEnabled == null) autoRenewEnabled = false;
    }

    @Transient
    public boolean isActive() {
        return subscriptionStatus == SubscriptionStatus.ACTIVE;
    }

    @Transient
    public boolean isExpired() {
        return licenseExpiryDate != null && LocalDate.now().isAfter(licenseExpiryDate);
    }

    @Transient
    public boolean inGracePeriod() {
        return licenseGracePeriodEnd != null &&
                LocalDate.now().isAfter(licenseExpiryDate) &&
                LocalDate.now().isBefore(licenseGracePeriodEnd);
    }

    @Transient
    public boolean beyondGracePeriod() {
        return licenseGracePeriodEnd != null && LocalDate.now().isAfter(licenseGracePeriodEnd);
    }

    @Transient
    public boolean fullyArchived() {
        return isArchived != null && isArchived;
    }

    @Transient
    public int resolveBayCount() {
        return defaultBayCount != null ? defaultBayCount : 1;
    }

    @Transient
    public int resolveBenchesPerBay() {
        return defaultBenchesPerBay != null ? defaultBenchesPerBay : 0;
    }

    @Transient
    public int resolveSpotChecksPerBench() {
        return defaultSpotChecksPerBench != null ? defaultSpotChecksPerBench : 1;
    }
}

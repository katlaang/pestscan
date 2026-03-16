package mofo.com.pestscout.farm.dto;

import mofo.com.pestscout.farm.model.LicenseType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FarmLicenseResponse(
        UUID farmId,
        String farmName,
        String licenseReference,
        LicenseType licenseType,
        LocalDate licenseStartDate,
        Integer licenseExtensionMonths,
        SubscriptionStatus subscriptionStatus,
        SubscriptionTier subscriptionTier,
        String billingEmail,
        BigDecimal licensedAreaHectares,
        BigDecimal quotaDiscountPercentage,
        BigDecimal effectiveLicensedAreaHectares,
        LocalDate licenseExpiryDate,
        LocalDate licenseGracePeriodEnd,
        LocalDate licenseArchivedDate,
        Boolean autoRenewEnabled,
        Boolean archived,
        Instant expiryNotificationSentAt,
        Instant generatedAt,
        Instant updatedAt
) {
}

package mofo.com.pestscout.farm.dto;

import mofo.com.pestscout.farm.model.FarmLicenseAction;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FarmLicenseHistoryResponse(
        UUID historyId,
        String licenseReference,
        FarmLicenseAction action,
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
        String notes,
        UUID actorUserId,
        String actorEmail,
        Instant createdAt
) {
}

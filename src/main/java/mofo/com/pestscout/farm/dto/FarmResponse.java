package mofo.com.pestscout.farm.dto;

import mofo.com.pestscout.farm.model.FarmStructureType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FarmResponse(

        UUID id,
        String farmTag,

        String name,
        String description,
        String externalId,
        String address,
        String city,
        String province,
        String postalCode,
        String country,

        String contactName,
        String contactEmail,
        String contactPhone,

        SubscriptionStatus subscriptionStatus,
        SubscriptionTier subscriptionTier,
        String billingEmail,

        // license
        BigDecimal licensedAreaHectares,
        Integer licensedUnitQuota,
        BigDecimal quotaDiscountPercentage,
        LocalDate licenseExpiryDate,
        Boolean autoRenewEnabled,
        Boolean accessLocked,

        FarmStructureType structureType,

        Integer defaultBayCount,
        Integer defaultBenchesPerBay,
        Integer defaultSpotChecksPerBench,
        java.time.Instant createdAt,     // ← new
        java.time.Instant updatedAt,
        String timezone,

        UUID ownerId,
        UUID scoutId
) {
}

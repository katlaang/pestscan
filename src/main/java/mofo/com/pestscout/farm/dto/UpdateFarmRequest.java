package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.*;
import mofo.com.pestscout.farm.model.FarmStructureType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;

import java.math.BigDecimal;

/**
 * Request payload for creating or updating a farm.
 * Service layer decides which fields are allowed to change on update.
 */
public record UpdateFarmRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 500)
        String description,

        @Size(max = 255)
        String externalId,

        @Size(max = 255)
        String address,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String province,

        @Size(max = 20)
        String postalCode,

        @Size(max = 100)
        String country,

        @Size(max = 255)
        String contactName,

        @Email
        @Size(max = 255)
        String contactEmail,

        @Size(max = 50)
        String contactPhone,

        // usually null on create; may be changed by super admin on update
        SubscriptionStatus subscriptionStatus,

        SubscriptionTier subscriptionTier,

        @Email
        @Size(max = 255)
        String billingEmail,

        // GPS coordinates; optional but recommended
        BigDecimal latitude,
        BigDecimal longitude,

        @NotNull
        @DecimalMin("0.0")
        BigDecimal licensedAreaHectares,

        @Min(0)
        Integer licensedUnitQuota,

        @DecimalMin("0.0")
        BigDecimal quotaDiscountPercentage,

        FarmStructureType structureType,

        // farm-level defaults that greenhouses / fields can override
        @Min(0)
        Integer defaultBayCount,

        @Min(0)
        Integer defaultBenchesPerBay,

        @Min(0)
        Integer defaultSpotChecksPerBench,

        @Size(max = 100)
        String timezone
) {
}

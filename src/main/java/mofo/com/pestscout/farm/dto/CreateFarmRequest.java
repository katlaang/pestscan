package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import mofo.com.pestscout.farm.model.FarmStructureType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO used to create a farm.
 * Only SUPER_ADMIN can use this.
 * Includes license and billing fields.
 */
public record CreateFarmRequest(

        @NotBlank String name,
        String description,
        String externalId,

        String address,
        String city,
        String province,
        String postalCode,
        String country,

        @NotNull UUID ownerId,

        // Optional scout assignment on creation
        UUID scoutId,

        String contactName,
        @Email String contactEmail,
        String contactPhone,

        // Licensing fields (Superadmin only)
        @NotNull SubscriptionStatus subscriptionStatus,
        @NotNull SubscriptionTier subscriptionTier,

        @Email String billingEmail,

        @NotNull @DecimalMin("0.0") BigDecimal licensedAreaHectares,
        Integer licensedUnitQuota,
        BigDecimal quotaDiscountPercentage,

        FarmStructureType structureType,

        // Defaults for layout
        Integer defaultBayCount,
        Integer defaultBenchesPerBay,
        Integer defaultSpotChecksPerBench,

        List<CreateGreenhouseRequest> greenhouses,
        List<CreateFieldBlockRequest> fieldBlocks,

        String timezone,

        // License lifecycle management
        LocalDate licenseExpiryDate,
        Boolean autoRenewEnabled
) {
}



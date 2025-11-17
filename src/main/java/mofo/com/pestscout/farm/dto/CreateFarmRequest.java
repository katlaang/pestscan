package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.*;
import mofo.com.pestscout.farm.model.FarmStructureType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to create a new farm.
 * Only SUPER_ADMIN can perform this operation.
 */
public record CreateFarmRequest(

        @NotNull
        UUID ownerId,

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

        @NotNull
        @DecimalMin("0.0")
        BigDecimal licensedAreaHectares,

        @Min(0)
        Integer licensedUnitQuota,

        @DecimalMin("0.0")
        BigDecimal quotaDiscountPercentage,

        FarmStructureType structureType,

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


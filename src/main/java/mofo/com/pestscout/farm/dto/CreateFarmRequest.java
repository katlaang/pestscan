package mofo.com.pestscout.farm.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
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
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFarmRequest {

    @NotBlank
    private String name;
    private String description;

    private String address;
    private String city;
    private String province;
    private String postalCode;
    private String country;
    private Boolean organic;

    private UUID ownerId;

    private UUID scoutId;

    private String contactName;
    @Email
    private String contactEmail;
    private String contactPhone;

    @NotNull
    private SubscriptionStatus subscriptionStatus;
    @NotNull
    private SubscriptionTier subscriptionTier;

    @Email
    private String billingEmail;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal licensedAreaHectares;
    private Integer licensedUnitQuota;
    private BigDecimal quotaDiscountPercentage;

    private FarmStructureType structureType;

    private Integer defaultBayCount;
    private Integer defaultBenchesPerBay;
    private Integer defaultSpotChecksPerBench;

    private List<CreateGreenhouseRequest> greenhouses;
    private List<CreateFieldBlockRequest> fieldBlocks;

    private String timezone;

    private LocalDate licenseExpiryDate;
    private Boolean autoRenewEnabled;
    @JsonDeserialize(using = LatitudeDeserializer.class)
    private BigDecimal latitude;
    @JsonDeserialize(using = LongitudeDeserializer.class)
    private BigDecimal longitude;
    private List<FarmMemberAssignmentRequest> memberAssignments;
}

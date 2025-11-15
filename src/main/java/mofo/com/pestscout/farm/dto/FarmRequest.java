package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mofo.com.pestscout.farm.model.FarmStructureType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 500)
    private String description;

    @Size(max = 255)
    private String externalId;

    @Size(max = 255)
    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String province;

    @Size(max = 20)
    private String postalCode;

    @Size(max = 100)
    private String country;

    @Size(max = 255)
    private String contactName;

    @Size(max = 255)
    private String contactEmail;

    @Size(max = 50)
    private String contactPhone;

    private SubscriptionStatus subscriptionStatus;

    private SubscriptionTier subscriptionTier;

    @Size(max = 255)
    private String billingEmail;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private BigDecimal licensedAreaHectares;

    @Min(0)
    private Integer licensedUnitQuota;

    private BigDecimal quotaDiscountPercentage;

    private FarmStructureType structureType;

    @Min(0)
    private Integer bayCount;

    @Min(0)
    private Integer benchesPerBay;

    @Min(0)
    private Integer spotChecksPerBench;

    @Size(max = 100)
    private String timezone;

}

package mofo.com.pestscout.farm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mofo.com.pestscout.farm.model.FarmStructureType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmResponse {

    private UUID id;
    private String name;
    private String description;
    private String externalId;
    private String address;
    private String city;
    private String province;
    private String postalCode;
    private String country;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private SubscriptionStatus subscriptionStatus;
    private SubscriptionTier subscriptionTier;
    private String billingEmail;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal licensedAreaHectares;
    private Integer licensedUnitQuota;
    private BigDecimal quotaDiscountPercentage;
    private FarmStructureType structureType;
    private Integer bayCount;
    private Integer benchesPerBay;
    private Integer spotChecksPerBench;
    private String timezone;
}

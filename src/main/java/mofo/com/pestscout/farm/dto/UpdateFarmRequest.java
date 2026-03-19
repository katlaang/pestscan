package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpdateFarmRequest(

        @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        @Size(max = 255)
        String name,

        @Size(max = 500)
        String description,

        @Size(max = 255)
        String address,

        BigDecimal latitude,
        BigDecimal longitude,

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

        // License + billing (super admin only)
        SubscriptionStatus subscriptionStatus,
        SubscriptionTier subscriptionTier,

        @Email
        @Size(max = 255)
        String billingEmail,

        BigDecimal licensedAreaHectares,
        Integer licensedUnitQuota,
        BigDecimal quotaDiscountPercentage,

        LocalDate licenseExpiryDate,
        LocalDate licenseGracePeriodEnd,
        LocalDate licenseArchivedDate,
        Boolean autoRenewEnabled,
        Boolean isArchived,

        Integer defaultBayCount,
        Integer defaultBenchesPerBay,
        Integer defaultSpotChecksPerBench,

        @Size(max = 100)
        String timezone,

        java.util.UUID ownerId,
        java.util.UUID scoutId,
        Boolean accessLocked,
        List<FarmMemberAssignmentRequest> memberAssignments
) {
        public UpdateFarmRequest(
                String name,
                String description,
                String address,
                BigDecimal latitude,
                BigDecimal longitude,
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
                BigDecimal licensedAreaHectares,
                Integer licensedUnitQuota,
                BigDecimal quotaDiscountPercentage,
                LocalDate licenseExpiryDate,
                LocalDate licenseGracePeriodEnd,
                LocalDate licenseArchivedDate,
                Boolean autoRenewEnabled,
                Boolean isArchived,
                Integer defaultBayCount,
                Integer defaultBenchesPerBay,
                Integer defaultSpotChecksPerBench,
                String timezone,
                java.util.UUID ownerId,
                java.util.UUID scoutId,
                Boolean accessLocked
        ) {
                this(
                        name,
                        description,
                        address,
                        latitude,
                        longitude,
                        city,
                        province,
                        postalCode,
                        country,
                        contactName,
                        contactEmail,
                        contactPhone,
                        subscriptionStatus,
                        subscriptionTier,
                        billingEmail,
                        licensedAreaHectares,
                        licensedUnitQuota,
                        quotaDiscountPercentage,
                        licenseExpiryDate,
                        licenseGracePeriodEnd,
                        licenseArchivedDate,
                        autoRenewEnabled,
                        isArchived,
                        defaultBayCount,
                        defaultBenchesPerBay,
                        defaultSpotChecksPerBench,
                        timezone,
                        ownerId,
                        scoutId,
                        accessLocked,
                        null
                );
        }
}

package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import mofo.com.pestscout.farm.model.LicenseType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateFarmLicenseRequest(
        SubscriptionStatus subscriptionStatus,
        SubscriptionTier subscriptionTier,
        LicenseType licenseType,
        LocalDate licenseStartDate,
        Integer licenseExtensionMonths,

        @Email
        @Size(max = 255)
        String billingEmail,

        @DecimalMin("0.0")
        BigDecimal licensedAreaHectares,

        BigDecimal quotaDiscountPercentage,

        LocalDate licenseExpiryDate,
        LocalDate licenseGracePeriodEnd,
        LocalDate licenseArchivedDate,
        Boolean autoRenewEnabled,
        Boolean isArchived,

        @Size(max = 2000)
        String notes
) {

        public UpdateFarmLicenseRequest(
                SubscriptionStatus subscriptionStatus,
                SubscriptionTier subscriptionTier,
                String billingEmail,
                BigDecimal licensedAreaHectares,
                BigDecimal quotaDiscountPercentage,
                LocalDate licenseExpiryDate,
                LocalDate licenseGracePeriodEnd,
                LocalDate licenseArchivedDate,
                Boolean autoRenewEnabled,
                Boolean isArchived,
                String notes
        ) {
                this(
                        subscriptionStatus,
                        subscriptionTier,
                        null,
                        null,
                        null,
                        billingEmail,
                        licensedAreaHectares,
                        quotaDiscountPercentage,
                        licenseExpiryDate,
                        licenseGracePeriodEnd,
                        licenseArchivedDate,
                        autoRenewEnabled,
                        isArchived,
                        notes
                );
        }
}

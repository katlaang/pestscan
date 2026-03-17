package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.farm.config.LicensePolicyProperties;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.LicenseType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class LicenseService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final LicensePolicyProperties licensePolicyProperties;

    public LicenseService(LicensePolicyProperties licensePolicyProperties) {
        this.licensePolicyProperties = licensePolicyProperties;
    }

    /**
     * Applies a percentage discount to a base amount, returning a non-negative result
     * rounded to two decimals.
     */
    public BigDecimal applyDiscount(BigDecimal baseAmount, BigDecimal discountPercentage) {
        if (baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal effectiveDiscount = normalizePercentage(discountPercentage);
        BigDecimal discounted = baseAmount.multiply(BigDecimal.ONE.subtract(effectiveDiscount.divide(ONE_HUNDRED)));
        if (discounted.compareTo(BigDecimal.ZERO) < 0) {
            discounted = BigDecimal.ZERO;
        }
        return discounted.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Computes the licensed area after applying any quota discount.
     */
    public BigDecimal resolveEffectiveLicensedArea(Farm farm) {
        BigDecimal licensedArea = Optional.ofNullable(farm.getLicensedAreaHectares()).orElse(BigDecimal.ZERO);
        if (licensedArea.compareTo(BigDecimal.ZERO) < 0) {
            licensedArea = BigDecimal.ZERO;
        }
        return applyDiscount(licensedArea, farm.getQuotaDiscountPercentage());
    }

    /**
     * Normalizes and computes the license lifecycle dates from the configured commercial policy.
     * Existing explicit grace/archive values can still be overridden by callers after this method runs.
     */
    public void applyCommercialSchedule(Farm farm) {
        LicenseType licenseType = Optional.ofNullable(farm.getLicenseType()).orElse(LicenseType.PAID);
        int extensionMonths = normalizeExtensionMonths(licenseType, farm.getLicenseExtensionMonths());
        farm.setLicenseType(licenseType);
        farm.setLicenseExtensionMonths(extensionMonths);

        LocalDate startDate = resolveStartDate(farm, licenseType, extensionMonths);
        if (startDate == null) {
            return;
        }

        int totalMonths = baseMonthsFor(licenseType) + extensionMonths;
        LocalDate expiryDate = startDate.plusMonths(totalMonths).minusDays(1);

        farm.setLicenseStartDate(startDate);
        farm.setLicenseExpiryDate(expiryDate);

        if (farm.getLicenseGracePeriodEnd() == null || farm.getLicenseGracePeriodEnd().isBefore(expiryDate)) {
            farm.setLicenseGracePeriodEnd(expiryDate.plusDays(licensePolicyProperties.getDashboardVisibilityDaysAfterExpiry()));
        }

        if (farm.getLicenseArchivedDate() == null || farm.getLicenseArchivedDate().isBefore(farm.getLicenseGracePeriodEnd())) {
            farm.setLicenseArchivedDate(farm.getLicenseGracePeriodEnd());
        }
    }

    public void validateFarmLicenseActive(Farm farm) {
        assertOperationalAccess(farm);
    }

    public void assertOperationalAccess(Farm farm) {
        if (Boolean.TRUE.equals(farm.getIsArchived()) || farm.fullyArchived()) {
            throw new BadRequestException("Farm is archived and cannot use licensed features.");
        }
        if (farm.getSubscriptionStatus() != SubscriptionStatus.ACTIVE) {
            throw new BadRequestException("Farm license is not active.");
        }
        if (farm.isExpired()) {
            throw new BadRequestException("Farm license has expired. Only dashboards remain visible until the dashboard access window ends.");
        }
    }

    public void assertDashboardAccess(Farm farm) {
        if (Boolean.TRUE.equals(farm.getIsArchived()) || farm.fullyArchived()) {
            throw new ForbiddenException("Farm data has been archived and dashboards are no longer visible.");
        }
        if (farm.getSubscriptionStatus() != SubscriptionStatus.ACTIVE) {
            throw new ForbiddenException("Farm dashboards are unavailable because the license is not active.");
        }
        if (farm.getLicenseExpiryDate() == null) {
            return;
        }
        if (!farm.isExpired()) {
            return;
        }
        if (farm.beyondGracePeriod()) {
            throw new ForbiddenException("Dashboards are hidden for this farm. Raw data remains retained but is no longer visible to farm users.");
        }
    }

    public boolean isDashboardOnlyWindow(Farm farm) {
        return farm.isExpired() && farm.inGracePeriod();
    }

    public boolean dashboardsHidden(Farm farm) {
        return farm.dashboardsHidden() || Boolean.TRUE.equals(farm.getIsArchived()) || farm.fullyArchived();
    }

    public void validateAreaWithinLicense(Farm farm, BigDecimal requestedArea) {
        BigDecimal effectiveLicensedArea = resolveEffectiveLicensedArea(farm);
        if (requestedArea == null || requestedArea.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (effectiveLicensedArea.compareTo(BigDecimal.ZERO) <= 0 || requestedArea.compareTo(effectiveLicensedArea) > 0) {
            throw new BadRequestException("Requested scouting area exceeds licensed hectares.");
        }
    }

    public BigDecimal calculateSelectedAreaHectares(
            BigDecimal structureAreaHectares,
            int totalBayCount,
            boolean includeAllBays,
            int selectedBayCount,
            String structureName
    ) {
        BigDecimal normalizedArea = normalizeArea(structureAreaHectares);
        if (normalizedArea.compareTo(BigDecimal.ZERO) <= 0) {
            // Structure-level area is optional. Farm-level licensed hectares remain the source of truth.
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (includeAllBays || totalBayCount <= 0) {
            return normalizedArea;
        }

        int boundedSelectedBays = Math.max(0, Math.min(selectedBayCount, totalBayCount));
        if (boundedSelectedBays == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return normalizedArea.multiply(BigDecimal.valueOf(boundedSelectedBays))
                .divide(BigDecimal.valueOf(totalBayCount), 2, RoundingMode.HALF_UP);
    }

    public int normalizeExtensionMonths(LicenseType licenseType, Integer rawExtensionMonths) {
        int extensionMonths = rawExtensionMonths == null ? 0 : rawExtensionMonths;
        if (extensionMonths < 0) {
            throw new BadRequestException("License extension months cannot be negative.");
        }

        int maxExtension = maxExtensionMonthsFor(Optional.ofNullable(licenseType).orElse(LicenseType.PAID));
        if (extensionMonths > maxExtension) {
            throw new BadRequestException("License extension exceeds the allowed maximum of " + maxExtension + " month(s).");
        }

        return extensionMonths;
    }

    public LocalDate resolveStartDate(Farm farm) {
        return resolveStartDate(
                farm,
                Optional.ofNullable(farm.getLicenseType()).orElse(LicenseType.PAID),
                Optional.ofNullable(farm.getLicenseExtensionMonths()).orElse(0)
        );
    }

    private LocalDate resolveStartDate(Farm farm, LicenseType licenseType, int extensionMonths) {
        if (farm.getLicenseStartDate() != null) {
            return farm.getLicenseStartDate();
        }

        if (farm.getLicenseExpiryDate() != null) {
            return farm.getLicenseExpiryDate().plusDays(1).minusMonths(baseMonthsFor(licenseType) + extensionMonths);
        }

        if (farm.getSubscriptionStatus() == SubscriptionStatus.ACTIVE) {
            return LocalDate.now();
        }

        return null;
    }

    private int baseMonthsFor(LicenseType licenseType) {
        return switch (licenseType) {
            case TRIAL -> licensePolicyProperties.getTrialMonths();
            case PAID -> licensePolicyProperties.getPaidMonths();
        };
    }

    private int maxExtensionMonthsFor(LicenseType licenseType) {
        return switch (licenseType) {
            case TRIAL -> licensePolicyProperties.getMaxTrialExtensionMonths();
            case PAID -> licensePolicyProperties.getMaxPaidExtensionMonths();
        };
    }

    private BigDecimal normalizePercentage(BigDecimal percentage) {
        if (percentage == null) {
            return BigDecimal.ZERO;
        }
        if (percentage.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (percentage.compareTo(ONE_HUNDRED) > 0) {
            return ONE_HUNDRED;
        }
        return percentage;
    }

    private BigDecimal normalizeArea(BigDecimal area) {
        if (area == null || area.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return area.setScale(2, RoundingMode.HALF_UP);
    }
}

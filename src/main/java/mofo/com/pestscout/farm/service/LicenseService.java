package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.farm.model.Farm;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class LicenseService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

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

    public void validateFarmLicenseActive(Farm farm) {
        if (Boolean.TRUE.equals(farm.getIsArchived()) || farm.fullyArchived()) {
            throw new BadRequestException("Farm is archived and cannot create new sessions.");
        }
        if (farm.beyondGracePeriod()) {
            throw new BadRequestException("Farm license has expired and is beyond grace period.");
        }
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
}


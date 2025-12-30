package mofo.com.pestscout.unit.farm.service;

import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.service.LicenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicenseServiceTest {

    private LicenseService licenseService;
    private Farm farm;

    @BeforeEach
    void setUp() {
        licenseService = new LicenseService();
        farm = Farm.builder()
                .licensedAreaHectares(new BigDecimal("10"))
                .quotaDiscountPercentage(new BigDecimal("10"))
                .build();
    }

    @Test
    @DisplayName("Applies quota discount when provided")
    void applyDiscount_ReturnsDiscountedAmount() {
        BigDecimal discounted = licenseService.applyDiscount(new BigDecimal("100"), new BigDecimal("20"));

        assertThat(discounted).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    @DisplayName("Calculates effective area using discount")
    void resolveEffectiveLicensedArea_AppliesDiscount() {
        BigDecimal effective = licenseService.resolveEffectiveLicensedArea(farm);

        assertThat(effective).isEqualByComparingTo(new BigDecimal("9.00"));
    }

    @Test
    @DisplayName("Throws when farm is archived")
    void validateFarmLicenseActive_ThrowsWhenArchived() {
        farm.setIsArchived(true);

        assertThatThrownBy(() -> licenseService.validateFarmLicenseActive(farm))
                .hasMessageContaining("archived");
    }
}


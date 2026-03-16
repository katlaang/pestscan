package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.farm.config.LicensePolicyProperties;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.LicenseType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicenseServiceTest {

    private final LicensePolicyProperties properties = new LicensePolicyProperties();
    private final LicenseService licenseService = new LicenseService(properties);

    @Test
    void applyCommercialSchedule_setsTrialExpiryAndDashboardWindow() {
        Farm farm = Farm.builder()
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .licenseType(LicenseType.TRIAL)
                .licenseStartDate(LocalDate.of(2026, 1, 15))
                .licenseExtensionMonths(3)
                .build();

        licenseService.applyCommercialSchedule(farm);

        assertThat(farm.getLicenseExpiryDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(farm.getLicenseGracePeriodEnd()).isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(farm.getLicenseArchivedDate()).isEqualTo(LocalDate.of(2026, 8, 13));
    }

    @Test
    void applyCommercialSchedule_rejectsPaidExtensionBeyondConfiguredMaximum() {
        Farm farm = Farm.builder()
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .licenseType(LicenseType.PAID)
                .licenseStartDate(LocalDate.of(2026, 1, 1))
                .licenseExtensionMonths(7)
                .build();

        assertThatThrownBy(() -> licenseService.applyCommercialSchedule(farm))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("allowed maximum");
    }

    @Test
    void assertOperationalAccess_blocksExpiredFarmEvenDuringDashboardWindow() {
        Farm farm = Farm.builder()
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .licenseType(LicenseType.PAID)
                .licenseStartDate(LocalDate.now().minusMonths(12).minusDays(5))
                .licenseExtensionMonths(0)
                .build();
        licenseService.applyCommercialSchedule(farm);

        assertThat(farm.inGracePeriod()).isTrue();
        assertThatThrownBy(() -> licenseService.assertOperationalAccess(farm))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void assertDashboardAccess_allowsManagerWindowUntilGraceEnds() {
        Farm farm = Farm.builder()
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .licenseType(LicenseType.PAID)
                .licenseStartDate(LocalDate.now().minusMonths(12).minusDays(5))
                .licenseExtensionMonths(0)
                .build();
        licenseService.applyCommercialSchedule(farm);

        licenseService.assertDashboardAccess(farm);
        assertThat(licenseService.isDashboardOnlyWindow(farm)).isTrue();
    }

    @Test
    void assertDashboardAccess_hidesDashboardsAfterVisibilityWindow() {
        Farm farm = Farm.builder()
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .licenseType(LicenseType.PAID)
                .licenseStartDate(LocalDate.now().minusMonths(13).minusDays(40))
                .licenseExtensionMonths(0)
                .build();
        licenseService.applyCommercialSchedule(farm);

        assertThatThrownBy(() -> licenseService.assertDashboardAccess(farm))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("hidden");
    }
}

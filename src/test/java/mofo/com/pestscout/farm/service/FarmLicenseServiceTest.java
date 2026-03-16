package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.dto.FarmLicenseResponse;
import mofo.com.pestscout.farm.dto.UpdateFarmLicenseRequest;
import mofo.com.pestscout.farm.model.*;
import mofo.com.pestscout.farm.repository.FarmLicenseHistoryRepository;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.farm.security.FarmAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmLicenseServiceTest {

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private FarmLicenseHistoryRepository farmLicenseHistoryRepository;

    @Mock
    private FarmAccessService farmAccessService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private LicenseService licenseService;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private FarmLicenseService farmLicenseService;

    @Test
    void generateLicense_createsReferenceAndHistorySnapshot() {
        UUID farmId = UUID.randomUUID();
        Farm farm = Farm.builder()
                .id(farmId)
                .name("North Farm")
                .farmTag("CA-NORTH")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.PREMIUM)
                .licensedAreaHectares(new BigDecimal("12.00"))
                .billingEmail("billing@example.com")
                .updatedAt(LocalDateTime.of(2026, 3, 16, 12, 0))
                .build();

        User actor = User.builder()
                .email("superadmin@example.com")
                .password("secret")
                .phoneNumber("123")
                .customerNumber("CA0001")
                .role(Role.SUPER_ADMIN)
                .build();
        actor.setId(UUID.randomUUID());

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(farmRepository.existsByLicenseReference(anyString())).thenReturn(false);
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(licenseService.resolveEffectiveLicensedArea(farm)).thenReturn(new BigDecimal("12.00"));
        when(farmLicenseHistoryRepository.findFirstByFarmIdOrderByCreatedAtAsc(farmId)).thenReturn(Optional.of(
                FarmLicenseHistory.builder()
                        .createdAt(LocalDateTime.of(2026, 3, 16, 12, 0))
                        .build()
        ));

        FarmLicenseResponse response = farmLicenseService.generateLicense(farmId);

        assertThat(response.licenseReference()).startsWith("LIC-");
        assertThat(response.licensedAreaHectares()).isEqualByComparingTo("12.00");

        ArgumentCaptor<FarmLicenseHistory> historyCaptor = ArgumentCaptor.forClass(FarmLicenseHistory.class);
        verify(farmLicenseHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getAction()).isEqualTo(FarmLicenseAction.GENERATED);
        assertThat(historyCaptor.getValue().getLicenseReference()).isEqualTo(response.licenseReference());
    }

    @Test
    void updateLicense_updatesFarmAndRecordsAuditHistory() {
        UUID farmId = UUID.randomUUID();
        Farm farm = Farm.builder()
                .id(farmId)
                .name("North Farm")
                .farmTag("CA-NORTH")
                .licenseReference("LIC-CA-NORTH-20260316-ABCDEF12")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .subscriptionTier(SubscriptionTier.BASIC)
                .licensedAreaHectares(new BigDecimal("5.00"))
                .updatedAt(LocalDateTime.of(2026, 3, 16, 13, 0))
                .build();

        User actor = User.builder()
                .email("superadmin@example.com")
                .password("secret")
                .phoneNumber("123")
                .customerNumber("CA0001")
                .role(Role.SUPER_ADMIN)
                .firstName("Ada")
                .lastName("Admin")
                .build();
        actor.setId(UUID.randomUUID());

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserService.getCurrentUser()).thenReturn(actor);
        when(licenseService.resolveEffectiveLicensedArea(farm)).thenReturn(new BigDecimal("9.00"));
        when(farmLicenseHistoryRepository.findFirstByFarmIdOrderByCreatedAtAsc(farmId)).thenReturn(Optional.of(
                FarmLicenseHistory.builder()
                        .createdAt(LocalDateTime.of(2026, 3, 1, 9, 0))
                        .build()
        ));

        FarmLicenseResponse response = farmLicenseService.updateLicense(
                farmId,
                new UpdateFarmLicenseRequest(
                        SubscriptionStatus.ACTIVE,
                        SubscriptionTier.PREMIUM,
                        "billing@example.com",
                        new BigDecimal("10.00"),
                        new BigDecimal("10.00"),
                        LocalDate.of(2026, 12, 31),
                        LocalDate.of(2027, 1, 31),
                        null,
                        true,
                        false,
                        "Annual renewal"
                )
        );

        assertThat(response.subscriptionTier()).isEqualTo(SubscriptionTier.PREMIUM);
        assertThat(response.effectiveLicensedAreaHectares()).isEqualByComparingTo("9.00");
        assertThat(farm.getLicensedAreaHectares()).isEqualByComparingTo("10.00");
        assertThat(farm.getLicenseGracePeriodEnd()).isEqualTo(LocalDate.of(2027, 1, 31));

        ArgumentCaptor<FarmLicenseHistory> historyCaptor = ArgumentCaptor.forClass(FarmLicenseHistory.class);
        verify(farmLicenseHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getAction()).isEqualTo(FarmLicenseAction.UPDATED);
        assertThat(historyCaptor.getValue().getNotes()).isEqualTo("Annual renewal");
    }
}

package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.analytics.service.RawDataPdfExportService;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.repository.UserFarmMembershipRepository;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FarmLicenseAction;
import mofo.com.pestscout.farm.model.FarmLicenseHistory;
import mofo.com.pestscout.farm.repository.FarmLicenseHistoryRepository;
import mofo.com.pestscout.farm.repository.FarmRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LicenseExpiryNotificationService {

    private static final String SYSTEM_ACTOR_EMAIL = "system@pestscout.local";

    private final FarmRepository farmRepository;
    private final FarmLicenseHistoryRepository farmLicenseHistoryRepository;
    private final UserFarmMembershipRepository membershipRepository;
    private final LicenseService licenseService;
    private final RawDataPdfExportService rawDataPdfExportService;

    @Transactional(readOnly = true)
    public boolean shouldQueueExpiryNotice(Farm farm) {
        return farm.getLicenseExpiryNotificationSentAt() == null
                && farm.isExpired()
                && !licenseService.dashboardsHidden(farm)
                && !resolveRecipients(farm).isEmpty();
    }

    @Transactional
    public void queueExpiryNotice(Farm farm) {
        Set<String> recipients = resolveRecipients(farm);
        if (recipients.isEmpty()) {
            return;
        }

        String downloadUrl = rawDataPdfExportService.buildDownloadUrl(farm.getId());
        recipients.forEach(recipient ->
                log.info(
                        "Queued license expiry email for farm {} to {}. Subject='PestScout data export reminder'. Download URL={}",
                        farm.getId(),
                        recipient,
                        downloadUrl
                )
        );

        farm.setLicenseExpiryNotificationSentAt(java.time.LocalDateTime.now());
        farmRepository.save(farm);
        farmLicenseHistoryRepository.save(FarmLicenseHistory.builder()
                .farm(farm)
                .licenseReference(farm.getLicenseReference())
                .action(FarmLicenseAction.EXPIRY_NOTICE_QUEUED)
                .licenseType(farm.getLicenseType())
                .licenseStartDate(farm.getLicenseStartDate())
                .licenseExtensionMonths(farm.getLicenseExtensionMonths())
                .subscriptionStatus(farm.getSubscriptionStatus())
                .subscriptionTier(farm.getSubscriptionTier())
                .billingEmail(farm.getBillingEmail())
                .licensedAreaHectares(farm.getLicensedAreaHectares())
                .quotaDiscountPercentage(farm.getQuotaDiscountPercentage())
                .effectiveLicensedAreaHectares(licenseService.resolveEffectiveLicensedArea(farm))
                .licenseExpiryDate(farm.getLicenseExpiryDate())
                .licenseGracePeriodEnd(farm.getLicenseGracePeriodEnd())
                .licenseArchivedDate(farm.getLicenseArchivedDate())
                .autoRenewEnabled(farm.getAutoRenewEnabled())
                .archived(farm.getIsArchived())
                .actorEmail(SYSTEM_ACTOR_EMAIL)
                .notes("Expiry reminder queued for " + recipients.size() + " recipient(s). Raw data export: " + downloadUrl)
                .build());
    }

    private Set<String> resolveRecipients(Farm farm) {
        Set<String> recipients = new LinkedHashSet<>();

        if (farm.getOwner() != null && farm.getOwner().getEmail() != null && !farm.getOwner().getEmail().isBlank()) {
            recipients.add(farm.getOwner().getEmail().trim());
        }

        membershipRepository.findByFarmId(farm.getId()).stream()
                .filter(membership -> Boolean.TRUE.equals(membership.getIsActive()))
                .filter(membership -> membership.getRole() == Role.FARM_ADMIN || membership.getRole() == Role.MANAGER)
                .map(membership -> membership.getUser().getEmail())
                .filter(email -> email != null && !email.isBlank())
                .map(String::trim)
                .forEach(recipients::add);

        return recipients;
    }
}

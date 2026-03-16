package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.dto.FarmLicenseHistoryResponse;
import mofo.com.pestscout.farm.dto.FarmLicenseResponse;
import mofo.com.pestscout.farm.dto.UpdateFarmLicenseRequest;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FarmLicenseAction;
import mofo.com.pestscout.farm.model.FarmLicenseHistory;
import mofo.com.pestscout.farm.repository.FarmLicenseHistoryRepository;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.farm.security.FarmAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FarmLicenseService {

    private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final FarmRepository farmRepository;
    private final FarmLicenseHistoryRepository farmLicenseHistoryRepository;
    private final FarmAccessService farmAccessService;
    private final CurrentUserService currentUserService;
    private final LicenseService licenseService;
    private final CacheService cacheService;

    @Transactional(readOnly = true)
    public FarmLicenseResponse getCurrentLicense(UUID farmId) {
        farmAccessService.requireSuperAdmin();
        Farm farm = loadFarm(farmId);
        return toResponse(farm);
    }

    @Transactional
    public FarmLicenseResponse generateLicense(UUID farmId) {
        farmAccessService.requireSuperAdmin();
        Farm farm = loadFarm(farmId);
        licenseService.applyCommercialSchedule(farm);

        if (farm.getLicenseReference() == null || farm.getLicenseReference().isBlank()) {
            farm.setLicenseReference(generateLicenseReference(farm));
        }
        farm = farmRepository.save(farm);

        recordHistory(farm, FarmLicenseAction.GENERATED, "License snapshot generated from current farm settings.");
        cacheService.evictFarmCachesAfterCommit(farmId);
        return toResponse(farm);
    }

    @Transactional
    public FarmLicenseResponse updateLicense(UUID farmId, UpdateFarmLicenseRequest request) {
        farmAccessService.requireSuperAdmin();
        Farm farm = loadFarm(farmId);
        boolean commercialPolicyChanged = false;

        if (farm.getLicenseReference() == null || farm.getLicenseReference().isBlank()) {
            farm.setLicenseReference(generateLicenseReference(farm));
        }

        if (request.subscriptionStatus() != null) {
            farm.setSubscriptionStatus(request.subscriptionStatus());
        }
        if (request.subscriptionTier() != null) {
            farm.setSubscriptionTier(request.subscriptionTier());
        }
        if (request.licenseType() != null) {
            farm.setLicenseType(request.licenseType());
            commercialPolicyChanged = true;
        }
        if (request.licenseStartDate() != null) {
            farm.setLicenseStartDate(request.licenseStartDate());
            commercialPolicyChanged = true;
        }
        if (request.licenseExtensionMonths() != null) {
            farm.setLicenseExtensionMonths(request.licenseExtensionMonths());
            commercialPolicyChanged = true;
        }
        if (request.billingEmail() != null) {
            farm.setBillingEmail(request.billingEmail());
        }
        if (request.licensedAreaHectares() != null) {
            farm.setLicensedAreaHectares(request.licensedAreaHectares());
        }
        if (request.quotaDiscountPercentage() != null) {
            farm.setQuotaDiscountPercentage(request.quotaDiscountPercentage());
        }
        if (request.licenseExpiryDate() != null) {
            farm.setLicenseExpiryDate(request.licenseExpiryDate());
            commercialPolicyChanged = true;
        }
        if (commercialPolicyChanged || farm.getLicenseStartDate() != null || farm.getLicenseExpiryDate() != null) {
            licenseService.applyCommercialSchedule(farm);
        }
        if (request.licenseGracePeriodEnd() != null) {
            farm.setLicenseGracePeriodEnd(request.licenseGracePeriodEnd());
        }
        if (request.licenseArchivedDate() != null) {
            farm.setLicenseArchivedDate(request.licenseArchivedDate());
        }
        if (request.autoRenewEnabled() != null) {
            farm.setAutoRenewEnabled(request.autoRenewEnabled());
        }
        if (request.isArchived() != null) {
            farm.setIsArchived(request.isArchived());
        }
        if (commercialPolicyChanged || request.subscriptionStatus() != null) {
            farm.setLicenseExpiryNotificationSentAt(null);
        }

        validateChronology(farm);

        Farm saved = farmRepository.save(farm);
        recordHistory(saved, FarmLicenseAction.UPDATED, request.notes());
        cacheService.evictFarmCachesAfterCommit(farmId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FarmLicenseHistoryResponse> getLicenseHistory(UUID farmId) {
        farmAccessService.requireSuperAdmin();
        loadFarm(farmId);

        return farmLicenseHistoryRepository.findByFarmIdOrderByCreatedAtDesc(farmId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private Farm loadFarm(UUID farmId) {
        return farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));
    }

    private void validateChronology(Farm farm) {
        LocalDate expiryDate = farm.getLicenseExpiryDate();
        LocalDate graceEnd = farm.getLicenseGracePeriodEnd();
        LocalDate archivedDate = farm.getLicenseArchivedDate();

        if (expiryDate != null && graceEnd != null && graceEnd.isBefore(expiryDate)) {
            throw new BadRequestException("License grace period end cannot be before the license expiry date.");
        }
        if (graceEnd != null && archivedDate != null && archivedDate.isBefore(graceEnd)) {
            throw new BadRequestException("License archived date cannot be before the grace period end date.");
        }
    }

    private void recordHistory(Farm farm, FarmLicenseAction action, String notes) {
        User currentUser = currentUserService.getCurrentUser();

        FarmLicenseHistory history = FarmLicenseHistory.builder()
                .farm(farm)
                .licenseReference(farm.getLicenseReference())
                .action(action)
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
                .actorUserId(currentUser.getId())
                .actorEmail(currentUser.getEmail())
                .notes(notes)
                .build();

        farmLicenseHistoryRepository.save(history);
    }

    private FarmLicenseResponse toResponse(Farm farm) {
        Optional<FarmLicenseHistory> generatedSnapshot = farmLicenseHistoryRepository.findFirstByFarmIdOrderByCreatedAtAsc(farm.getId());
        return new FarmLicenseResponse(
                farm.getId(),
                farm.getName(),
                farm.getLicenseReference(),
                farm.getLicenseType(),
                farm.getLicenseStartDate(),
                farm.getLicenseExtensionMonths(),
                farm.getSubscriptionStatus(),
                farm.getSubscriptionTier(),
                farm.getBillingEmail(),
                farm.getLicensedAreaHectares(),
                farm.getQuotaDiscountPercentage(),
                licenseService.resolveEffectiveLicensedArea(farm),
                farm.getLicenseExpiryDate(),
                farm.getLicenseGracePeriodEnd(),
                farm.getLicenseArchivedDate(),
                farm.getAutoRenewEnabled(),
                farm.getIsArchived(),
                toInstant(farm.getLicenseExpiryNotificationSentAt()),
                generatedSnapshot.map(FarmLicenseHistory::getCreatedAt).map(this::toInstant).orElse(null),
                toInstant(farm.getUpdatedAt())
        );
    }

    private FarmLicenseHistoryResponse toHistoryResponse(FarmLicenseHistory history) {
        return new FarmLicenseHistoryResponse(
                history.getId(),
                history.getLicenseReference(),
                history.getAction(),
                history.getLicenseType(),
                history.getLicenseStartDate(),
                history.getLicenseExtensionMonths(),
                history.getSubscriptionStatus(),
                history.getSubscriptionTier(),
                history.getBillingEmail(),
                history.getLicensedAreaHectares(),
                history.getQuotaDiscountPercentage(),
                history.getEffectiveLicensedAreaHectares(),
                history.getLicenseExpiryDate(),
                history.getLicenseGracePeriodEnd(),
                history.getLicenseArchivedDate(),
                history.getAutoRenewEnabled(),
                history.getArchived(),
                history.getNotes(),
                history.getActorUserId(),
                history.getActorEmail(),
                toInstant(history.getCreatedAt())
        );
    }

    private String generateLicenseReference(Farm farm) {
        String base = farm.getFarmTag() == null || farm.getFarmTag().isBlank()
                ? "FARM"
                : farm.getFarmTag().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "");

        String candidate;
        do {
            String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            candidate = "LIC-" + base + "-" + LocalDate.now().format(REFERENCE_DATE) + "-" + suffix;
        } while (farmRepository.existsByLicenseReference(candidate));

        return candidate;
    }

    private Instant toInstant(java.time.LocalDateTime value) {
        return value != null ? value.toInstant(ZoneOffset.UTC) : null;
    }
}

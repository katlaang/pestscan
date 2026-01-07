package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.auth.service.CustomerNumberService;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ForbiddenException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.dto.CreateFarmRequest;
import mofo.com.pestscout.farm.dto.FarmResponse;
import mofo.com.pestscout.farm.dto.UpdateFarmRequest;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FarmStructureType;
import mofo.com.pestscout.farm.model.FieldBlock;
import mofo.com.pestscout.farm.model.Greenhouse;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.farm.security.FarmAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FarmService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FarmService.class);
    private final FarmRepository farmRepository;
    private final FarmAccessService farmAccess;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final CustomerNumberService customerNumberService;
    private final CacheService cacheService;

    /**
     * SUPER_ADMIN ONLY.
     * Creates a new farm and assigns initial licensing settings.
     */
    @Transactional
    public FarmResponse createFarm(CreateFarmRequest request) {
        farmAccess.requireSuperAdmin();
        LOGGER.info("Creating farm '{}'", request.name());

        farmRepository.findByNameIgnoreCase(request.name()).ifPresent(existing -> {
            throw new ConflictException("Farm already exists: " + request.name());
        });

        String countryCode = customerNumberService.normalizeCountryCode(request.country());

        Farm farm = toFarmEntity(request, countryCode);
        Farm saved = farmRepository.save(farm);

        LOGGER.info("Farm '{}' created with id {}", saved.getName(), saved.getId());
        return mapToResponse(saved);
    }

    /**
     * Updates farm information.
     * SUPER_ADMIN can update all fields.
     * FARM_ADMIN/MANAGER can only update non-license fields.
     *
     * Cache is evicted after update to ensure fresh data.
     */
    @Transactional
    @CacheEvict(value = "farms", keyGenerator = "tenantAwareKeyGenerator")
    public FarmResponse updateFarm(UUID farmId, UpdateFarmRequest request) {

        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        // Access: super admin OR farm owner/manager
        farmAccess.requireAdminOrSuperAdmin(farm);

        LOGGER.info("Updating farm {} ({})", farm.getName(), farm.getId());

        // Enforce name uniqueness
        if (!farm.getName().equalsIgnoreCase(request.name())) {
            farmRepository.findByNameIgnoreCase(request.name())
                    .filter(existing -> !existing.getId().equals(farmId))
                    .ifPresent(existing -> {
                        throw new ConflictException("Farm already exists with name " + request.name());
                    });
        }

        // Apply field updates based on role
        applyAllowedFarmUpdates(farm, request);

        Farm saved = farmRepository.save(farm);

        // Clear all related caches (greenhouses, sessions, analytics, etc.)
        cacheService.evictFarmCachesAfterCommit(farmId);

        LOGGER.info("Farm '{}' updated successfully", saved.getName());
        return mapToResponse(saved);
    }

    /**
     * Returns all farms visible to this user.
     * SUPER_ADMIN sees all.
     * FARM_ADMIN/MANAGER sees only farms they own.
     * SCOUT sees only the farm they are assigned to.
     *
     * Cached per user role to improve performance.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = "farms-list",
            keyGenerator = "tenantAwareKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<FarmResponse> listFarms() {
        LOGGER.info("Listing farms for current user");
        List<Farm> farms;

        switch (farmAccess.getCurrentUserRole()) {
            case SUPER_ADMIN -> farms = farmRepository.findAll();
            case FARM_ADMIN, MANAGER -> {
                farms = farmRepository.findByOwnerId(currentUserService.getCurrentUserId());
            }
            case SCOUT -> throw new ForbiddenException("Scouts cannot access farm dashboards.");
            default -> farms = List.of();
        }

        return farms.stream()
                .sorted(Comparator.comparing(Farm::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Loads a single farm.
     * Cached for 2 hours since farm metadata rarely changes.
     */
    @Transactional(readOnly = true)
    @Cacheable(
            value = "farms",
            keyGenerator = "tenantAwareKeyGenerator",
            unless = "#result == null"
    )
    public FarmResponse getFarm(UUID farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        farmAccess.requireViewAccess(farm);
        return mapToResponse(farm);
    }

    // -------------------------------
    // INTERNAL HELPERS
    // -------------------------------

    private Farm toFarmEntity(CreateFarmRequest request, String countryCode) {
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.ownerId()));
        User scout = null;
        if (request.scoutId() != null) {
            scout = userRepository.findById(request.scoutId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.scoutId()));
        }

        Farm farm = Farm.builder()
                .name(request.name())
                .description(request.description())
                .externalId(generateExternalId())
                .farmTag(generateFarmTag(countryCode, request.name()))
                .address(request.address())
                .city(request.city())
                .province(request.province())
                .postalCode(request.postalCode())
                .country(request.country())
                .owner(owner)
                .scout(scout)
                .contactName(resolveContactName(owner))
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .subscriptionStatus(request.subscriptionStatus())
                .subscriptionTier(request.subscriptionTier())
                .billingEmail(request.billingEmail())
                .licensedAreaHectares(request.licensedAreaHectares())
                .licensedUnitQuota(request.licensedUnitQuota())
                .quotaDiscountPercentage(request.quotaDiscountPercentage())
                .structureType(resolveStructureType(request.structureType()))
                .defaultBayCount(request.defaultBayCount())
                .defaultBenchesPerBay(request.defaultBenchesPerBay())
                .defaultSpotChecksPerBench(request.defaultSpotChecksPerBench())
                .timezone(request.timezone())
                .licenseExpiryDate(request.licenseExpiryDate())
                .autoRenewEnabled(request.autoRenewEnabled())
                .build();

        if (request.greenhouses() != null) {
            request.greenhouses().forEach(ghRequest -> {
                Greenhouse greenhouse = Greenhouse.builder()
                        .farm(farm)
                        .name(ghRequest.name())
                        .description(ghRequest.description())
                        .bayCount(ghRequest.bayCount())
                        .benchesPerBay(ghRequest.benchesPerBay())
                        .spotChecksPerBench(ghRequest.spotChecksPerBench())
                        .bayTags(normalizeTags(ghRequest.bayTags()))
                        .benchTags(normalizeTags(ghRequest.benchTags()))
                        .build();
                farm.getGreenhouses().add(greenhouse);
            });
        }

        if (request.fieldBlocks() != null) {
            request.fieldBlocks().forEach(blockRequest -> {
                FieldBlock block = FieldBlock.builder()
                        .farm(farm)
                        .name(blockRequest.name())
                        .bayCount(blockRequest.bayCount())
                        .spotChecksPerBay(blockRequest.spotChecksPerBay())
                        .bayTags(normalizeTags(blockRequest.bayTags()))
                        .active(Boolean.TRUE.equals(blockRequest.active()))
                        .build();
                farm.getFieldBlocks().add(block);
            });
        }

        return farm;
    }

    /**
     * Applies allowed updates depending on user role.
     */
    private void applyAllowedFarmUpdates(Farm farm, UpdateFarmRequest request) {

        // Common fields editable by FARM_MANAGER or SUPER_ADMIN
        farm.setName(request.name());
        farm.setDescription(request.description());
        farm.setAddress(request.address());
        farm.setCity(request.city());
        farm.setProvince(request.province());
        farm.setPostalCode(request.postalCode());
        farm.setCountry(request.country());
        farm.setContactName(resolveContactName(farm.getOwner()));
        farm.setContactEmail(request.contactEmail());
        farm.setContactPhone(request.contactPhone());
        farm.setTimezone(request.timezone());

        // Defaults (manager allowed)
        if (request.defaultBayCount() != null)
            farm.setDefaultBayCount(request.defaultBayCount());
        if (request.defaultBenchesPerBay() != null)
            farm.setDefaultBenchesPerBay(request.defaultBenchesPerBay());
        if (request.defaultSpotChecksPerBench() != null)
            farm.setDefaultSpotChecksPerBench(request.defaultSpotChecksPerBench());

        if (farmAccess.isSuperAdmin()) {
            if (request.subscriptionStatus() != null) {
                farm.setSubscriptionStatus(request.subscriptionStatus());
            }
            if (request.subscriptionTier() != null) {
                farm.setSubscriptionTier(request.subscriptionTier());
            }
            farm.setBillingEmail(request.billingEmail());

            if (request.licensedAreaHectares() != null) {
                farm.setLicensedAreaHectares(request.licensedAreaHectares());
            }
            farm.setLicensedUnitQuota(request.licensedUnitQuota());
            farm.setQuotaDiscountPercentage(request.quotaDiscountPercentage());
            farm.setLicenseExpiryDate(request.licenseExpiryDate());
            farm.setLicenseGracePeriodEnd(request.licenseGracePeriodEnd());
            farm.setLicenseArchivedDate(request.licenseArchivedDate());
            farm.setAutoRenewEnabled(request.autoRenewEnabled());
            farm.setIsArchived(request.isArchived());
            farm.setLatitude(request.latitude());
            farm.setLongitude(request.longitude());
        }
    }

    private FarmResponse mapToResponse(Farm farm) {
        // Map in the context of the current user's role so we can hide licensing data from scouts.
        Role role = farmAccess.getCurrentUserRole();
        boolean hideLicense = role == Role.SCOUT;

        return new FarmResponse(
                farm.getId(),
                farm.getFarmTag(),

                farm.getName(),
                farm.getDescription(),
                farm.getExternalId(),
                farm.getAddress(),
                farm.getCity(),
                farm.getProvince(),
                farm.getPostalCode(),
                farm.getCountry(),

                farm.getContactName(),
                farm.getContactEmail(),
                farm.getContactPhone(),

                farm.getSubscriptionStatus(),
                farm.getSubscriptionTier(),
                hideLicense ? null : farm.getBillingEmail(),

                farm.getLicensedAreaHectares(),
                hideLicense ? null : farm.getLicensedUnitQuota(),
                hideLicense ? null : farm.getQuotaDiscountPercentage(),
                hideLicense ? null : farm.getLicenseExpiryDate(),
                hideLicense ? null : farm.getAutoRenewEnabled(),
                // accessLocked is a view-only flag that mirrors license state for non-scouters; scouts do not see it.
                hideLicense ? null : farm.isExpired() || farm.inGracePeriod() || Boolean.TRUE.equals(farm.getIsArchived()),
                farm.getStructureType(),

                farm.getDefaultBayCount(),
                farm.getDefaultBenchesPerBay(),
                farm.getDefaultSpotChecksPerBench(),
                toInstant(farm.getCreatedAt()),
                toInstant(farm.getUpdatedAt()),
                farm.getTimezone(),
                farm.getOwner() != null ? farm.getOwner().getId() : null,
                farm.getScout() != null ? farm.getScout().getId() : null
        );
    }

    private String generateExternalId() {
        String externalId;

        do {
            externalId = UUID.randomUUID().toString();
        } while (farmRepository.existsByExternalId(externalId));

        return externalId;
    }

    private String generateFarmTag(String countryCode, String farmName) {
        String prefix = (countryCode == null || countryCode.isBlank())
                ? "ZZ"
                : countryCode.trim().toUpperCase(Locale.ROOT);
        String base = normalizeFarmTagPart(farmName);
        String candidate = buildFarmTag(prefix, base, "");
        int suffix = 1;

        while (farmRepository.existsByFarmTag(candidate)) {
            suffix++;
            candidate = buildFarmTag(prefix, base, "-" + suffix);
        }

        return candidate;
    }

    private String normalizeFarmTagPart(String farmName) {
        if (farmName == null) {
            return "FARM";
        }
        String normalized = farmName.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "FARM" : normalized;
    }

    private String buildFarmTag(String prefix, String base, String suffix) {
        int maxBaseLength = 32 - prefix.length() - 1 - suffix.length();
        String trimmedBase = base;
        if (maxBaseLength < 1) {
            trimmedBase = "FARM";
        } else if (base.length() > maxBaseLength) {
            trimmedBase = base.substring(0, maxBaseLength);
        }
        return prefix + "-" + trimmedBase + suffix;
    }


    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }


    private FarmStructureType resolveStructureType(FarmStructureType requested) {
        return requested != null ? requested : FarmStructureType.GREENHOUSE;
    }

    private String resolveContactName(User owner) {
        if (owner == null) {
            return null;
        }
        String firstName = owner.getFirstName();
        String lastName = owner.getLastName();
        StringBuilder name = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            name.append(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(lastName.trim());
        }
        String result = name.toString();
        return result.isBlank() ? null : result;
    }

    private Instant toInstant(java.time.LocalDateTime value) {
        return value != null ? value.toInstant(ZoneOffset.UTC) : null;
    }
}

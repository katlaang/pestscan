package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.farm.dto.CreateFarmRequest;
import mofo.com.pestscout.farm.dto.FarmResponse;
import mofo.com.pestscout.farm.dto.UpdateFarmRequest;
import mofo.com.pestscout.farm.model.*;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import mofo.com.pestscout.farm.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FarmService {

    private final FarmRepository farmRepository;
    private final FarmAccessService farmAccess;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    /**
     * SUPER_ADMIN ONLY.
     * Creates a new farm and assigns initial licensing settings.
     */
    @Transactional
    public FarmResponse createFarm(CreateFarmRequest request) {
        farmAccess.requireSuperAdmin();
        log.info("Creating farm '{}'", request.name());

        farmRepository.findByNameIgnoreCase(request.name()).ifPresent(existing -> {
            throw new ConflictException("Farm already exists: " + request.name());
        });

        Farm farm = toFarmEntity(request);
        Farm saved = farmRepository.save(farm);

        log.info("Farm '{}' created with id {}", saved.getName(), saved.getId());
        return mapToResponse(saved);
    }

    /**
     * Updates farm information.
     * SUPER_ADMIN can update all fields.
     * FARM_ADMIN/MANAGER can only update non-license fields.
     */
    @Transactional
    public FarmResponse updateFarm(UUID farmId, UpdateFarmRequest request) {

        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        // Access: super admin OR farm owner/manager
        farmAccess.requireAdminOrSuperAdmin(farm);

        log.info("Updating farm {} ({})", farm.getName(), farm.getId());

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

        log.info("Farm '{}' updated successfully", saved.getName());
        return mapToResponse(saved);
    }

    /**
     * Returns all farms visible to this user.
     * SUPER_ADMIN sees all.
     * FARM_ADMIN/MANAGER sees only farms they own.
     * SCOUT sees only the farm they are assigned to.
     */
    @Transactional(readOnly = true)
    public List<FarmResponse> listFarms() {
        log.info("Listing farms for current user");
        List<Farm> farms;

        switch (farmAccess.getCurrentUserRole()) {
            case SUPER_ADMIN -> farms = farmRepository.findAll();
            case FARM_ADMIN, MANAGER -> {
                farms = farmRepository.findByOwnerId(currentUserService.getCurrentUserId());
            }
            case SCOUT -> farms = farmRepository.findByScoutId(currentUserService.getCurrentUserId());
            default -> farms = List.of();
        }

        return farms.stream()
                .sorted(Comparator.comparing(Farm::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Loads a single farm.
     */
    @Transactional(readOnly = true)
    public FarmResponse getFarm(UUID farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        farmAccess.requireViewAccess(farm);
        return mapToResponse(farm);
    }

    // -------------------------------
    // INTERNAL HELPERS
    // -------------------------------

    private Farm toFarmEntity(CreateFarmRequest request) {
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
                .externalId(request.externalId())
                .address(request.address())
                .city(request.city())
                .province(request.province())
                .postalCode(request.postalCode())
                .country(request.country())
                .owner(owner)
                .scout(scout)
                .contactName(request.contactName())
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
        farm.setExternalId(request.externalId());
        farm.setAddress(request.address());
        farm.setCity(request.city());
        farm.setProvince(request.province());
        farm.setPostalCode(request.postalCode());
        farm.setCountry(request.country());
        farm.setContactName(request.contactName());
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

    private Instant toInstant(java.time.LocalDateTime value) {
        return value != null ? value.toInstant(ZoneOffset.UTC) : null;
    }
}

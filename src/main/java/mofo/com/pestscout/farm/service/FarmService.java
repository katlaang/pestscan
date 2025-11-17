package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.dto.FarmResponse;
import mofo.com.pestscout.farm.dto.UpdateFarmRequest;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FarmStructureType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * SUPER_ADMIN ONLY.
     * Creates a new farm and assigns initial licensing settings.
     */
    @Transactional
    public FarmResponse createFarm(UpdateFarmRequest request) {
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
        List<Farm> farms = farmAccess.getFarmsVisibleToUser();
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

    private Farm toFarmEntity(UpdateFarmRequest request) {
        return Farm.builder()
                .name(request.name())
                .description(request.description())
                .externalId(request.externalId())
                .address(request.address())
                .city(request.city())
                .province(request.province())
                .postalCode(request.postalCode())
                .country(request.country())
                .contactName(request.contactName())
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .subscriptionStatus(
                        request.subscriptionStatus() != null ? request.subscriptionStatus() : SubscriptionStatus.PENDING_ACTIVATION
                )
                .subscriptionTier(
                        request.subscriptionTier() != null ? request.subscriptionTier() : SubscriptionTier.BASIC
                )
                .billingEmail(request.billingEmail())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .licensedAreaHectares(request.licensedAreaHectares())
                .licensedUnitQuota(request.licensedUnitQuota())
                .quotaDiscountPercentage(request.quotaDiscountPercentage())
                .structureType(resolveStructureType(request.structureType()))
                .defaultBayCount(request.defaultBayCount())
                .defaultBenchesPerBay(request.defaultBenchesPerBay())
                .defaultSpotChecksPerBench(request.defaultSpotChecksPerBench())
                .timezone(request.timezone())
                .build();
    }

    /**
     * Applies allowed updates depending on user role.
     */
    private void applyAllowedFarmUpdates(Farm farm, UpdateFarmRequest request) {

        boolean isSuperAdmin = farmAccess.isSuperAdmin();

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
        farm.setBillingEmail(request.billingEmail());
        farm.setTimezone(request.timezone());

        // Defaults (manager allowed)
        if (request.defaultBayCount() != null)
            farm.setDefaultBayCount(request.defaultBayCount());
        if (request.defaultBenchesPerBay() != null)
            farm.setDefaultBenchesPerBay(request.defaultBenchesPerBay());
        if (request.defaultSpotChecksPerBench() != null)
            farm.setDefaultSpotChecksPerBench(request.defaultSpotChecksPerBench());

        // License-only fields (SUPER_ADMIN ONLY)
        if (isSuperAdmin) {
            farm.setSubscriptionStatus(request.subscriptionStatus());
            farm.setSubscriptionTier(request.subscriptionTier());
            farm.setLicensedAreaHectares(request.licensedAreaHectares());
            farm.setLicensedUnitQuota(request.licensedUnitQuota());
            farm.setQuotaDiscountPercentage(request.quotaDiscountPercentage());
            farm.setLatitude(request.latitude());
            farm.setLongitude(request.longitude());
        }
    }

    private FarmResponse mapToResponse(Farm farm) {
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
                farm.getBillingEmail(),

                farm.getLicensedAreaHectares(),
                farm.getLicensedUnitQuota(),
                farm.getQuotaDiscountPercentage(),
                farm.getLicenseExpiryDate(),
                farm.getAutoRenewEnabled(),
                farm.getLongitude(),
                farm.getLatitude(),
                farm.getAccessLocked(),

                farm.getStructureType(),

                farm.getDefaultBayCount(),
                farm.getDefaultBenchesPerBay(),
                farm.getDefaultSpotChecksPerBench(),

                farm.getTimezone(),

                farm.getOwnerId(),
                farm.getScoutId(),

                farm.getCreatedAt(),
                farm.getUpdatedAt()
        );
    }


    private FarmStructureType resolveStructureType(FarmStructureType requested) {
        return requested != null ? requested : FarmStructureType.GREENHOUSE;
    }
}

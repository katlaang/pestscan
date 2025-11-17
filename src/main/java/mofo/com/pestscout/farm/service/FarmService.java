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

    private static final int DEFAULT_BAY_COUNT = 1;
    private static final int DEFAULT_BENCHES_PER_BAY = 0;
    private static final int DEFAULT_SPOT_CHECKS = 1;

    private final FarmRepository farmRepository;

    /**
     * Create a new farm. The name must be unique (case insensitive).
     * Subscription status and tier are defaulted if not supplied.
     */
    @Transactional
    public FarmResponse createFarm(UpdateFarmRequest request) {
        farmRepository.findByNameIgnoreCase(request.name())
                .ifPresent(existing -> {
                    throw new ConflictException("Farm already exists with name " + request.name());
                });

        Farm farm = toFarmEntity(request);
        Farm saved = farmRepository.save(farm);
        return mapToResponse(saved);
    }

    /**
     * Update an existing farm. Name uniqueness is preserved.
     */
    @Transactional
    public FarmResponse updateFarm(UUID farmId, UpdateFarmRequest request) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        // if name changed, verify uniqueness
        if (!farm.getName().equalsIgnoreCase(request.name())) {
            farmRepository.findByNameIgnoreCase(request.name())
                    .filter(existing -> !existing.getId().equals(farmId))
                    .ifPresent(existing -> {
                        throw new ConflictException("Farm already exists with name " + request.name());
                    });
        }

        updateFarmEntity(farm, request);
        Farm saved = farmRepository.save(farm);
        return mapToResponse(saved);
    }

    /**
     * List all farms sorted by name.
     */
    @Transactional(readOnly = true)
    public List<FarmResponse> listFarms() {
        return farmRepository.findAll().stream()
                .sorted(Comparator.comparing(Farm::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Load one farm by id.
     */
    @Transactional(readOnly = true)
    public FarmResponse getFarm(UUID farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));
        return mapToResponse(farm);
    }

    /**
     * Map an incoming request into a new Farm entity.
     */
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
                .subscriptionStatus(request.subscriptionStatus() != null
                        ? request.subscriptionStatus()
                        : SubscriptionStatus.PENDING_ACTIVATION)
                .subscriptionTier(request.subscriptionTier() != null
                        ? request.subscriptionTier()
                        : SubscriptionTier.BASIC)
                .billingEmail(request.billingEmail())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .licensedAreaHectares(request.licensedAreaHectares())
                .licensedUnitQuota(request.licensedUnitQuota())
                .quotaDiscountPercentage(request.quotaDiscountPercentage())
                .structureType(resolveStructureType(request.structureType()))
                .defaultBayCount(resolveBayCount(request.defaultBayCount()))
                .defaultBenchesPerBay(resolveBenchesPerBay(request.defaultBenchesPerBay()))
                .defaultSpotChecksPerBench(resolveSpotChecks(request.defaultSpotChecksPerBench()))
                .timezone(request.timezone())
                .build();
    }

    /**
     * Apply updates from the request onto an existing Farm entity.
     */
    private void updateFarmEntity(Farm farm, UpdateFarmRequest request) {
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

        if (request.subscriptionStatus() != null) {
            farm.setSubscriptionStatus(request.subscriptionStatus());
        }
        if (request.subscriptionTier() != null) {
            farm.setSubscriptionTier(request.subscriptionTier());
        }

        farm.setBillingEmail(request.billingEmail());
        farm.setLatitude(request.latitude());
        farm.setLongitude(request.longitude());
        farm.setLicensedAreaHectares(request.licensedAreaHectares());
        farm.setLicensedUnitQuota(request.licensedUnitQuota());
        farm.setQuotaDiscountPercentage(request.quotaDiscountPercentage());

        if (request.structureType() != null) {
            farm.setStructureType(request.structureType());
        }

        if (request.defaultBayCount() != null) {
            farm.setDefaultBayCount(request.defaultBayCount());
        }
        if (request.defaultBenchesPerBay() != null) {
            farm.setDefaultBenchesPerBay(request.defaultBenchesPerBay());
        }
        if (request.defaultSpotChecksPerBench() != null) {
            farm.setDefaultSpotChecksPerBench(request.defaultSpotChecksPerBench());
        }

        farm.setTimezone(request.timezone());
    }

    /**
     * Convert a Farm entity to a response DTO for the API.
     */
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
                farm.getLatitude(),
                farm.getLongitude(),
                farm.getLicensedAreaHectares(),
                farm.getLicensedUnitQuota(),
                farm.getQuotaDiscountPercentage(),
                farm.getStructureType(),
                farm.getDefaultBayCount(),
                farm.getDefaultBenchesPerBay(),
                farm.getDefaultSpotChecksPerBench(),
                farm.getTimezone()
        );
    }

    private FarmStructureType resolveStructureType(FarmStructureType requested) {
        return requested != null ? requested : FarmStructureType.GREENHOUSE;
    }

    /**
     * Default values used when the farm level layout has not been configured.
     * These should match the defaults in Farm.resolveBayCount / resolveBenchesPerBay / resolveSpotChecksPerBench.
     */
    private Integer resolveBayCount(Integer requested) {
        return requested != null ? requested : DEFAULT_BAY_COUNT;
    }

    private Integer resolveBenchesPerBay(Integer requested) {
        return requested != null ? requested : DEFAULT_BENCHES_PER_BAY;
    }

    private Integer resolveSpotChecks(Integer requested) {
        return requested != null ? requested : DEFAULT_SPOT_CHECKS;
    }
}

package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.dto.FarmRequest;
import mofo.com.pestscout.farm.dto.FarmResponse;
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

    private static final int DEFAULT_BAY_COUNT = 6;
    private static final int DEFAULT_BENCHES_PER_BAY = 5;
    private static final int DEFAULT_SPOT_CHECKS = 3;

    private final FarmRepository farmRepository;

    @Transactional
    public FarmResponse createFarm(FarmRequest request) {
        farmRepository.findByNameIgnoreCase(request.getName())
                .ifPresent(existing -> {
                    throw new ConflictException("Farm already exists with name " + request.getName());
                });

        Farm farm = toFarmEntity(request);
        Farm saved = farmRepository.save(farm);
        return mapToResponse(saved);
    }

    @Transactional
    public FarmResponse updateFarm(UUID farmId, FarmRequest request) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        if (!farm.getName().equalsIgnoreCase(request.getName())) {
            farmRepository.findByNameIgnoreCase(request.getName())
                    .filter(existing -> !existing.getId().equals(farmId))
                    .ifPresent(existing -> {
                        throw new ConflictException("Farm already exists with name " + request.getName());
                    });
        }

        updateFarmEntity(farm, request);
        return mapToResponse(farmRepository.save(farm));
    }

    @Transactional(readOnly = true)
    public List<FarmResponse> listFarms() {
        return farmRepository.findAll().stream()
                .sorted(Comparator.comparing(Farm::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FarmResponse getFarm(UUID farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));
        return mapToResponse(farm);
    }

    private Farm toFarmEntity(FarmRequest request) {
        return Farm.builder()
                .name(request.getName())
                .description(request.getDescription())
                .externalId(request.getExternalId())
                .address(request.getAddress())
                .city(request.getCity())
                .province(request.getProvince())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .contactName(request.getContactName())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .subscriptionStatus(request.getSubscriptionStatus() != null
                        ? request.getSubscriptionStatus()
                        : SubscriptionStatus.PENDING_ACTIVATION)
                .subscriptionTier(request.getSubscriptionTier() != null
                        ? request.getSubscriptionTier()
                        : SubscriptionTier.BASIC)
                .billingEmail(request.getBillingEmail())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .licensedAreaHectares(request.getLicensedAreaHectares())
                .licensedUnitQuota(request.getLicensedUnitQuota())
                .quotaDiscountPercentage(request.getQuotaDiscountPercentage())
                .structureType(resolveStructureType(request.getStructureType()))
                .bayCount(resolveBayCount(request.getBayCount()))
                .benchesPerBay(resolveBenchesPerBay(request.getBenchesPerBay()))
                .spotChecksPerBench(resolveSpotChecks(request.getSpotChecksPerBench()))
                .timezone(request.getTimezone())
                .build();
    }

    private void updateFarmEntity(Farm farm, FarmRequest request) {
        farm.setName(request.getName());
        farm.setDescription(request.getDescription());
        farm.setExternalId(request.getExternalId());
        farm.setAddress(request.getAddress());
        farm.setCity(request.getCity());
        farm.setProvince(request.getProvince());
        farm.setPostalCode(request.getPostalCode());
        farm.setCountry(request.getCountry());
        farm.setContactName(request.getContactName());
        farm.setContactEmail(request.getContactEmail());
        farm.setContactPhone(request.getContactPhone());
        farm.setSubscriptionStatus(request.getSubscriptionStatus() != null
                ? request.getSubscriptionStatus()
                : farm.getSubscriptionStatus());
        farm.setSubscriptionTier(request.getSubscriptionTier() != null
                ? request.getSubscriptionTier()
                : farm.getSubscriptionTier());
        farm.setBillingEmail(request.getBillingEmail());
        farm.setLatitude(request.getLatitude());
        farm.setLongitude(request.getLongitude());
        farm.setLicensedAreaHectares(request.getLicensedAreaHectares());
        farm.setLicensedUnitQuota(request.getLicensedUnitQuota());
        farm.setQuotaDiscountPercentage(request.getQuotaDiscountPercentage());
        farm.setStructureType(request.getStructureType() != null
                ? request.getStructureType()
                : farm.getStructureType());
        farm.setBayCount(request.getBayCount() != null
                ? request.getBayCount()
                : farm.getBayCount());
        farm.setBenchesPerBay(request.getBenchesPerBay() != null
                ? request.getBenchesPerBay()
                : farm.getBenchesPerBay());
        farm.setSpotChecksPerBench(request.getSpotChecksPerBench() != null
                ? request.getSpotChecksPerBench()
                : farm.getSpotChecksPerBench());
        farm.setTimezone(request.getTimezone());
    }

    private FarmResponse mapToResponse(Farm farm) {
        return FarmResponse.builder()
                .id(farm.getId())
                .name(farm.getName())
                .description(farm.getDescription())
                .externalId(farm.getExternalId())
                .address(farm.getAddress())
                .city(farm.getCity())
                .province(farm.getProvince())
                .postalCode(farm.getPostalCode())
                .country(farm.getCountry())
                .contactName(farm.getContactName())
                .contactEmail(farm.getContactEmail())
                .contactPhone(farm.getContactPhone())
                .subscriptionStatus(farm.getSubscriptionStatus())
                .subscriptionTier(farm.getSubscriptionTier())
                .billingEmail(farm.getBillingEmail())
                .latitude(farm.getLatitude())
                .longitude(farm.getLongitude())
                .licensedAreaHectares(farm.getLicensedAreaHectares())
                .licensedUnitQuota(farm.getLicensedUnitQuota())
                .quotaDiscountPercentage(farm.getQuotaDiscountPercentage())
                .structureType(farm.getStructureType())
                .bayCount(farm.getBayCount())
                .benchesPerBay(farm.getBenchesPerBay())
                .spotChecksPerBench(farm.getSpotChecksPerBench())
                .timezone(farm.getTimezone())
                .build();
    }

    private FarmStructureType resolveStructureType(FarmStructureType requested) {
        return requested != null ? requested : FarmStructureType.GREENHOUSE;
    }

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

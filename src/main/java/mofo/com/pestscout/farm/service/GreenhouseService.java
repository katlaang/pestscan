package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.dto.*;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.Greenhouse;
import mofo.com.pestscout.farm.model.GreenhouseBayDefinition;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.GreenhouseRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GreenhouseService {

    private final FarmRepository farmRepository;
    private final GreenhouseRepository greenhouseRepository;
    private final FarmAccessService farmAccessService;
    private final CacheService cacheService;

    @Transactional
    public GreenhouseDto createGreenhouse(UUID farmId, CreateGreenhouseRequest request) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        farmAccessService.requireSuperAdmin();
        GreenhouseLayout layout = resolveCreateLayout(farm, request);

        Greenhouse greenhouse = Greenhouse.builder()
                .farm(farm)
                .name(request.name())
                .description(request.description())
                .bayCount(layout.bayCount())
                .benchesPerBay(layout.maxBedCount())
                .spotChecksPerBench(request.spotChecksPerBench() != null ? request.spotChecksPerBench() : farm.resolveSpotChecksPerBench())
                .areaHectares(request.areaHectares())
                .bayTags(layout.bayTags())
                .benchTags(layout.bedTags())
                .bays(layout.bays())
                .build();

        Greenhouse saved = greenhouseRepository.save(greenhouse);
        cacheService.evictFarmCachesAfterCommit(farmId);
        return toDto(saved);
    }

    @Transactional
    public GreenhouseDto updateGreenhouse(UUID greenhouseId, UpdateGreenhouseRequest request) {
        Greenhouse greenhouse = greenhouseRepository.findById(greenhouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Greenhouse", "id", greenhouseId));

        farmAccessService.requireAdminOrSuperAdmin(greenhouse.getFarm());
        boolean isSuperAdmin = farmAccessService.isSuperAdmin();

        if (isSuperAdmin && request.bays() != null) {
            applyLayout(greenhouse, resolveLayoutFromBays(request.bays(), request.benchTags()));
        }
        if (isSuperAdmin && request.name() != null) {
            greenhouse.setName(request.name());
        }
        if (isSuperAdmin && request.description() != null) {
            greenhouse.setDescription(request.description());
        }
        if (isSuperAdmin && request.bays() == null && hasLegacyLayoutUpdates(request)) {
            applyLayout(greenhouse, resolveLegacyLayout(
                    greenhouse.getFarm(),
                    request.bayCount(),
                    request.benchesPerBay(),
                    request.bayTags(),
                    request.benchTags()
            ));
        } else {
            if (isSuperAdmin && request.bayTags() != null) {
                greenhouse.setBayTags(normalizeTags(request.bayTags()));
            }
            if (isSuperAdmin && request.benchTags() != null) {
                greenhouse.setBenchTags(normalizeTags(request.benchTags()));
            }
        }
        if (request.bays() == null && request.bayCount() != null) {
            greenhouse.setBayCount(request.bayCount());
        }
        if (request.bays() == null && request.benchesPerBay() != null) {
            greenhouse.setBenchesPerBay(request.benchesPerBay());
        }
        if (request.spotChecksPerBench() != null) {
            greenhouse.setSpotChecksPerBench(request.spotChecksPerBench());
        }
        if (isSuperAdmin && request.areaHectares() != null) {
            greenhouse.setAreaHectares(request.areaHectares());
        }

        Greenhouse saved = greenhouseRepository.save(greenhouse);
        cacheService.evictFarmCachesAfterCommit(greenhouse.getFarm().getId());
        return toDto(saved);
    }

    @Transactional
    public void deleteGreenhouse(UUID greenhouseId) {
        Greenhouse greenhouse = greenhouseRepository.findById(greenhouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Greenhouse", "id", greenhouseId));

        farmAccessService.requireSuperAdmin();
        greenhouseRepository.delete(greenhouse);
        cacheService.evictFarmCachesAfterCommit(greenhouse.getFarm().getId());
    }

    @Transactional(readOnly = true)
    @Cacheable(
            value = "greenhouses",
            keyGenerator = "tenantAwareKeyGenerator",
            unless = "#result == null"
    )
    public GreenhouseDto getGreenhouse(UUID greenhouseId) {
        Greenhouse greenhouse = greenhouseRepository.findById(greenhouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Greenhouse", "id", greenhouseId));

        farmAccessService.requireViewAccess(greenhouse.getFarm());
        return toDto(greenhouse);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            value = "greenhouses",
            keyGenerator = "tenantAwareKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<GreenhouseDto> listGreenhouses(UUID farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        farmAccessService.requireViewAccess(farm);
        return greenhouseRepository.findByFarmId(farmId).stream()
                .sorted(Comparator.comparing(Greenhouse::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    private GreenhouseDto toDto(Greenhouse greenhouse) {
        return new GreenhouseDto(
                greenhouse.getId(),
                greenhouse.getVersion(),
                greenhouse.getFarm().getId(),
                greenhouse.getName(),
                greenhouse.getDescription(),
                greenhouse.getBayCount(),
                greenhouse.getBenchesPerBay(),
                greenhouse.getSpotChecksPerBench(),
                List.copyOf(greenhouse.getBayTags()),
                List.copyOf(greenhouse.getBenchTags()),
                greenhouse.getActive(),
                greenhouse.getAreaHectares(),
                mapBayDtos(greenhouse)
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

    private GreenhouseLayout resolveCreateLayout(Farm farm, CreateGreenhouseRequest request) {
        if (request.bays() != null && !request.bays().isEmpty()) {
            return resolveLayoutFromBays(request.bays(), request.benchTags());
        }
        return resolveLegacyLayout(
                farm,
                request.bayCount(),
                request.benchesPerBay(),
                request.bayTags(),
                request.benchTags()
        );
    }

    private GreenhouseLayout resolveLayoutFromBays(List<GreenhouseBayRequest> requestedBays, List<String> requestedBedTags) {
        List<GreenhouseBayDefinition> bays = normalizeBays(requestedBays);
        int maxBedCount = bays.stream()
                .mapToInt(GreenhouseBayDefinition::getBedCount)
                .max()
                .orElse(0);
        List<String> bedTags = normalizeTags(requestedBedTags);
        if (!bedTags.isEmpty() && bedTags.size() < maxBedCount) {
            throw new BadRequestException("Provided bed tags do not cover all configured beds.");
        }
        return new GreenhouseLayout(
                bays.size(),
                maxBedCount,
                bays.stream().map(GreenhouseBayDefinition::getBayTag).toList(),
                defaultTags(bedTags, "Bed", maxBedCount),
                bays
        );
    }

    private GreenhouseLayout resolveLegacyLayout(
            Farm farm,
            Integer requestedBayCount,
            Integer requestedBedsPerBay,
            List<String> requestedBayTags,
            List<String> requestedBedTags
    ) {
        int bayCount = requestedBayCount != null ? requestedBayCount : farm.resolveBayCount();
        int maxBedCount = requestedBedsPerBay != null ? requestedBedsPerBay : farm.resolveBenchesPerBay();
        return new GreenhouseLayout(
                bayCount,
                maxBedCount,
                defaultTags(normalizeTags(requestedBayTags), "Bay", bayCount),
                defaultTags(normalizeTags(requestedBedTags), "Bed", maxBedCount),
                List.of()
        );
    }

    private List<GreenhouseBayDefinition> normalizeBays(List<GreenhouseBayRequest> requestedBays) {
        if (requestedBays == null || requestedBays.isEmpty()) {
            return List.of();
        }

        List<GreenhouseBayDefinition> bays = requestedBays.stream()
                .map(request -> {
                    String bayTag = request.bayTag() != null ? request.bayTag().trim() : null;
                    if (bayTag == null || bayTag.isBlank()) {
                        throw new BadRequestException("Each greenhouse bay must have a name.");
                    }
                    if (request.bedCount() == null || request.bedCount() < 1) {
                        throw new BadRequestException("Each greenhouse bay must define at least one bed.");
                    }
                    return GreenhouseBayDefinition.builder()
                            .bayTag(bayTag)
                            .bedCount(request.bedCount())
                            .build();
                })
                .toList();

        long distinctTags = bays.stream()
                .map(GreenhouseBayDefinition::getBayTag)
                .distinct()
                .count();
        if (distinctTags != bays.size()) {
            throw new BadRequestException("Greenhouse bay names must be unique.");
        }
        return bays;
    }

    private List<String> defaultTags(List<String> providedTags, String prefix, int count) {
        if (count <= 0) {
            return providedTags;
        }
        if (providedTags.isEmpty()) {
            return java.util.stream.IntStream.rangeClosed(1, count)
                    .mapToObj(index -> prefix + "-" + index)
                    .toList();
        }
        if (providedTags.size() >= count) {
            return providedTags.stream()
                    .limit(count)
                    .toList();
        }

        List<String> paddedTags = new java.util.ArrayList<>(providedTags);
        for (int index = paddedTags.size() + 1; index <= count; index++) {
            paddedTags.add(prefix + "-" + index);
        }
        return List.copyOf(paddedTags);
    }

    private boolean hasLegacyLayoutUpdates(UpdateGreenhouseRequest request) {
        return request.bayCount() != null
                || request.benchesPerBay() != null
                || request.bayTags() != null
                || request.benchTags() != null;
    }

    private void applyLayout(Greenhouse greenhouse, GreenhouseLayout layout) {
        greenhouse.setBayCount(layout.bayCount());
        greenhouse.setBenchesPerBay(layout.maxBedCount());
        greenhouse.setBayTags(layout.bayTags());
        greenhouse.setBenchTags(layout.bedTags());
        greenhouse.setBays(layout.bays());
    }

    private List<GreenhouseBayDto> mapBayDtos(Greenhouse greenhouse) {
        List<GreenhouseBayDefinition> bays = greenhouse.getBays();
        if (bays != null && !bays.isEmpty()) {
            return java.util.stream.IntStream.range(0, bays.size())
                    .mapToObj(index -> {
                        GreenhouseBayDefinition bay = bays.get(index);
                        List<String> bedTags = defaultTags(List.of(), "Bed", bay.getBedCount());
                        return new GreenhouseBayDto(index + 1, bay.getBayTag(), bay.getBedCount(), bedTags);
                    })
                    .toList();
        }

        List<String> bayTags = greenhouse.getBayTags() != null && !greenhouse.getBayTags().isEmpty()
                ? greenhouse.getBayTags()
                : defaultTags(List.of(), "Bay", greenhouse.resolvedBayCount());
        List<String> bedTags = greenhouse.resolvedBedTags();
        int bedCount = greenhouse.resolvedBenchesPerBay();
        return java.util.stream.IntStream.range(0, greenhouse.resolvedBayCount())
                .mapToObj(index -> new GreenhouseBayDto(
                        index + 1,
                        bayTags.get(index),
                        bedCount,
                        bedTags
                ))
                .toList();
    }

    private record GreenhouseLayout(
            int bayCount,
            int maxBedCount,
            List<String> bayTags,
            List<String> bedTags,
            List<GreenhouseBayDefinition> bays
    ) {
    }

}

package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.dto.CreateGreenhouseRequest;
import mofo.com.pestscout.farm.dto.GreenhouseDto;
import mofo.com.pestscout.farm.dto.UpdateGreenhouseRequest;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.Greenhouse;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.GreenhouseRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import mofo.com.pestscout.common.service.CacheService;
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

        Greenhouse greenhouse = Greenhouse.builder()
                .farm(farm)
                .name(request.name())
                .description(request.description())
                .bayCount(request.bayCount())
                .benchesPerBay(request.benchesPerBay())
                .spotChecksPerBench(request.spotChecksPerBench())
                .bayTags(normalizeTags(request.bayTags()))
                .benchTags(normalizeTags(request.benchTags()))
                .build();

        Greenhouse saved = greenhouseRepository.save(greenhouse);
        cacheService.evictFarmCaches(farmId);
        return toDto(saved);
    }

    @Transactional
    public GreenhouseDto updateGreenhouse(UUID greenhouseId, UpdateGreenhouseRequest request) {
        Greenhouse greenhouse = greenhouseRepository.findById(greenhouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Greenhouse", "id", greenhouseId));

        farmAccessService.requireAdminOrSuperAdmin(greenhouse.getFarm());
        boolean isSuperAdmin = farmAccessService.isSuperAdmin();

        if (isSuperAdmin && request.name() != null) {
            greenhouse.setName(request.name());
        }
        if (isSuperAdmin && request.description() != null) {
            greenhouse.setDescription(request.description());
        }
        if (isSuperAdmin && request.bayTags() != null) {
            greenhouse.setBayTags(normalizeTags(request.bayTags()));
        }
        if (isSuperAdmin && request.benchTags() != null) {
            greenhouse.setBenchTags(normalizeTags(request.benchTags()));
        }
        if (request.bayCount() != null) {
            greenhouse.setBayCount(request.bayCount());
        }
        if (request.benchesPerBay() != null) {
            greenhouse.setBenchesPerBay(request.benchesPerBay());
        }
        if (request.spotChecksPerBench() != null) {
            greenhouse.setSpotChecksPerBench(request.spotChecksPerBench());
        }

        Greenhouse saved = greenhouseRepository.save(greenhouse);
        cacheService.evictFarmCaches(greenhouse.getFarm().getId());
        return toDto(saved);
    }

    @Transactional
    public void deleteGreenhouse(UUID greenhouseId) {
        Greenhouse greenhouse = greenhouseRepository.findById(greenhouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Greenhouse", "id", greenhouseId));

        farmAccessService.requireSuperAdmin();
        greenhouseRepository.delete(greenhouse);
        cacheService.evictFarmCaches(greenhouse.getFarm().getId());
    }

    @Transactional(readOnly = true)
    @Cacheable(
            value = "greenhouses",
            key = "'detail::' + #greenhouseId.toString()",
            unless = "#result == null"
    )
    public GreenhouseDto getGreenhouse(UUID greenhouseId) {
        Greenhouse greenhouse = greenhouseRepository.findById(greenhouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Greenhouse", "id", greenhouseId));

        return toDto(greenhouse);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            value = "greenhouses",
            key = "'farm::' + #farmId.toString()",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<GreenhouseDto> listGreenhouses(UUID farmId) {
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
                greenhouse.getActive()
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

}

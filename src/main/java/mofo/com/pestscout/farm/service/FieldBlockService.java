package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.dto.CreateFieldBlockRequest;
import mofo.com.pestscout.farm.dto.FieldBlockDto;
import mofo.com.pestscout.farm.dto.UpdateFieldBlockRequest;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FieldBlock;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.FieldBlockRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FieldBlockService {

    private final FarmRepository farmRepository;
    private final FieldBlockRepository fieldBlockRepository;
    private final FarmAccessService farmAccessService;
    private final CacheService cacheService;
    private final FarmAreaAllocationService farmAreaAllocationService;

    @Transactional
    public FieldBlockDto createFieldBlock(UUID farmId, CreateFieldBlockRequest request) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        farmAccessService.requireAdminOrSuperAdmin(farm);
        farmAreaAllocationService.validateStructureArea(farm, request.areaHectares(), null, null);

        FieldBlock block = FieldBlock.builder()
                .farm(farm)
                .name(request.name())
                .bayCount(request.bayCount() != null ? request.bayCount() : 0)
                .spotChecksPerBay(request.spotChecksPerBay() != null ? request.spotChecksPerBay() : farm.resolveSpotChecksPerBench())
                .areaHectares(request.areaHectares())
                .cropType(normalizeNullableText(request.cropType()))
                .bayTags(normalizeTags(request.bayTags()))
                .active(request.active() != null ? request.active() : Boolean.TRUE)
                .build();

        FieldBlock saved = fieldBlockRepository.save(block);
        cacheService.evictFarmCachesAfterCommit(farmId);
        return toDto(saved);
    }

    @Transactional
    public FieldBlockDto updateFieldBlock(UUID fieldBlockId, UpdateFieldBlockRequest request) {
        FieldBlock block = fieldBlockRepository.findById(fieldBlockId)
                .orElseThrow(() -> new ResourceNotFoundException("FieldBlock", "id", fieldBlockId));

        farmAccessService.requireAdminOrSuperAdmin(block.getFarm());
        farmAreaAllocationService.validateStructureArea(
                block.getFarm(),
                request.areaHectares() != null ? request.areaHectares() : block.getAreaHectares(),
                null,
                block.getId()
        );

        if (request.name() != null) {
            block.setName(request.name());
        }
        if (request.bayCount() != null) {
            block.setBayCount(request.bayCount());
        }
        if (request.spotChecksPerBay() != null) {
            block.setSpotChecksPerBay(request.spotChecksPerBay());
        }
        if (request.bayTags() != null) {
            block.setBayTags(normalizeTags(request.bayTags()));
        }
        if (request.active() != null) {
            block.setActive(request.active());
        }
        if (request.areaHectares() != null) {
            block.setAreaHectares(request.areaHectares());
        }
        if (request.cropType() != null) {
            block.setCropType(normalizeNullableText(request.cropType()));
        }

        FieldBlock saved = fieldBlockRepository.save(block);
        cacheService.evictFarmCachesAfterCommit(block.getFarm().getId());
        return toDto(saved);
    }

    @Transactional
    public void deleteFieldBlock(UUID fieldBlockId) {
        FieldBlock block = fieldBlockRepository.findById(fieldBlockId)
                .orElseThrow(() -> new ResourceNotFoundException("FieldBlock", "id", fieldBlockId));

        farmAccessService.requireSuperAdmin();
        fieldBlockRepository.delete(block);
        cacheService.evictFarmCachesAfterCommit(block.getFarm().getId());
    }

    @Transactional(readOnly = true)
    @Cacheable(
            value = "field-blocks",
            keyGenerator = "tenantAwareKeyGenerator",
            unless = "#result == null"
    )
    public FieldBlockDto getFieldBlock(UUID fieldBlockId) {
        FieldBlock block = fieldBlockRepository.findById(fieldBlockId)
                .orElseThrow(() -> new ResourceNotFoundException("FieldBlock", "id", fieldBlockId));

        farmAccessService.requireViewAccess(block.getFarm());
        return toDto(block);
    }

    @Transactional(readOnly = true)
    @Cacheable(
            value = "field-blocks",
            keyGenerator = "tenantAwareKeyGenerator",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<FieldBlockDto> listFieldBlocks(UUID farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        farmAccessService.requireViewAccess(farm);
        return fieldBlockRepository.findByFarmId(farmId).stream()
                .sorted(Comparator.comparing(FieldBlock::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    private FieldBlockDto toDto(FieldBlock block) {
        return new FieldBlockDto(
                block.getId(),
                block.getVersion(),          // from BaseEntity
                block.getFarm().getId(),
                block.getName(),
                block.getBayCount(),
                block.getSpotChecksPerBay(),
                List.copyOf(block.getBayTags()),
                block.getActive(),
                block.getAreaHectares(),
                block.getCropType()
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

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

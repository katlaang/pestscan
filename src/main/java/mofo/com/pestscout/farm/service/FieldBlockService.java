package mofo.com.pestscout.farm.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.dto.CreateFieldBlockRequest;
import mofo.com.pestscout.farm.dto.FieldBlockDto;
import mofo.com.pestscout.farm.dto.UpdateFieldBlockRequest;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FieldBlock;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.FieldBlockRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
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

    @Transactional
    public FieldBlockDto createFieldBlock(UUID farmId, CreateFieldBlockRequest request) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        farmAccessService.requireSuperAdmin();

        FieldBlock block = FieldBlock.builder()
                .farm(farm)
                .name(request.name())
                .bayCount(request.bayCount())
                .spotChecksPerBay(request.spotChecksPerBay())
                .bayTags(normalizeTags(request.bayTags()))
                .active(request.active() != null ? request.active() : Boolean.TRUE)
                .build();

        FieldBlock saved = fieldBlockRepository.save(block);
        return toDto(saved);
    }

    @Transactional
    public FieldBlockDto updateFieldBlock(UUID fieldBlockId, UpdateFieldBlockRequest request) {
        FieldBlock block = fieldBlockRepository.findById(fieldBlockId)
                .orElseThrow(() -> new ResourceNotFoundException("FieldBlock", "id", fieldBlockId));

        farmAccessService.requireAdminOrSuperAdmin(block.getFarm());
        boolean isSuperAdmin = farmAccessService.isSuperAdmin();

        if (isSuperAdmin && request.name() != null) {
            block.setName(request.name());
        }
        if (request.bayCount() != null) {
            block.setBayCount(request.bayCount());
        }
        if (request.spotChecksPerBay() != null) {
            block.setSpotChecksPerBay(request.spotChecksPerBay());
        }
        if (farmAccessService.isSuperAdmin() && request.bayTags() != null) {
            block.setBayTags(normalizeTags(request.bayTags()));
        }
        if (request.active() != null) {
            if (isSuperAdmin) {
                block.setActive(request.active());
            }
        }

        FieldBlock saved = fieldBlockRepository.save(block);
        return toDto(saved);
    }

    @Transactional
    public void deleteFieldBlock(UUID fieldBlockId) {
        FieldBlock block = fieldBlockRepository.findById(fieldBlockId)
                .orElseThrow(() -> new ResourceNotFoundException("FieldBlock", "id", fieldBlockId));

        farmAccessService.requireSuperAdmin();
        fieldBlockRepository.delete(block);
    }

    @Transactional(readOnly = true)
    public FieldBlockDto getFieldBlock(UUID fieldBlockId) {
        FieldBlock block = fieldBlockRepository.findById(fieldBlockId)
                .orElseThrow(() -> new ResourceNotFoundException("FieldBlock", "id", fieldBlockId));

        return toDto(block);
    }

    @Transactional(readOnly = true)
    public List<FieldBlockDto> listFieldBlocks(UUID farmId) {
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
                block.getActive()
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

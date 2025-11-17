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

    @Transactional
    public FieldBlockDto createFieldBlock(UUID farmId, CreateFieldBlockRequest request) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        FieldBlock block = FieldBlock.builder()
                .farm(farm)
                .name(request.name())
                .bayCount(request.bayCount())
                .spotChecksPerBay(request.spotChecksPerBay())
                .active(Boolean.TRUE.equals(request.active()))
                .build();

        FieldBlock saved = fieldBlockRepository.save(block);
        return toDto(saved);
    }

    @Transactional
    public FieldBlockDto updateFieldBlock(UUID fieldBlockId, UpdateFieldBlockRequest request) {
        FieldBlock block = fieldBlockRepository.findById(fieldBlockId)
                .orElseThrow(() -> new ResourceNotFoundException("FieldBlock", "id", fieldBlockId));

        if (request.name() != null) {
            block.setName(request.name());
        }
        if (request.bayCount() != null) {
            block.setBayCount(request.bayCount());
        }
        if (request.spotChecksPerBay() != null) {
            block.setSpotChecksPerBay(request.spotChecksPerBay());
        }
        if (request.active() != null) {
            block.setActive(request.active());
        }

        FieldBlock saved = fieldBlockRepository.save(block);
        return toDto(saved);
    }

    @Transactional
    public void deleteFieldBlock(UUID fieldBlockId) {
        FieldBlock block = fieldBlockRepository.findById(fieldBlockId)
                .orElseThrow(() -> new ResourceNotFoundException("FieldBlock", "id", fieldBlockId));

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
                block.getActive()
        );
    }
}

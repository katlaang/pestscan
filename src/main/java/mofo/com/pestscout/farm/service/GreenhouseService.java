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

    @Transactional
    public GreenhouseDto createGreenhouse(UUID farmId, CreateGreenhouseRequest request) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));

        Greenhouse greenhouse = Greenhouse.builder()
                .farm(farm)
                .name(request.name())
                .description(request.description())
                .bayCount(request.bayCount())
                .benchesPerBay(request.benchesPerBay())
                .spotChecksPerBench(request.spotChecksPerBench())
                .active(Boolean.TRUE.equals(request.active()))
                .build();

        Greenhouse saved = greenhouseRepository.save(greenhouse);
        return toDto(saved);
    }

    @Transactional
    public GreenhouseDto updateGreenhouse(UUID greenhouseId, UpdateGreenhouseRequest request) {
        Greenhouse greenhouse = greenhouseRepository.findById(greenhouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Greenhouse", "id", greenhouseId));

        if (request.name() != null) {
            greenhouse.setName(request.name());
        }
        if (request.description() != null) {
            greenhouse.setDescription(request.description());
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
        if (request.active() != null) {
            greenhouse.setActive(request.active());
        }

        Greenhouse saved = greenhouseRepository.save(greenhouse);
        return toDto(saved);
    }

    @Transactional
    public void deleteGreenhouse(UUID greenhouseId) {
        Greenhouse greenhouse = greenhouseRepository.findById(greenhouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Greenhouse", "id", greenhouseId));

        greenhouseRepository.delete(greenhouse);
    }

    @Transactional(readOnly = true)
    public GreenhouseDto getGreenhouse(UUID greenhouseId) {
        Greenhouse greenhouse = greenhouseRepository.findById(greenhouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Greenhouse", "id", greenhouseId));

        return toDto(greenhouse);
    }

    @Transactional(readOnly = true)
    public List<GreenhouseDto> listGreenhouses(UUID farmId) {
        return greenhouseRepository.findByFarmId(farmId).stream()
                .sorted(Comparator.comparing(Greenhouse::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto)
                .toList();
    }

    private GreenhouseDto toDto(Greenhouse greenhouse) {
        return new GreenhouseDto(
                greenhouse.getId(),
                greenhouse.getFarm().getId(),
                greenhouse.getName(),
                greenhouse.getDescription(),
                greenhouse.getBayCount(),
                greenhouse.getBenchesPerBay(),
                greenhouse.getSpotChecksPerBench(),
                greenhouse.resolvedBayCount(),
                greenhouse.resolvedBenchesPerBay(),
                greenhouse.resolvedSpotChecksPerBench(),
                greenhouse.getActive()
        );
    }

}

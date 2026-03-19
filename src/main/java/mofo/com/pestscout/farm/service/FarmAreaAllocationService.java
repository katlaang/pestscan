package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.farm.dto.CreateFieldBlockRequest;
import mofo.com.pestscout.farm.dto.CreateGreenhouseRequest;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FieldBlock;
import mofo.com.pestscout.farm.model.Greenhouse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class FarmAreaAllocationService {

    public void validateFarmStructureAreas(
            BigDecimal licensedAreaHectares,
            List<CreateGreenhouseRequest> greenhouses,
            List<CreateFieldBlockRequest> fieldBlocks
    ) {
        BigDecimal requestedArea = sumGreenhouseAreas(greenhouses).add(sumFieldAreas(fieldBlocks));
        assertWithinLicensedArea(licensedAreaHectares, requestedArea,
                "Configured greenhouse and field areas exceed the farm licensed hectares.");
    }

    public void validateCurrentAllocationWithinLicense(Farm farm, BigDecimal licensedAreaHectares) {
        assertWithinLicensedArea(
                licensedAreaHectares,
                calculateAllocatedArea(farm, null, null),
                "Licensed hectares cannot be reduced below the area already assigned to greenhouses and fields."
        );
    }

    public void validateStructureArea(Farm farm,
                                      BigDecimal requestedStructureArea,
                                      UUID greenhouseToExclude,
                                      UUID fieldBlockToExclude) {
        BigDecimal allocatedArea = calculateAllocatedArea(farm, greenhouseToExclude, fieldBlockToExclude)
                .add(normalizeArea(requestedStructureArea));
        assertWithinLicensedArea(
                farm.getLicensedAreaHectares(),
                allocatedArea,
                "Configured greenhouse and field areas exceed the farm licensed hectares."
        );
    }

    public BigDecimal calculateAllocatedArea(Farm farm, UUID greenhouseToExclude, UUID fieldBlockToExclude) {
        if (farm == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal greenhouseArea = farm.getGreenhouses().stream()
                .filter(greenhouse -> !greenhouse.isDeleted())
                .filter(greenhouse -> greenhouseToExclude == null || !greenhouseToExclude.equals(greenhouse.getId()))
                .map(Greenhouse::getAreaHectares)
                .map(this::normalizeArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal fieldArea = farm.getFieldBlocks().stream()
                .filter(fieldBlock -> !fieldBlock.isDeleted())
                .filter(fieldBlock -> fieldBlockToExclude == null || !fieldBlockToExclude.equals(fieldBlock.getId()))
                .map(FieldBlock::getAreaHectares)
                .map(this::normalizeArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return greenhouseArea.add(fieldArea);
    }

    private BigDecimal sumGreenhouseAreas(List<CreateGreenhouseRequest> greenhouses) {
        if (greenhouses == null || greenhouses.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return greenhouses.stream()
                .map(CreateGreenhouseRequest::areaHectares)
                .map(this::normalizeArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumFieldAreas(List<CreateFieldBlockRequest> fieldBlocks) {
        if (fieldBlocks == null || fieldBlocks.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return fieldBlocks.stream()
                .map(CreateFieldBlockRequest::areaHectares)
                .map(this::normalizeArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void assertWithinLicensedArea(BigDecimal licensedAreaHectares, BigDecimal allocatedArea, String message) {
        if (allocatedArea.compareTo(normalizeArea(licensedAreaHectares)) > 0) {
            throw new BadRequestException(message);
        }
    }

    private BigDecimal normalizeArea(BigDecimal area) {
        if (area == null) {
            return BigDecimal.ZERO;
        }
        if (area.signum() < 0) {
            throw new BadRequestException("Area hectares cannot be negative.");
        }
        return area;
    }
}

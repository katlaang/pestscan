package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateGreenhouseRequest(
        @NotNull UUID farmId,
        @NotBlank String name,
        Integer bayCount,
        Integer benchesPerBay,
        Integer spotChecksPerBench,
        Boolean active,
        String description
) {
}

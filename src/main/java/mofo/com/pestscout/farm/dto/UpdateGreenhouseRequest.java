package mofo.com.pestscout.farm.dto;


import jakarta.validation.constraints.NotNull;

public record UpdateGreenhouseRequest(
        @NotNull Long version,
        String name,
        Integer bayCount,
        Integer benchesPerBay,
        Integer spotChecksPerBench,
        String description,
        Boolean active
) {
}

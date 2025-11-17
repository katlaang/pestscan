package mofo.com.pestscout.farm.dto;

public record UpdateGreenhouseRequestAdmin(
        String name,
        String description,
        Integer bayCount,
        Integer benchesPerBay,
        Integer spotChecksPerBench,
        Boolean active
) {
}

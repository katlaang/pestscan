package mofo.com.pestscout.farm.dto;

public record UpdateGreenhouseRequestAdmin(
        String name,
        String description,
        Integer bayCount,
        Integer benchesPerBay,
        Integer spotChecksPerBench,
        java.util.List<String> bayTags,
        java.util.List<String> benchTags,
        Boolean active
) {
}

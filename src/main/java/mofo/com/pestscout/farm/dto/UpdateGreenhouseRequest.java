package mofo.com.pestscout.farm.dto;

public record UpdateGreenhouseRequest(

        String name,
        Integer bayCount,
        Integer benchesPerBay,
        Integer spotChecksPerBench,
        Boolean active,
        String description,
        java.util.List<String> bayTags,
        java.util.List<String> benchTags
) {
}

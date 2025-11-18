package mofo.com.pestscout.farm.dto;

public record UpdateFieldBlockRequestAdmin(
        String name,
        Integer bayCount,
        Integer spotChecksPerBay,
        Boolean active
) {
}


package mofo.com.pestscout.analytics.dto;


public record AlertDto(
        String location,
        String pest,
        String severity,
        int count,
        String time
) {
}



package mofo.com.pestscout.scouting.dto;

public record ReopenSessionRequest(
        String comment,
        String deviceId,
        String deviceType,
        String location,
        String actorName
) {
}

package mofo.com.pestscout.scouting.dto;

import jakarta.validation.constraints.NotNull;

public record RemoteStartSessionRequest(
        @NotNull Long version,
        String comment,
        String deviceId,
        String deviceType,
        String location,
        String actorName
) {
}

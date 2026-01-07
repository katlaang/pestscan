package mofo.com.pestscout.scouting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitSessionRequest(
        @NotNull Long version,
        @NotNull Boolean confirmationAcknowledged,
        @NotBlank(message = "Scout name is required for audit") String actorName,
        String comment,
        String deviceId,
        String deviceType,
        String location
) {
}

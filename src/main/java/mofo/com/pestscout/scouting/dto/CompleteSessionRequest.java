package mofo.com.pestscout.scouting.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompleteSessionRequest(
        @NotNull Long version,
        @NotNull Boolean confirmationAcknowledged,
        String comment,
        String deviceId,
        String deviceType,
        String location,
        @NotBlank(message = "Actor name is required for audit") String actorName
) {
}

package mofo.com.pestscout.scouting.dto;


import jakarta.validation.constraints.NotNull;

public record CompleteSessionRequest(
        @NotNull Long version,
        @NotNull Boolean confirmationAcknowledged
) {
}

package mofo.com.pestscout.farm.dto;


import jakarta.validation.constraints.NotNull;

public record CompleteSessionRequest(
        @NotNull Long version,
        @NotNull Boolean confirmationAcknowledged
) {
}

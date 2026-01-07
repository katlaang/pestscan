package mofo.com.pestscout.scouting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record PhotoMetadataRequest(
        @NotNull UUID sessionId,
        UUID observationId,
        @NotBlank String localPhotoId,
        String purpose,
        LocalDateTime capturedAt
) {
}


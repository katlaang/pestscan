package mofo.com.pestscout.scouting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PhotoUploadConfirmationRequest(
        @NotNull UUID sessionId,
        @NotBlank String localPhotoId,
        @NotBlank String objectKey
) {
}


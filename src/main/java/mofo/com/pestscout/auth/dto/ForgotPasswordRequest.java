package mofo.com.pestscout.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mofo.com.pestscout.auth.model.ResetChannel;

/**
 * Request to generate a password reset token.
 */
public record ForgotPasswordRequest(
        @Email(message = "Please provide a valid email")
        @NotBlank(message = "Email is required")
        String email,

        /**
         * Optional context for how the reset will be verified. Defaults to EMAIL.
         */
        ResetChannel channel,

        /**
         * Optional notes recorded at the time of the request.
         */
        @Size(max = 512, message = "Request notes cannot exceed 512 characters")
        String requestNotes
) {
    public ResetChannel resolvedChannel() {
        return channel == null ? ResetChannel.EMAIL : channel;
    }
}

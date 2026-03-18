package mofo.com.pestscout.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import mofo.com.pestscout.auth.model.ResetChannel;

import java.time.LocalDate;

/**
 * Request to complete a password reset with contextual safety checks.
 */
public record ResetPasswordRequest(
        @JsonAlias({"resetToken"})
        String token,

        @JsonAlias({"newPassword"})
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
        String password,

        @JsonAlias({"channel"})
        ResetChannel verificationChannel,

        /**
         * Caller provided first name (required for PHONE_CALL resets).
         */
        String firstName,

        /**
         * Caller provided last name (required for PHONE_CALL resets).
         */
        String lastName,

        /**
         * Caller provided email (required for PHONE_CALL resets).
         */
        @Email(message = "Please provide a valid email")
        String email,

        /**
         * Name of the person who called (required for PHONE_CALL resets).
         */
        String callerName,

        /**
         * Callback number confirmed during the call (required for PHONE_CALL resets).
         */
        String callbackNumber,

        /**
         * Notes about what was validated before issuing the reset (required for PHONE_CALL resets).
         */
        @Size(max = 1024, message = "Verification notes cannot exceed 1024 characters")
        String verificationNotes,

        /**
         * Answer provided for when the account was last accessed (required for PHONE_CALL resets).
         */
        @PastOrPresent(message = "Last login date cannot be in the future")
        LocalDate lastLoginDate,

        /**
         * Customer number supplied by the caller (required for PHONE_CALL resets).
         */
        @Size(max = 255, message = "Customer number cannot exceed 255 characters")
        String customerNumber
) {
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String resolvedToken(String fallbackToken) {
        return hasText(token) ? token.trim() : trimToNull(fallbackToken);
    }

    public ResetChannel resolvedVerificationChannel(ResetChannel fallbackChannel) {
        if (verificationChannel != null) {
            return verificationChannel;
        }
        if (fallbackChannel != null) {
            return fallbackChannel;
        }
        return ResetChannel.EMAIL;
    }

    public ResetPasswordRequest withResolvedToken(String fallbackToken) {
        return new ResetPasswordRequest(
                resolvedToken(fallbackToken),
                password,
                verificationChannel,
                firstName,
                lastName,
                email,
                callerName,
                callbackNumber,
                verificationNotes,
                lastLoginDate,
                customerNumber
        );
    }
}

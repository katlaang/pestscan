package mofo.com.pestscout.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import mofo.com.pestscout.auth.model.Role;

import java.util.UUID;

/**
 * User registration request DTO
 */
@Builder
public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "Phone number is required")
        @Size(max = 50)
        String phoneNumber,

        @NotNull(message = "Role is required")
        Role role,

        // Optional for super admin registration
        UUID farmId
) {
}


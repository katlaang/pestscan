package mofo.com.pestscout.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mofo.com.pestscout.auth.model.Role;

/**
 * Request payload for updating an existing user.
 * <p>
 * This DTO is designed for partial updates:
 * - All fields are optional.
 * - In the service layer you decide which non-null fields to apply.
 * <p>
 * Typical usage:
 * - Farm Admin updating a user in their farm.
 * - Super Admin updating any user across farms.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    /**
     * New email for the user.
     * Optional.
     * If not null, the service will validate uniqueness before applying.
     */
    @Email(message = "Invalid email format")
    private String email;

    /**
     * New password for the user.
     * Optional.
     * If not null, the service should encode it (BCrypt) before saving.
     */
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /**
     * New first name for the user.
     * Optional.
     */
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    /**
     * New last name for the user.
     * Optional.
     */
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    /**
     * New phone number for the user.
     * Optional.
     * Validation can be strengthened later with a custom pattern if needed.
     */
    @Size(max = 50, message = "Phone number must not exceed 50 characters")
    private String phoneNumber;

    /**
     * New country for the user.
     * Optional.
     */
    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;

    /**
     * New role for the user.
     * Optional.
     * Only Farm Admin or Super Admin should be allowed to change this.
     */
    private Role role;

    /**
     * New enabled status for the user.
     * Optional.
     * If not null, controls whether the user can log in.
     */
    private Boolean isEnabled;
}

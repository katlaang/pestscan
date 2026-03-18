package mofo.com.pestscout.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mofo.com.pestscout.auth.model.Role;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User data transfer object (excludes sensitive fields like password)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private UUID id;
    private UUID farmId;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String country;
    private String customerNumber;
    private Role role;
    private Boolean isEnabled;
    private Boolean active;
    private Boolean deleted;
    private Boolean passwordChangeRequired;
    private Boolean reactivationRequired;
    private Boolean passwordExpired;
    private LocalDateTime passwordExpiresAt;
    private LocalDateTime temporaryPasswordExpiresAt;
    private LocalDateTime deletedAt;
    private LocalDateTime lastLogin;
    private LocalDateTime lastActivityAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

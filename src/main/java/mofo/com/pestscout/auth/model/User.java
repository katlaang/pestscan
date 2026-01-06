package mofo.com.pestscout.auth.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * User entity representing system users
 * Maps to the 'users' table in the database
 */

@Entity
@Table(name = "users")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {


    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(name = "first_name", length = 255)
    private String firstName;

    @Column(name = "last_name", length = 255)
    private String lastName;

    @Column(name = "phone_number", nullable = false, length = 50)
    private String phoneNumber;

    @Column(name = "customer_number", nullable = false, unique = true, length = 100)
    private String customerNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    @Builder.Default
    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;


    /**
     * Check if user is active
     */
    @Transient
    public boolean isActive() {
        return Boolean.TRUE.equals(isEnabled);
    }

    /**
     * Update last login timestamp
     */
    public void updateLastLogin() {
        this.lastLogin = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + getId() +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", role=" + role +
                '}';
    }
}

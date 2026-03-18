package mofo.com.pestscout.auth.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.Objects;

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

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "customer_number", nullable = false, unique = true, length = 100)
    private String customerNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    @Builder.Default
    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    @Builder.Default
    @Column(name = "password_change_required", nullable = false)
    private Boolean passwordChangeRequired = false;

    @Column(name = "password_expires_at")
    private LocalDateTime passwordExpiresAt;

    @Column(name = "temporary_password_expires_at")
    private LocalDateTime temporaryPasswordExpiresAt;

    @Builder.Default
    @Column(name = "reactivation_required", nullable = false)
    private Boolean reactivationRequired = false;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;


    /**
     * Check if user is active
     */
    @Transient
    public boolean isActive() {
        return Boolean.TRUE.equals(isEnabled) && !isDeleted() && !isTemporaryPasswordExpired();
    }

    @Transient
    public boolean isTemporaryPasswordExpired() {
        return Boolean.TRUE.equals(passwordChangeRequired)
                && temporaryPasswordExpiresAt != null
                && LocalDateTime.now().isAfter(temporaryPasswordExpiresAt);
    }

    @Transient
    public boolean isPasswordExpired() {
        return passwordExpiresAt != null && LocalDateTime.now().isAfter(passwordExpiresAt);
    }

    /**
     * Update last login timestamp
     */
    public void updateLastLogin() {
        this.lastLogin = LocalDateTime.now();
    }

    public void recordActivity() {
        this.lastActivityAt = LocalDateTime.now();
    }

    public void applyPassword(String encodedPassword, LocalDateTime expiresAt) {
        this.password = Objects.requireNonNull(encodedPassword, "encodedPassword");
        this.passwordExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public void beginTemporaryPasswordWindow(LocalDateTime expiresAt) {
        this.passwordChangeRequired = true;
        this.temporaryPasswordExpiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.reactivationRequired = false;
        this.isEnabled = true;
        restore();
    }

    public void completePasswordReset() {
        this.passwordChangeRequired = false;
        this.temporaryPasswordExpiresAt = null;
        this.reactivationRequired = false;
        this.isEnabled = true;
    }

    public void markTemporaryPasswordExpired() {
        markDeleted();
        this.isEnabled = false;
        this.reactivationRequired = true;
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

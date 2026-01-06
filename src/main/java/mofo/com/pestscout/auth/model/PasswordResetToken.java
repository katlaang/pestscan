package mofo.com.pestscout.auth.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One-time password reset token with audit details for how the reset was verified.
 *
 * Tokens are persisted so we can: (1) enforce single-use + expiry checks server-side,
 * (2) keep an audit trail of the verification answers and the support user who
 * completed a phone reset, and (3) avoid leaking account existence by handling all
 * reset requests the same while still recording them for fraud review.
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_channel", nullable = false, length = 50)
    private ResetChannel verificationChannel;

    @Column(name = "caller_name", length = 255)
    private String callerName;

    @Column(name = "callback_number", length = 50)
    private String callbackNumber;

    @Column(name = "verification_notes", length = 2048)
    private String verificationNotes;

    @Column(name = "first_name_confirmation", length = 255)
    private String firstNameConfirmation;

    @Column(name = "last_name_confirmation", length = 255)
    private String lastNameConfirmation;

    @Column(name = "email_confirmation", length = 255)
    private String emailConfirmation;

    @Column(name = "last_login_verified_on")
    private LocalDate lastLoginVerifiedOn;

    @Column(name = "customer_number_confirmation", length = 255)
    private String customerNumberConfirmation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_user_id")
    private User performedBy;

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}

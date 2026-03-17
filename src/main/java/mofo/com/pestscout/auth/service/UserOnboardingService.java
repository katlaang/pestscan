package mofo.com.pestscout.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mofo.com.pestscout.auth.model.PasswordResetToken;
import mofo.com.pestscout.auth.model.ResetChannel;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.PasswordResetTokenRepository;
import mofo.com.pestscout.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserOnboardingService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;

    @Value("${app.auth.temporary-password-valid-days:5}")
    private long temporaryPasswordValidDays;

    @Value("${app.auth.public-reset-url:http://localhost:3000/reset-password}")
    private String publicResetUrl;

    @Transactional
    public PasswordResetToken issueSetupInvitation(User user, boolean reactivation) {
        invalidateActiveTokens(user);

        LocalDateTime expiresAt = user.getTemporaryPasswordExpiresAt();
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusDays(temporaryPasswordValidDays);
            user.setTemporaryPasswordExpiresAt(expiresAt);
            userRepository.save(user);
        }

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(expiresAt)
                .verificationChannel(ResetChannel.EMAIL)
                .verificationNotes(reactivation ? "ACCOUNT_REACTIVATION_INVITATION" : "ACCOUNT_SETUP_INVITATION")
                .build();

        PasswordResetToken savedToken = passwordResetTokenRepository.save(token);
        queueSetupEmail(user, savedToken, reactivation);
        return savedToken;
    }

    @Transactional
    public void invalidateActiveTokens(User user) {
        passwordResetTokenRepository.findByUserAndUsedAtIsNull(user)
                .forEach(token -> {
                    token.setUsedAt(LocalDateTime.now());
                    passwordResetTokenRepository.save(token);
                });
    }

    @Transactional
    public long expireOverdueTemporaryPasswordUsers() {
        List<User> expiredUsers = userRepository
                .findByDeletedFalseAndPasswordChangeRequiredTrueAndTemporaryPasswordExpiresAtBefore(LocalDateTime.now());

        expiredUsers.stream()
                .filter(user -> !user.isDeleted())
                .forEach(user -> {
                    user.markTemporaryPasswordExpired();
                    userRepository.save(user);
                    invalidateActiveTokens(user);
                    log.info("Soft-deleted user {} after temporary password expired at {}", user.getEmail(), user.getTemporaryPasswordExpiresAt());
                });

        return expiredUsers.size();
    }

    public LocalDateTime calculateTemporaryPasswordExpiry() {
        return LocalDateTime.now().plusDays(temporaryPasswordValidDays);
    }

    private void queueSetupEmail(User user, PasswordResetToken token, boolean reactivation) {
        String resetUrl = UriComponentsBuilder.fromUriString(publicResetUrl)
                .queryParam("token", token.getToken())
                .build()
                .toUriString();

        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();

        log.info(
                "Queued {} email to {}. Subject='{} your PestScout password'. Recipient='{}'. Reset URL={}. ExpiresAt={}",
                reactivation ? "account reactivation" : "account setup",
                user.getEmail(),
                reactivation ? "Reset" : "Set",
                fullName.isBlank() ? user.getEmail() : fullName,
                resetUrl,
                token.getExpiresAt()
        );
    }
}

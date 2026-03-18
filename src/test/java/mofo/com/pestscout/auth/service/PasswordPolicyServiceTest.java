package mofo.com.pestscout.auth.service;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.model.UserPasswordHistory;
import mofo.com.pestscout.auth.repository.UserPasswordHistoryRepository;
import mofo.com.pestscout.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordPolicyService Unit Tests")
class PasswordPolicyServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserPasswordHistoryRepository passwordHistoryRepository;

    @InjectMocks
    private PasswordPolicyService passwordPolicyService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Walker")
                .phoneNumber("1234567890")
                .country("KE")
                .customerNumber("KE00000123")
                .role(Role.MANAGER)
                .isEnabled(true)
                .build();
    }

    @Test
    @DisplayName("Should encode password and assign a 90-day expiry")
    void validateAndApplyPassword_WithValidPassword_SetsPasswordAndExpiry() {
        when(passwordHistoryRepository.findByUserOrderByCreatedAtDescIdDesc(user)).thenReturn(List.of());
        when(passwordEncoder.encode("StrongPassword123!")).thenReturn("encoded-password");

        LocalDateTime before = LocalDateTime.now();
        passwordPolicyService.validateAndApplyPassword(user, "StrongPassword123!");

        assertThat(user.getPassword()).isEqualTo("encoded-password");
        assertThat(user.getPasswordExpiresAt()).isAfter(before.plusDays(89));
    }

    @Test
    @DisplayName("Should reject passwords containing the user's name")
    void validateAndApplyPassword_WhenPasswordContainsName_ThrowsBadRequestException() {
        assertThatThrownBy(() -> passwordPolicyService.validateAndApplyPassword(user, "AliceSecure123!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not contain your first name or last name");

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("Should reject passwords that match the current password")
    void validateAndApplyPassword_WhenPasswordMatchesCurrent_ThrowsBadRequestException() {
        user.setPassword("stored-hash");
        when(passwordEncoder.matches("StrongPassword123!", "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> passwordPolicyService.validateAndApplyPassword(user, "StrongPassword123!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("previous six passwords");
    }

    @Test
    @DisplayName("Should reject passwords that match a recent fingerprint ignoring case and spacing")
    void validateAndApplyPassword_WhenPasswordMatchesRecentFingerprint_ThrowsBadRequestException() {
        user.setPassword(null);
        UserPasswordHistory history = UserPasswordHistory.builder()
                .user(user)
                .passwordHash("older-hash")
                .passwordFingerprint(fingerprint("My Old Pass 123!"))
                .build();

        when(passwordHistoryRepository.findByUserOrderByCreatedAtDescIdDesc(user)).thenReturn(List.of(history));

        assertThatThrownBy(() -> passwordPolicyService.validateAndApplyPassword(user, "my old pass 123!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("previous six passwords");
    }

    @Test
    @DisplayName("Should store password history and prune anything beyond six entries")
    void recordPassword_PrunesEntriesBeyondConfiguredLimit() {
        user.setPassword("encoded-password");
        List<UserPasswordHistory> historyEntries = List.of(
                history("hash-1"), history("hash-2"), history("hash-3"),
                history("hash-4"), history("hash-5"), history("hash-6"), history("hash-7")
        );

        when(passwordHistoryRepository.findByUserOrderByCreatedAtDescIdDesc(user)).thenReturn(historyEntries);

        passwordPolicyService.recordPassword(user, "StrongPassword123!");

        ArgumentCaptor<UserPasswordHistory> captor = ArgumentCaptor.forClass(UserPasswordHistory.class);
        verify(passwordHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(captor.getValue().getPasswordFingerprint()).isNotBlank();
        verify(passwordHistoryRepository).deleteAll(historyEntries.subList(6, historyEntries.size()));
    }

    @Test
    @DisplayName("Should reject expired passwords")
    void assertPasswordIsCurrent_WhenPasswordExpired_ThrowsBadRequestException() {
        user.setPasswordExpiresAt(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> passwordPolicyService.assertPasswordIsCurrent(user))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Password expired");
    }

    private UserPasswordHistory history(String hash) {
        return UserPasswordHistory.builder()
                .id(UUID.randomUUID())
                .user(user)
                .passwordHash(hash)
                .passwordFingerprint(hash)
                .build();
    }

    private String fingerprint(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalized = Normalizer.normalize(password, Normalizer.Form.NFKC)
                    .trim()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("\\s+", "");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

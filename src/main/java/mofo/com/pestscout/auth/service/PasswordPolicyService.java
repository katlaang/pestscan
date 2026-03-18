package mofo.com.pestscout.auth.service;

import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.model.UserPasswordHistory;
import mofo.com.pestscout.auth.repository.UserPasswordHistoryRepository;
import mofo.com.pestscout.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

@Service
public class PasswordPolicyService {

    private static final int MIN_NAME_FRAGMENT_LENGTH = 3;

    private final PasswordEncoder passwordEncoder;
    private final UserPasswordHistoryRepository passwordHistoryRepository;

    @Value("${app.auth.password-valid-days:90}")
    private long passwordValidDays = 90;

    @Value("${app.auth.password-history-count:6}")
    private int passwordHistoryCount = 6;

    @Value("${app.auth.password-expiry-warning-days:15}")
    private long passwordExpiryWarningDays = 15;

    public PasswordPolicyService(
            PasswordEncoder passwordEncoder,
            UserPasswordHistoryRepository passwordHistoryRepository
    ) {
        this.passwordEncoder = passwordEncoder;
        this.passwordHistoryRepository = passwordHistoryRepository;
    }

    public void validateAndApplyPassword(User user, String rawPassword) {
        String candidatePassword = requirePassword(rawPassword);
        ensurePasswordDoesNotContainName(user, candidatePassword);
        ensurePasswordNotRecentlyUsed(user, candidatePassword);

        user.applyPassword(
                passwordEncoder.encode(candidatePassword),
                LocalDateTime.now().plusDays(passwordValidDays)
        );
    }

    public void recordPassword(User user, String rawPassword) {
        if (user == null || user.getId() == null || !StringUtils.hasText(user.getPassword())) {
            throw new IllegalArgumentException("A persisted user with an encoded password is required");
        }

        UserPasswordHistory historyEntry = UserPasswordHistory.builder()
                .user(user)
                .passwordHash(user.getPassword())
                .passwordFingerprint(fingerprint(rawPassword))
                .build();

        passwordHistoryRepository.save(historyEntry);
        pruneHistory(user);
    }

    public void assertPasswordIsCurrent(User user) {
        if (user != null && user.isPasswordExpired()) {
            throw new BadRequestException("Password expired. Reset your password to continue.");
        }
    }

    public Long getPasswordExpiryWarningDaysRemaining(User user) {
        if (user == null || user.getPasswordExpiresAt() == null || user.requiresPasswordChange()) {
            return null;
        }

        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), user.getPasswordExpiresAt().toLocalDate());
        if (daysRemaining < 0 || daysRemaining > passwordExpiryWarningDays) {
            return null;
        }

        return daysRemaining;
    }

    public boolean isPasswordExpiryWarningRequired(User user) {
        return getPasswordExpiryWarningDaysRemaining(user) != null;
    }

    public String getPasswordExpiryWarningMessage(User user) {
        Long daysRemaining = getPasswordExpiryWarningDaysRemaining(user);
        if (daysRemaining == null) {
            return null;
        }

        return "Your password is nearing expiry. Please change your password in the next " + daysRemaining + " days";
    }

    private void ensurePasswordDoesNotContainName(User user, String rawPassword) {
        if (user == null) {
            return;
        }

        String normalizedPassword = normalizeForNameComparison(rawPassword);
        if (normalizedPassword.isEmpty()) {
            return;
        }

        Set<String> nameFragments = extractNameFragments(user);
        boolean containsName = nameFragments.stream().anyMatch(normalizedPassword::contains);
        if (containsName) {
            throw new BadRequestException("Password must not contain your first name or last name");
        }
    }

    private void ensurePasswordNotRecentlyUsed(User user, String rawPassword) {
        if (user == null) {
            return;
        }

        if (StringUtils.hasText(user.getPassword()) && passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadRequestException("Password must not match any of your previous six passwords");
        }

        if (user.getId() == null) {
            return;
        }

        String candidateFingerprint = fingerprint(rawPassword);
        List<UserPasswordHistory> recentPasswords = passwordHistoryRepository.findByUserOrderByCreatedAtDescIdDesc(user).stream()
                .limit(passwordHistoryCount)
                .toList();

        boolean reusedPassword = recentPasswords.stream().anyMatch(history ->
                matchesHistory(rawPassword, candidateFingerprint, history));

        if (reusedPassword) {
            throw new BadRequestException("Password must not match any of your previous six passwords");
        }
    }

    private boolean matchesHistory(String rawPassword, String candidateFingerprint, UserPasswordHistory history) {
        if (StringUtils.hasText(history.getPasswordFingerprint())
                && history.getPasswordFingerprint().equals(candidateFingerprint)) {
            return true;
        }

        return StringUtils.hasText(history.getPasswordHash())
                && passwordEncoder.matches(rawPassword, history.getPasswordHash());
    }

    private void pruneHistory(User user) {
        List<UserPasswordHistory> historyEntries = passwordHistoryRepository.findByUserOrderByCreatedAtDescIdDesc(user);
        if (historyEntries.size() <= passwordHistoryCount) {
            return;
        }

        passwordHistoryRepository.deleteAll(historyEntries.subList(passwordHistoryCount, historyEntries.size()));
    }

    private Set<String> extractNameFragments(User user) {
        Set<String> fragments = new LinkedHashSet<>();

        Stream.of(user.getFirstName(), user.getLastName())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(name -> {
                    addFragment(fragments, name);
                    for (String part : name.split("[^\\p{L}\\p{Nd}]+")) {
                        addFragment(fragments, part);
                    }
                });

        return fragments;
    }

    private void addFragment(Set<String> fragments, String value) {
        String normalized = normalizeForNameComparison(value);
        if (normalized.length() >= MIN_NAME_FRAGMENT_LENGTH) {
            fragments.add(normalized);
        }
    }

    private String requirePassword(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new BadRequestException("Password is required");
        }
        return rawPassword;
    }

    private String fingerprint(String rawPassword) {
        return sha256(normalizeForFingerprint(rawPassword));
    }

    private String normalizeForFingerprint(String value) {
        return normalizeUnicode(value)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private String normalizeForNameComparison(String value) {
        return normalizeUnicode(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{Nd}]+", "");
    }

    private String normalizeUnicode(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required for password policy checks", ex);
        }
    }
}

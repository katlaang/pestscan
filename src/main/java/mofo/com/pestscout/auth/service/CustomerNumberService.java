package mofo.com.pestscout.auth.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.common.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomerNumberService {

    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public String resolveCustomerNumber(String requestedCustomerNumber, String countryCode) {
        if (requestedCustomerNumber != null && !requestedCustomerNumber.isBlank()) {
            String normalized = requestedCustomerNumber.trim().toUpperCase(Locale.ROOT);
            if (!normalized.startsWith(countryCode)) {
                throw new BadRequestException("Customer number must start with the farm country code: " + countryCode);
            }
            if (!normalized.matches(countryCode + "\\d{8}")) {
                throw new BadRequestException("Customer number must follow the pattern " + countryCode + "########");
            }
            return normalized;
        }

        return generateUniqueCustomerNumber(countryCode);
    }

    public String normalizeCountryCode(String country) {
        if (country == null || country.isBlank()) {
            return "ZZ";
        }

        String trimmed = country.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);

        for (String isoCountry : Locale.getISOCountries()) {
            if (upper.equalsIgnoreCase(isoCountry)) {
                return isoCountry.toUpperCase(Locale.ROOT);
            }

            Locale locale = new Locale("", isoCountry);
            if (locale.getDisplayCountry(Locale.ENGLISH).equalsIgnoreCase(trimmed)) {
                return isoCountry.toUpperCase(Locale.ROOT);
            }
        }

        return upper.length() >= 2 ? upper.substring(0, 2) : "ZZ";
    }

    public String generateUniqueCustomerNumber(String countryCode) {
        String prefix = (countryCode == null || countryCode.isBlank())
                ? "ZZ"
                : countryCode.toUpperCase(Locale.ROOT);

        String candidate;
        do {
            String digits = String.format("%08d", secureRandom.nextInt(100_000_000));
            candidate = prefix + digits;
        } while (userRepository.existsByCustomerNumber(candidate));

        return candidate;
    }
}


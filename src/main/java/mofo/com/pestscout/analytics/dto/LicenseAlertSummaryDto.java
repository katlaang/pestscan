package mofo.com.pestscout.analytics.dto;

import java.time.LocalDate;
import java.util.UUID;

public record LicenseAlertSummaryDto(
        UUID farmId,
        String farmName,
        LocalDate licenseExpiryDate,
        long daysUntilExpiry,
        String status
) {
}

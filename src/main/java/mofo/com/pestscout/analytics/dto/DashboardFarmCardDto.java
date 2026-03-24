package mofo.com.pestscout.analytics.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DashboardFarmCardDto(
        UUID farmId,
        String farmTag,
        String farmName,
        LocalDate licenseExpiryDate,
        Long daysUntilLicenseExpiry,
        Boolean accessLocked
) {
}

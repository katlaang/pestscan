package mofo.com.pestscout.farm.dto;

import mofo.com.pestscout.farm.model.SeverityLevel;

/**
 * API-friendly legend entry for the heat map so the UI can render the
 * Green → Dark Red scale with numeric thresholds.
 */
public record SeverityLegendEntry(
        String level,
        int minInclusive,
        int maxInclusive,
        String colorHex
) {
    public static SeverityLegendEntry from(SeverityLevel level) {
        return new SeverityLegendEntry(
                level.name(),
                level.getMinInclusive(),
                level.getMaxInclusive(),
                level.getColorHex()
        );
    }
}

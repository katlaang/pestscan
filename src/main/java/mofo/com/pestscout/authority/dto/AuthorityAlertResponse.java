package mofo.com.pestscout.authority.dto;

import mofo.com.pestscout.authority.model.AuthorityAlertSeverity;
import mofo.com.pestscout.authority.model.AuthorityAlertType;
import mofo.com.pestscout.scouting.model.SpeciesCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuthorityAlertResponse(
        UUID id,
        AuthorityAlertType alertType,
        AuthorityAlertSeverity severity,
        String issuingAuthority,
        String title,
        String messageBody,
        String suggestedMitigation,
        String country,
        String state,
        SpeciesCode linkedSpecies,
        String sourceUrl,
        LocalDate issuedDate,
        LocalDate expiryDate,
        Boolean active,
        Boolean highlighted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

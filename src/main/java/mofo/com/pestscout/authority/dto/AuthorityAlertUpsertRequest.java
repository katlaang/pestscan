package mofo.com.pestscout.authority.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mofo.com.pestscout.authority.model.AuthorityAlertSeverity;
import mofo.com.pestscout.authority.model.AuthorityAlertType;
import mofo.com.pestscout.scouting.model.SpeciesCode;

import java.time.LocalDate;

public record AuthorityAlertUpsertRequest(
        @NotNull AuthorityAlertType alertType,
        @NotNull AuthorityAlertSeverity severity,
        @NotBlank @Size(max = 255) String issuingAuthority,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 4000) String messageBody,
        @Size(max = 2000) String suggestedMitigation,
        @NotBlank @Size(max = 100) String country,
        @Size(max = 100) String state,
        SpeciesCode linkedSpecies,
        @Size(max = 1000) String sourceUrl,
        @NotNull LocalDate issuedDate,
        LocalDate expiryDate,
        @NotNull Boolean active
) {
}

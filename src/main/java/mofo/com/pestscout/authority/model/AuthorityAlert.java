package mofo.com.pestscout.authority.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;
import mofo.com.pestscout.scouting.model.SpeciesCode;

import java.time.LocalDate;

@Entity
@Table(name = "authority_alerts", indexes = {
        @Index(name = "idx_authority_alerts_country", columnList = "country"),
        @Index(name = "idx_authority_alerts_country_state", columnList = "country,state"),
        @Index(name = "idx_authority_alerts_active_severity", columnList = "active,severity")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthorityAlert extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 64)
    private AuthorityAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private AuthorityAlertSeverity severity;

    @Column(name = "issuing_authority", nullable = false, length = 255)
    private String issuingAuthority;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message_body", nullable = false, length = 4000)
    private String messageBody;

    @Column(name = "suggested_mitigation", nullable = false, length = 2000)
    private String suggestedMitigation;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "state", length = 100)
    private String state;

    @Enumerated(EnumType.STRING)
    @Column(name = "linked_species", length = 64)
    private SpeciesCode linkedSpecies;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public boolean isCurrentlyActive(LocalDate today) {
        if (!Boolean.TRUE.equals(active) || isDeleted()) {
            return false;
        }
        if (issuedDate != null && issuedDate.isAfter(today)) {
            return false;
        }
        return expiryDate == null || !expiryDate.isBefore(today);
    }
}

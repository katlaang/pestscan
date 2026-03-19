package mofo.com.pestscout.scouting.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;

import java.util.UUID;

/**
 * Persisted draft observation row for an in-progress scouting session.
 * Drafts survive logout/session timeout and are promoted into final observations on completion.
 */
@Entity
@Table(
        name = "scouting_observation_drafts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_session_draft_cell_species",
                columnNames = {"session_id", "session_target_id", "bay_index", "bench_index", "spot_index", "species_identifier"}
        ),
        indexes = {
                @Index(name = "idx_scouting_observation_drafts_session", columnList = "session_id"),
                @Index(name = "idx_scouting_observation_drafts_custom_species", columnList = "custom_species_id")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ScoutingObservationDraft extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ScoutingSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_target_id", nullable = false)
    private ScoutingSessionTarget sessionTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "species_code", length = 50)
    private SpeciesCode speciesCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_species_id")
    private CustomSpeciesDefinition customSpecies;

    @Column(name = "species_identifier", nullable = false, length = 128)
    private String speciesIdentifier;

    @Column(name = "bay_index", nullable = false)
    private Integer bayIndex;

    @Column(name = "bay_label", length = 255)
    private String bayLabel;

    @Column(name = "bench_index", nullable = false)
    private Integer benchIndex;

    @Column(name = "bench_label", length = 255)
    private String benchLabel;

    @Column(name = "spot_index", nullable = false)
    private Integer spotIndex;

    @Column(name = "count_value", nullable = false)
    private Integer count;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "client_request_id", unique = true)
    private UUID clientRequestId;

    @Transient
    public ObservationCategory getCategory() {
        if (customSpecies != null) {
            return customSpecies.getCategory();
        }
        return speciesCode != null ? speciesCode.getCategory() : null;
    }

    @Transient
    public String getSpeciesDisplayName() {
        if (customSpecies != null) {
            return customSpecies.getName();
        }
        return speciesCode != null ? speciesCode.getDisplayName() : null;
    }

    @Transient
    public String resolveSpeciesIdentifier() {
        if (speciesIdentifier != null && !speciesIdentifier.isBlank()) {
            return speciesIdentifier;
        }
        if (customSpecies != null && customSpecies.getId() != null) {
            return "CUSTOM:" + customSpecies.getId();
        }
        if (speciesCode != null) {
            return "CODE:" + speciesCode.name();
        }
        return null;
    }
}

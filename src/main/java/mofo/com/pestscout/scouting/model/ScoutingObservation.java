package mofo.com.pestscout.scouting.model;

import jakarta.persistence.*;
import lombok.*;
import mofo.com.pestscout.common.model.BaseEntity;

/**
 * One row = one species at one grid cell (bay, bench, spot) in a session.
 */
@Entity
@Table(
        name = "scouting_observations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_session_cell_species",
                columnNames = {"session_id", "session_target_id", "bay_index", "bench_index", "spot_index", "species_code"}
        ),
        indexes = {
                @Index(name = "idx_scouting_observations_session", columnList = "session_id"),
                @Index(name = "idx_scouting_observations_species", columnList = "species_code")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutingObservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ScoutingSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_target_id", nullable = false)
    private ScoutingSessionTarget sessionTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "species_code", nullable = false, length = 50)
    private SpeciesCode speciesCode;

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

    /**
     * Derived category for convenience in code and queries.
     */
    @Transient
    public ObservationCategory getCategory() {
        return speciesCode.getCategory();
    }
}

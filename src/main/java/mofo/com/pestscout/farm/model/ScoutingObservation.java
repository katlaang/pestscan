package mofo.com.pestscout.farm.model;

import jakarta.persistence.*;
import lombok.*;
import mofo.com.pestscout.common.model.BaseEntity;

/**
 * Observation captured during a scouting session for a specific bay, bench and spot check.
 */
@Entity
@Table(name = "scouting_observations",
        indexes = {
                @Index(name = "idx_scouting_observations_session", columnList = "session_id"),
                @Index(name = "idx_scouting_observations_category", columnList = "category")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutingObservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ScoutingSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ObservationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "pest_type", length = 50)
    private PestType pestType;

    @Enumerated(EnumType.STRING)
    @Column(name = "disease_type", length = 50)
    private DiseaseType diseaseType;

    @Column(name = "bay_index")
    private Integer bayIndex;

    @Column(name = "bench_index")
    private Integer benchIndex;

    @Column(name = "spot_index")
    private Integer spotIndex;

    @Column(name = "count_value")
    private Integer count;

    @Column(name = "notes", length = 2000)
    private String notes;
}

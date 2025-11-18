package mofo.com.pestscout.scouting.model;

import jakarta.persistence.*;
import lombok.*;
import mofo.com.pestscout.common.model.BaseEntity;
import mofo.com.pestscout.farm.model.FieldBlock;
import mofo.com.pestscout.farm.model.Greenhouse;

import java.util.ArrayList;
import java.util.List;

/**
 * Section-level selection within a scouting session. Each target links the session
 * to a greenhouse or field block and records which bays/benches were chosen so
 * the UI can present separate grids per structure.
 */
@Entity
@Table(name = "scouting_session_targets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutingSessionTarget extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ScoutingSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "greenhouse_id")
    private Greenhouse greenhouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_block_id")
    private FieldBlock fieldBlock;

    @Builder.Default
    @Column(name = "include_all_bays")
    private Boolean includeAllBays = Boolean.TRUE;

    @Builder.Default
    @Column(name = "include_all_benches")
    private Boolean includeAllBenches = Boolean.TRUE;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "scouting_target_bays", joinColumns = @JoinColumn(name = "target_id"))
    @Column(name = "bay_tag", length = 255)
    private List<String> bayTags = new ArrayList<>();

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "scouting_target_benches", joinColumns = @JoinColumn(name = "target_id"))
    @Column(name = "bench_tag", length = 255)
    private List<String> benchTags = new ArrayList<>();
}

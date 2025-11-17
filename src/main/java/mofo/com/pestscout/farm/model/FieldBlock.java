package mofo.com.pestscout.farm.model;

import jakarta.persistence.*;
import lombok.*;
import mofo.com.pestscout.common.model.BaseEntity;

@Entity
@Table(
        name = "field_blocks",
        indexes = {
                @Index(name = "idx_field_blocks_farm", columnList = "farm_id"),
                @Index(name = "idx_field_blocks_name", columnList = "name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldBlock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @Column(nullable = false, length = 255)
    private String name;   // "North Field A"

    @Column(name = "bay_count")
    private Integer bayCount;   // field rows or strips

    @Column(name = "spot_checks_per_bay")
    private Integer spotChecksPerBay;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    public int resolvedBayCount() {
        return bayCount != null ? bayCount : farm.resolveBayCount();
    }

    public int resolvedSpotChecksPerBay() {
        // reuse farm default for spots per bench if you want
        return spotChecksPerBay != null ? spotChecksPerBay : farm.resolveSpotChecksPerBench();
    }
}

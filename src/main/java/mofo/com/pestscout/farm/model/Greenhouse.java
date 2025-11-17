package mofo.com.pestscout.farm.model;

import jakarta.persistence.*;
import lombok.*;
import mofo.com.pestscout.common.model.BaseEntity;

@Entity
@Table(
        name = "greenhouses",
        indexes = {
                @Index(name = "idx_greenhouses_farm", columnList = "farm_id"),
                @Index(name = "idx_greenhouses_name", columnList = "name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Greenhouse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @Column(nullable = false, length = 255)
    private String name;   // "Tomato House 1"

    @Column(name = "bay_count")
    private Integer bayCount;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "benches_per_bay")
    private Integer benchesPerBay;

    @Column(name = "spot_checks_per_bench")
    private Integer spotChecksPerBench;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    public int resolvedBayCount() {
        return bayCount != null ? bayCount : farm.resolveBayCount();
    }

    public int resolvedBenchesPerBay() {
        return benchesPerBay != null ? benchesPerBay : farm.resolveBenchesPerBay();
    }

    public int resolvedSpotChecksPerBench() {
        return spotChecksPerBench != null ? spotChecksPerBench : farm.resolveSpotChecksPerBench();
    }
}


package mofo.com.pestscout.farm.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

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
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Column(name = "area_hectares", precision = 10, scale = 2)
    private BigDecimal areaHectares;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "greenhouse_bay_tags", joinColumns = @JoinColumn(name = "greenhouse_id"))
    @Column(name = "bay_tag", length = 255)
    private List<String> bayTags = new ArrayList<>();

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "greenhouse_bench_tags", joinColumns = @JoinColumn(name = "greenhouse_id"))
    @Column(name = "bench_tag", length = 255)
    private List<String> benchTags = new ArrayList<>();

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "greenhouse_bays", joinColumns = @JoinColumn(name = "greenhouse_id"))
    @OrderColumn(name = "position_index")
    private List<GreenhouseBayDefinition> bays = new ArrayList<>();

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    public int resolvedBayCount() {
        if (bays != null && !bays.isEmpty()) {
            return bays.size();
        }
        return bayCount != null ? bayCount : farm.resolveBayCount();
    }

    public int resolvedBenchesPerBay() {
        if (bays != null && !bays.isEmpty()) {
            return bays.stream()
                    .map(GreenhouseBayDefinition::getBedCount)
                    .filter(count -> count != null && count > 0)
                    .max(Integer::compareTo)
                    .orElse(0);
        }
        return benchesPerBay != null ? benchesPerBay : farm.resolveBenchesPerBay();
    }

    public int resolvedSpotChecksPerBench() {
        return spotChecksPerBench != null ? spotChecksPerBench : farm.resolveSpotChecksPerBench();
    }

    public List<String> resolvedBayTags() {
        if (bays != null && !bays.isEmpty()) {
            return bays.stream()
                    .map(GreenhouseBayDefinition::getBayTag)
                    .toList();
        }
        return bayTags != null ? List.copyOf(bayTags) : List.of();
    }

    public List<String> resolvedBedTags() {
        if (benchTags != null && !benchTags.isEmpty()) {
            return List.copyOf(benchTags);
        }

        int bedCount = resolvedBenchesPerBay();
        if (bedCount <= 0) {
            return List.of();
        }

        return IntStream.rangeClosed(1, bedCount)
                .mapToObj(index -> "Bed-" + index)
                .toList();
    }
}


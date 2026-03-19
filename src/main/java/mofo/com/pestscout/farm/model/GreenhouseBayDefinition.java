package mofo.com.pestscout.farm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.persistence.StringListJsonConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Embeddable
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class GreenhouseBayDefinition {

    @Column(name = "bay_tag", nullable = false, length = 255)
    private String bayTag;

    @Column(name = "bed_count", nullable = false)
    private Integer bedCount;

    @Builder.Default
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "bed_tags_json", length = 4000)
    private List<String> bedTags = new ArrayList<>();

    public List<String> resolvedBedTags() {
        if (bedTags != null && !bedTags.isEmpty()) {
            return List.copyOf(bedTags);
        }
        if (bedCount == null || bedCount <= 0) {
            return List.of();
        }
        return IntStream.rangeClosed(1, bedCount)
                .mapToObj(index -> "Bed-" + index)
                .toList();
    }
}

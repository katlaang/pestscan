package mofo.com.pestscout.farm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class GreenhouseBayDefinition {

    @Column(name = "bay_tag", nullable = false, length = 255)
    private String bayTag;

    @Column(name = "bed_count", nullable = false)
    private Integer bedCount;
}

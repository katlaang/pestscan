package mofo.com.pestscout.scouting.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;
import mofo.com.pestscout.farm.model.Farm;

/**
 * Stores reusable farm-specific custom pests, diseases, and beneficial insects.
 */
@Entity
@Table(
        name = "custom_species_definitions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_custom_species_farm_category_name",
                columnNames = {"farm_id", "category", "normalized_name"}
        ),
        indexes = {
                @Index(name = "idx_custom_species_farm", columnList = "farm_id"),
                @Index(name = "idx_custom_species_farm_category", columnList = "farm_id, category")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomSpeciesDefinition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private ObservationCategory category;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "code", nullable = false, length = 120)
    private String code;

    @Column(name = "normalized_name", nullable = false, length = 255)
    private String normalizedName;
}

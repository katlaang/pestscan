package mofo.com.pestscout.scouting.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScoutingPhotoAnalysisCandidate {

    @Enumerated(EnumType.STRING)
    @Column(name = "species_code", nullable = false, length = 64)
    private SpeciesCode speciesCode;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "rationale", length = 1000)
    private String rationale;
}

package mofo.com.pestscout.scouting.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "scouting_photo_analyses",
        indexes = {
                @Index(name = "idx_photo_analysis_farm", columnList = "farm_id"),
                @Index(name = "idx_photo_analysis_review_status", columnList = "review_status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_photo_analysis_photo", columnNames = "photo_id")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ScoutingPhotoAnalysis extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "photo_id", nullable = false)
    private ScoutingPhoto photo;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "provider", nullable = false, length = 100)
    private String provider;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    @Column(name = "summary", length = 1000)
    private String summary;

    @Column(name = "review_required", nullable = false)
    private boolean reviewRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "predicted_species_code", length = 64)
    private SpeciesCode predictedSpeciesCode;

    @Column(name = "predicted_confidence", precision = 5, scale = 2)
    private BigDecimal predictedConfidence;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 32)
    private PhotoAnalysisReviewStatus reviewStatus = PhotoAnalysisReviewStatus.PENDING_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "reviewed_species_code", length = 64)
    private SpeciesCode reviewedSpeciesCode;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "reviewer_name", length = 255)
    private String reviewerName;

    @Column(name = "review_notes", length = 2000)
    private String reviewNotes;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "scouting_photo_analysis_candidates",
            joinColumns = @JoinColumn(name = "analysis_id")
    )
    @OrderColumn(name = "candidate_rank")
    private List<ScoutingPhotoAnalysisCandidate> candidates = new ArrayList<>();
}

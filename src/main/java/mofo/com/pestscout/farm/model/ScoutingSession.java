package mofo.com.pestscout.farm.model;

import jakarta.persistence.*;
import lombok.*;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.model.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a scouting session which groups together observations captured by scouts. Sessions are
 * the core unit for analytics, heat map generation and alerting.
 */
@Entity
@Table(name = "scouting_sessions",
        indexes = {
                @Index(name = "idx_scouting_sessions_farm", columnList = "farm_id"),
                @Index(name = "idx_scouting_sessions_date", columnList = "session_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutingSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_id", nullable = false)
    private Farm farm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scout_id")
    private User scout;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "crop_type", length = 255)
    private String cropType;

    @Column(name = "crop_variety", length = 255)
    private String cropVariety;

    @Column(name = "weather", length = 255)
    private String weather;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "confirmation_acknowledged")
    private boolean confirmationAcknowledged;

    @Builder.Default
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScoutingObservation> observations = new ArrayList<>();

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "session_recommendations", joinColumns = @JoinColumn(name = "session_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "recommendation_type", length = 50)
    @Column(name = "recommendation", length = 2000)
    private Map<RecommendationType, String> recommendations = new EnumMap<>(RecommendationType.class);

    public void addObservation(ScoutingObservation observation) {
        observations.add(observation);
        observation.setSession(this);
    }

    public void removeObservation(ScoutingObservation observation) {
        observations.remove(observation);
        observation.setSession(null);
    }
}

package mofo.com.pestscout.scouting.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.model.BaseEntity;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FieldBlock;
import mofo.com.pestscout.farm.model.Greenhouse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a scouting session which groups together observations captured by scouts.
 * Sessions are the unit for reports, heat maps and alerts.
 */
@Entity
@Table(
        name = "scouting_sessions",
        indexes = {
                @Index(name = "idx_scouting_sessions_farm", columnList = "farm_id"),
                @Index(name = "idx_scouting_sessions_date", columnList = "session_date")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "greenhouse_id")
    private Greenhouse greenhouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_block_id")
    private FieldBlock fieldBlock;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "week_number")
    private Integer weekNumber;

    @Column(name = "crop_type", length = 255)
    private String cropType;

    @Column(name = "crop_variety", length = 255)
    private String cropVariety;

    @Column(name = "weather", length = 255)
    private String weather;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "temperature_celsius", precision = 10, scale = 2)
    private BigDecimal temperatureCelsius;

    @Column(name = "relative_humidity_percent", precision = 10, scale = 2)
    private BigDecimal relativeHumidityPercent;

    @Column(name = "observation_time")
    private LocalTime observationTime;

    @Column(name = "weather_notes", length = 2000)
    private String weatherNotes;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "default_photo_source_type", nullable = false, length = 32)
    private PhotoSourceType defaultPhotoSourceType = PhotoSourceType.SCOUT_HANDHELD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "remote_start_requested_at")
    private LocalDateTime remoteStartRequestedAt;

    @Column(name = "remote_start_requested_by_user_id")
    private java.util.UUID remoteStartRequestedByUserId;

    @Column(name = "remote_start_requested_by_name", length = 255)
    private String remoteStartRequestedByName;

    @Column(name = "reopen_comment", length = 2000)
    private String reopenComment;

    /**
     * True when the scout (or manager) has confirmed that all data is correct
     * at the time of completion.
     */
    @Column(name = "confirmation_acknowledged")
    private boolean confirmationAcknowledged;

    @Builder.Default
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScoutingObservation> observations = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScoutingSessionTarget> targets = new ArrayList<>();

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "session_recommendations", joinColumns = @JoinColumn(name = "session_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "recommendation_type", length = 50)
    @Column(name = "recommendation", length = 2000)
    private Map<RecommendationType, String> recommendations = new EnumMap<>(RecommendationType.class);

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "scouting_session_species", joinColumns = @JoinColumn(name = "session_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "species_code", length = 50)
    private List<SpeciesCode> surveySpecies = new ArrayList<>();

    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "scouting_session_custom_species",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "custom_species_id")
    )
    private List<CustomSpeciesDefinition> customSurveySpecies = new ArrayList<>();


    /**
     * A session is editable while it is draft or in progress.
     */
    public boolean isEditable() {
        return status == SessionStatus.DRAFT
                || status == SessionStatus.NEW
                || status == SessionStatus.IN_PROGRESS
                || status == SessionStatus.REOPENED;
    }

    /**
     * Mark the session as started when the scout actually begins work.
     */
    public void markStarted() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        clearRemoteStartRequest();
        status = SessionStatus.IN_PROGRESS;
    }

    /**
     * Mark the session as completed. The caller must pass whether the user
     * checked the "I confirm this data is correct" box.
     */
    public void markSubmitted(boolean acknowledged) {
        this.status = SessionStatus.SUBMITTED;
        this.submittedAt = LocalDateTime.now();
        this.confirmationAcknowledged = acknowledged;
        this.reopenComment = null;
        clearRemoteStartRequest();
    }

    public void markCompleted(boolean acknowledged) {
        this.status = SessionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.confirmationAcknowledged = acknowledged;
        clearRemoteStartRequest();
    }

    public void markReopened(String comment) {
        this.status = SessionStatus.REOPENED;
        this.reopenComment = comment;
        this.confirmationAcknowledged = false;
        this.completedAt = null;
        clearRemoteStartRequest();
    }

    public void markIncomplete() {
        this.status = SessionStatus.INCOMPLETE;
        clearRemoteStartRequest();
    }

    public void requestRemoteStart(java.util.UUID requestedByUserId, String requestedByName) {
        this.remoteStartRequestedAt = LocalDateTime.now();
        this.remoteStartRequestedByUserId = requestedByUserId;
        this.remoteStartRequestedByName = requestedByName;
    }

    public boolean isRemoteStartPending() {
        return remoteStartRequestedAt != null;
    }

    public void clearRemoteStartRequest() {
        this.remoteStartRequestedAt = null;
        this.remoteStartRequestedByUserId = null;
        this.remoteStartRequestedByName = null;
    }


    public void addObservation(ScoutingObservation observation) {
        observations.add(observation);
        observation.setSession(this);
    }

    public void removeObservation(ScoutingObservation observation) {
        observations.remove(observation);
        observation.setSession(null);
        observation.setSessionTarget(null);
    }

    public void addTarget(ScoutingSessionTarget target) {
        targets.add(target);
        target.setSession(this);
    }
}

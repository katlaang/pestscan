package mofo.com.pestscout.farm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mofo.com.pestscout.farm.model.RecommendationType;
import mofo.com.pestscout.farm.model.SessionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutingSessionResponse {

    private UUID id;
    private UUID farmId;
    private String farmName;
    private UUID managerId;
    private UUID scoutId;
    private String managerName;
    private String scoutName;
    private LocalDate sessionDate;
    private String cropType;
    private String cropVariety;
    private String weather;
    private String notes;
    private SessionStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private boolean confirmationAcknowledged;
    private Map<RecommendationType, String> recommendations;
    private List<ObservationResponse> observations;
}

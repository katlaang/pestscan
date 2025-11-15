package mofo.com.pestscout.farm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mofo.com.pestscout.farm.model.RecommendationType;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoutingSessionRequest {

    @NotNull
    private UUID farmId;

    private UUID managerId;

    private UUID scoutId;

    @NotNull
    private LocalDate sessionDate;

    @Size(max = 255)
    private String cropType;

    @Size(max = 255)
    private String cropVariety;

    @Size(max = 255)
    private String weather;

    @Size(max = 2000)
    private String notes;

    @Builder.Default
    @Valid
    private Map<RecommendationType, String> recommendations = new EnumMap<>(RecommendationType.class);
}

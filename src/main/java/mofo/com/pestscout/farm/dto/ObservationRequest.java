package mofo.com.pestscout.farm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mofo.com.pestscout.farm.model.DiseaseType;
import mofo.com.pestscout.farm.model.ObservationCategory;
import mofo.com.pestscout.farm.model.PestType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObservationRequest {

    @NotNull
    private ObservationCategory category;

    private PestType pestType;

    private DiseaseType diseaseType;

    @Min(0)
    private Integer bayIndex;

    @Min(0)
    private Integer benchIndex;

    @Min(0)
    private Integer spotIndex;

    @Min(0)
    private Integer count;

    @Size(max = 2000)
    private String notes;
}

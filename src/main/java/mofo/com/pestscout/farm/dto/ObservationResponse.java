package mofo.com.pestscout.farm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mofo.com.pestscout.farm.model.DiseaseType;
import mofo.com.pestscout.farm.model.ObservationCategory;
import mofo.com.pestscout.farm.model.PestType;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObservationResponse {

    private UUID id;
    private ObservationCategory category;
    private PestType pestType;
    private DiseaseType diseaseType;
    private Integer bayIndex;
    private Integer benchIndex;
    private Integer spotIndex;
    private Integer count;
    private String notes;
}

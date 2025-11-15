package mofo.com.pestscout.farm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mofo.com.pestscout.farm.model.SeverityLevel;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeatmapCellResponse {

    private int bayIndex;
    private int benchIndex;
    private int pestCount;
    private int diseaseCount;
    private int beneficialCount;
    private int totalCount;
    private SeverityLevel severityLevel;
    private String color;
}

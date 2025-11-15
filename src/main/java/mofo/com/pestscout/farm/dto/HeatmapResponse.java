package mofo.com.pestscout.farm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeatmapResponse {

    private UUID farmId;
    private String farmName;
    private int week;
    private int year;
    private int bayCount;
    private int benchesPerBay;
    private List<HeatmapCellResponse> cells;
    private Map<String, String> severityLegend;
}

package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.farm.dto.CoordinateFormatSupport;
import mofo.com.pestscout.farm.dto.FarmLayoutPreviewRequest;
import mofo.com.pestscout.farm.dto.FarmLayoutPreviewResponse;
import mofo.com.pestscout.farm.dto.FarmLayoutStructureDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class FarmLayoutService {

    private static final double GREENHOUSE_WIDTH = 0.00065d;
    private static final double GREENHOUSE_HEIGHT = 0.00045d;
    private static final double HORIZONTAL_GAP = 0.00012d;
    private static final double VERTICAL_GAP = 0.00010d;

    public FarmLayoutPreviewResponse previewLayout(FarmLayoutPreviewRequest request) {
        if (request.greenhouseCount() == null || request.greenhouseCount() < 1) {
            throw new BadRequestException("greenhouseCount must be at least 1.");
        }

        BigDecimal latitude = request.latitude() != null ? CoordinateFormatSupport.validateLatitude(request.latitude()) : null;
        BigDecimal longitude = request.longitude() != null ? CoordinateFormatSupport.validateLongitude(request.longitude()) : null;
        boolean geoReferenced = latitude != null && longitude != null;

        int greenhouseCount = request.greenhouseCount();
        int columns = (int) Math.ceil(Math.sqrt(greenhouseCount));
        int rows = (int) Math.ceil(greenhouseCount / (double) columns);

        double totalWidth = (columns * GREENHOUSE_WIDTH) + ((columns - 1) * HORIZONTAL_GAP);
        double totalHeight = (rows * GREENHOUSE_HEIGHT) + ((rows - 1) * VERTICAL_GAP);
        double originLat = latitude != null ? latitude.doubleValue() : 0.0d;
        double originLng = longitude != null ? longitude.doubleValue() : 0.0d;
        double northEdge = geoReferenced ? originLat + (totalHeight / 2.0d) : 0.0d;
        double westEdge = geoReferenced ? originLng - (totalWidth / 2.0d) : 0.0d;

        List<String> greenhouseNames = resolveGreenhouseNames(request.greenhouseNames(), greenhouseCount);
        List<FarmLayoutStructureDto> greenhouses = new ArrayList<>();

        for (int index = 0; index < greenhouseCount; index++) {
            int row = index / columns;
            int column = index % columns;

            if (geoReferenced) {
                double north = northEdge - (row * (GREENHOUSE_HEIGHT + VERTICAL_GAP));
                double south = north - GREENHOUSE_HEIGHT;
                double west = westEdge + (column * (GREENHOUSE_WIDTH + HORIZONTAL_GAP));
                double east = west + GREENHOUSE_WIDTH;

                greenhouses.add(new FarmLayoutStructureDto(
                        greenhouseNames.get(index),
                        index + 1,
                        (north + south) / 2.0d,
                        (west + east) / 2.0d,
                        List.of(
                                List.of(west, north),
                                List.of(east, north),
                                List.of(east, south),
                                List.of(west, south),
                                List.of(west, north)
                        )
                ));
                continue;
            }

            double top = row * (GREENHOUSE_HEIGHT + VERTICAL_GAP);
            double left = column * (GREENHOUSE_WIDTH + HORIZONTAL_GAP);
            double bottom = top + GREENHOUSE_HEIGHT;
            double right = left + GREENHOUSE_WIDTH;

            greenhouses.add(new FarmLayoutStructureDto(
                    greenhouseNames.get(index),
                    index + 1,
                    null,
                    null,
                    List.of(
                            List.of(left, top),
                            List.of(right, top),
                            List.of(right, bottom),
                            List.of(left, bottom),
                            List.of(left, top)
                    )
            ));
        }

        return new FarmLayoutPreviewResponse(
                greenhouseCount,
                rows,
                columns,
                geoReferenced,
                geoReferenced ? "FARM_ANCHORED_GREENHOUSE_LAYOUT" : "LOCAL_GREENHOUSE_LAYOUT",
                latitude,
                longitude,
                greenhouses
        );
    }

    private List<String> resolveGreenhouseNames(List<String> requestedNames, int greenhouseCount) {
        List<String> normalizedNames = requestedNames == null
                ? new ArrayList<>()
                : requestedNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .limit(greenhouseCount)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        while (normalizedNames.size() < greenhouseCount) {
            normalizedNames.add("Greenhouse " + (normalizedNames.size() + 1));
        }
        return List.copyOf(normalizedNames);
    }
}

package mofo.com.pestscout.region.service;

import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.region.dto.SupportedRegionDto;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class NorthAmericaRegionService {

    private static final Map<String, List<String>> SUPPORTED_REGIONS = buildSupportedRegions();

    public List<SupportedRegionDto> getSupportedRegions() {
        return SUPPORTED_REGIONS.entrySet().stream()
                .map(entry -> new SupportedRegionDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    public String normalizeCountry(String country) {
        String normalized = normalizeKey(country);
        return SUPPORTED_REGIONS.keySet().stream()
                .filter(candidate -> normalizeKey(candidate).equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Country must be one of: " + String.join(", ", SUPPORTED_REGIONS.keySet())));
    }

    public String normalizeState(String country, String state) {
        if (state == null || state.isBlank()) {
            throw new BadRequestException("State or province is required for supported countries.");
        }

        String canonicalCountry = normalizeCountry(country);
        String normalizedState = normalizeKey(state);
        return SUPPORTED_REGIONS.get(canonicalCountry).stream()
                .filter(candidate -> normalizeKey(candidate).equals(normalizedState))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("State or province '" + state + "' is not valid for " + canonicalCountry + "."));
    }

    public String normalizeOptionalState(String country, String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        return normalizeState(country, state);
    }

    public void validateFarmLocation(String country, String state) {
        normalizeCountry(country);
        normalizeState(country, state);
    }

    public boolean matchesRegion(String farmCountry, String farmState, String regionCountry, String regionState) {
        String normalizedFarmCountry = normalizeCountry(farmCountry);
        String normalizedRegionCountry = normalizeCountry(regionCountry);
        if (!normalizedFarmCountry.equals(normalizedRegionCountry)) {
            return false;
        }

        if (regionState == null || regionState.isBlank()) {
            return true;
        }

        String normalizedFarmState = normalizeState(normalizedFarmCountry, farmState);
        String normalizedRegionState = normalizeState(normalizedRegionCountry, regionState);
        return normalizedFarmState.equals(normalizedRegionState);
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ")
                .trim();
    }

    private static Map<String, List<String>> buildSupportedRegions() {
        Map<String, List<String>> regions = new LinkedHashMap<>();
        regions.put("United States", List.of(
                "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut",
                "Delaware", "District of Columbia", "Florida", "Georgia", "Hawaii", "Idaho", "Illinois",
                "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine", "Maryland", "Massachusetts",
                "Michigan", "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska", "Nevada",
                "New Hampshire", "New Jersey", "New Mexico", "New York", "North Carolina", "North Dakota",
                "Ohio", "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina", "South Dakota",
                "Tennessee", "Texas", "Utah", "Vermont", "Virginia", "Washington", "West Virginia",
                "Wisconsin", "Wyoming"
        ));
        regions.put("Canada", List.of(
                "Alberta", "British Columbia", "Manitoba", "New Brunswick", "Newfoundland and Labrador",
                "Northwest Territories", "Nova Scotia", "Nunavut", "Ontario", "Prince Edward Island",
                "Quebec", "Saskatchewan", "Yukon"
        ));
        regions.put("Mexico", List.of(
                "Aguascalientes", "Baja California", "Baja California Sur", "Campeche", "Chiapas",
                "Chihuahua", "Coahuila", "Colima", "Durango", "Guanajuato", "Guerrero", "Hidalgo",
                "Jalisco", "Mexico City", "Michoacan", "Morelos", "Nayarit", "Nuevo Leon", "Oaxaca",
                "Puebla", "Queretaro", "Quintana Roo", "San Luis Potosi", "Sinaloa", "Sonora",
                "State of Mexico", "Tabasco", "Tamaulipas", "Tlaxcala", "Veracruz", "Yucatan", "Zacatecas"
        ));
        return Map.copyOf(regions);
    }
}

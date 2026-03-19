package mofo.com.pestscout.scouting.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import mofo.com.pestscout.scouting.dto.CreateCustomSpeciesRequest;
import mofo.com.pestscout.scouting.dto.CustomSpeciesDto;
import mofo.com.pestscout.scouting.model.CustomSpeciesDefinition;
import mofo.com.pestscout.scouting.model.ObservationCategory;
import mofo.com.pestscout.scouting.repository.CustomSpeciesDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomSpeciesService {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int MAX_CODE_LENGTH = 120;

    private final CustomSpeciesDefinitionRepository customSpeciesDefinitionRepository;
    private final FarmRepository farmRepository;
    private final FarmAccessService farmAccessService;

    @Transactional(readOnly = true)
    public List<CustomSpeciesDto> listFarmCustomSpecies(UUID farmId, ObservationCategory category) {
        Farm farm = loadFarm(farmId);
        farmAccessService.requireViewAccess(farm);

        List<CustomSpeciesDefinition> definitions = category == null
                ? customSpeciesDefinitionRepository.findByFarmIdOrderByCategoryAscNameAsc(farmId)
                : customSpeciesDefinitionRepository.findByFarmIdAndCategoryOrderByNameAsc(farmId, category);

        return definitions.stream()
                .map(this::mapToDto)
                .toList();
    }

    @Transactional
    public List<CustomSpeciesDto> createCustomSpecies(UUID farmId, CreateCustomSpeciesRequest request) {
        Farm farm = loadFarm(farmId);
        farmAccessService.requireViewAccess(farm);

        Map<String, String> normalizedToDisplayName = normalizeRequestedNames(request.names());
        List<CustomSpeciesDto> results = new ArrayList<>();

        for (Map.Entry<String, String> entry : normalizedToDisplayName.entrySet()) {
            String normalizedName = entry.getKey();
            String displayName = entry.getValue();

            CustomSpeciesDefinition definition = customSpeciesDefinitionRepository
                    .findByFarmIdAndCategoryAndNormalizedName(farmId, request.category(), normalizedName)
                    .map(existing -> ensureCode(existing))
                    .orElseGet(() -> customSpeciesDefinitionRepository.save(
                            CustomSpeciesDefinition.builder()
                                    .farm(farm)
                                    .category(request.category())
                                    .name(displayName)
                                    .code(generateUniqueCode(farmId, request.category(), displayName))
                                    .normalizedName(normalizedName)
                                    .build()
                    ));

            results.add(mapToDto(definition));
        }

        return results;
    }

    private Farm loadFarm(UUID farmId) {
        return farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));
    }

    private Map<String, String> normalizeRequestedNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            throw new BadRequestException("Provide at least one custom species name.");
        }

        Map<String, String> normalizedToDisplayName = new LinkedHashMap<>();
        for (String rawName : names) {
            if (rawName == null) {
                continue;
            }

            String trimmed = sanitizeDisplayName(rawName);
            if (trimmed.isBlank()) {
                continue;
            }

            String normalized = normalizeNameKey(trimmed);
            normalizedToDisplayName.putIfAbsent(normalized, trimmed);
        }

        if (normalizedToDisplayName.isEmpty()) {
            throw new BadRequestException("Provide at least one non-empty custom species name.");
        }

        return normalizedToDisplayName;
    }

    private CustomSpeciesDto mapToDto(CustomSpeciesDefinition definition) {
        return new CustomSpeciesDto(
                definition.getId(),
                definition.getCategory(),
                definition.getName(),
                definition.getCode()
        );
    }

    private CustomSpeciesDefinition ensureCode(CustomSpeciesDefinition definition) {
        if (definition.getCode() != null && !definition.getCode().isBlank()) {
            return definition;
        }

        definition.setCode(generateUniqueCode(
                definition.getFarm().getId(),
                definition.getCategory(),
                definition.getName()
        ));
        return customSpeciesDefinitionRepository.save(definition);
    }

    private String sanitizeDisplayName(String rawName) {
        return WHITESPACE.matcher(Objects.requireNonNullElse(rawName, "").trim())
                .replaceAll(" ");
    }

    private String normalizeNameKey(String displayName) {
        String compactWhitespace = NON_ALPHANUMERIC.matcher(displayName.trim())
                .replaceAll(" ")
                .trim();
        return WHITESPACE.matcher(compactWhitespace)
                .replaceAll(" ")
                .toLowerCase(Locale.ROOT);
    }

    private String generateUniqueCode(UUID farmId, ObservationCategory category, String displayName) {
        String baseCode = generateBaseCode(displayName);
        String candidate = baseCode;
        int suffix = 2;

        while (customSpeciesDefinitionRepository.existsByFarmIdAndCategoryAndCode(farmId, category, candidate)) {
            candidate = appendSuffix(baseCode, suffix++);
        }

        return candidate;
    }

    private String generateBaseCode(String displayName) {
        String normalized = NON_ALPHANUMERIC.matcher(displayName.trim())
                .replaceAll("_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_")
                .toUpperCase(Locale.ROOT);

        if (normalized.isBlank()) {
            throw new BadRequestException("Custom species name must contain letters or numbers.");
        }

        return truncate(normalized, MAX_CODE_LENGTH);
    }

    private String appendSuffix(String baseCode, int suffix) {
        String suffixText = "_" + suffix;
        return truncate(baseCode, MAX_CODE_LENGTH - suffixText.length()) + suffixText;
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

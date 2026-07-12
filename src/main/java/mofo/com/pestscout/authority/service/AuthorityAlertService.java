package mofo.com.pestscout.authority.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.authority.dto.AlertCoverageDto;
import mofo.com.pestscout.authority.dto.AuthorityAlertResponse;
import mofo.com.pestscout.authority.dto.AuthorityAlertUpsertRequest;
import mofo.com.pestscout.authority.model.AuthorityAlert;
import mofo.com.pestscout.authority.model.AuthorityAlertSeverity;
import mofo.com.pestscout.authority.model.AuthorityAlertType;
import mofo.com.pestscout.authority.repository.AuthorityAlertRepository;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import mofo.com.pestscout.region.service.NorthAmericaRegionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorityAlertService {

    private static final Map<AuthorityAlertType, String> DEFAULT_MITIGATIONS = buildDefaultMitigations();

    private final AuthorityAlertRepository authorityAlertRepository;
    private final AuthorityAlertAccessService authorityAlertAccessService;
    private final NorthAmericaRegionService northAmericaRegionService;
    private final FarmRepository farmRepository;
    private final FarmAccessService farmAccessService;

    @Transactional
    public AuthorityAlertResponse createAlert(AuthorityAlertUpsertRequest request) {
        authorityAlertAccessService.requireCuratorOrSuperAdmin();
        validateDates(request.issuedDate(), request.expiryDate());

        AuthorityAlert alert = AuthorityAlert.builder().build();
        applyUpsert(alert, request);
        return toResponse(authorityAlertRepository.save(alert), false);
    }

    @Transactional
    public AuthorityAlertResponse updateAlert(UUID alertId, AuthorityAlertUpsertRequest request) {
        authorityAlertAccessService.requireCuratorOrSuperAdmin();
        validateDates(request.issuedDate(), request.expiryDate());

        AuthorityAlert alert = authorityAlertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("AuthorityAlert", "id", alertId));
        applyUpsert(alert, request);
        return toResponse(authorityAlertRepository.save(alert), false);
    }

    @Transactional(readOnly = true)
    public List<AuthorityAlertResponse> getRegionalAlerts(String country, List<String> states) {
        authorityAlertAccessService.requireRegionalAnalystOrSuperAdmin();
        String normalizedCountry = northAmericaRegionService.normalizeCountry(country);
        List<String> normalizedStates = states == null ? List.of() : states.stream()
                .map(state -> northAmericaRegionService.normalizeState(normalizedCountry, state))
                .distinct()
                .toList();

        return authorityAlertRepository.findByDeletedFalseAndActiveTrue().stream()
                .filter(alert -> alert.isCurrentlyActive(LocalDate.now()))
                .filter(alert -> normalizedCountry.equals(alert.getCountry()))
                .filter(alert -> normalizedStates.isEmpty() || alert.getState() == null || normalizedStates.contains(alert.getState()))
                .sorted(alertComparator(false))
                .map(alert -> toResponse(alert, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuthorityAlertResponse> getEmergencyFeed() {
        authorityAlertAccessService.requireRegionalAnalystOrSuperAdmin();
        return authorityAlertRepository.findByDeletedFalseAndActiveTrueAndSeverity(AuthorityAlertSeverity.EMERGENCY).stream()
                .filter(alert -> alert.isCurrentlyActive(LocalDate.now()))
                .sorted(alertComparator(false))
                .map(alert -> toResponse(alert, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuthorityAlertResponse> getFarmAlerts(UUID farmId) {
        Farm farm = farmRepository.findById(farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Farm", "id", farmId));
        farmAccessService.requireViewAccess(farm);

        return authorityAlertRepository.findByDeletedFalseAndActiveTrue().stream()
                .filter(alert -> alert.isCurrentlyActive(LocalDate.now()))
                .filter(alert -> northAmericaRegionService.matchesRegion(
                        farm.getCountry(),
                        farm.getProvince(),
                        alert.getCountry(),
                        alert.getState()
                ))
                .sorted(alertComparator(true))
                .map(alert -> toResponse(alert, isHighlightedForFarm(alert)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertCoverageDto> getCountryCoverage() {
        authorityAlertAccessService.requireRegionalAnalystOrSuperAdmin();

        Map<String, Long> counts = new LinkedHashMap<>();
        northAmericaRegionService.getSupportedRegions().forEach(region -> counts.put(region.country(), 0L));
        authorityAlertRepository.findByDeletedFalseAndActiveTrue().stream()
                .filter(alert -> alert.isCurrentlyActive(LocalDate.now()))
                .forEach(alert -> counts.computeIfPresent(alert.getCountry(), (key, value) -> value + 1));

        return counts.entrySet().stream()
                .map(entry -> new AlertCoverageDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertCoverageDto> getStateCoverage(String country) {
        authorityAlertAccessService.requireRegionalAnalystOrSuperAdmin();
        String normalizedCountry = northAmericaRegionService.normalizeCountry(country);

        Map<String, Long> counts = new LinkedHashMap<>();
        northAmericaRegionService.getSupportedRegions().stream()
                .filter(region -> region.country().equals(normalizedCountry))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unsupported country: " + country))
                .states()
                .forEach(state -> counts.put(state, 0L));

        authorityAlertRepository.findByDeletedFalseAndActiveTrue().stream()
                .filter(alert -> alert.isCurrentlyActive(LocalDate.now()))
                .filter(alert -> normalizedCountry.equals(alert.getCountry()))
                .forEach(alert -> {
                    if (alert.getState() == null) {
                        counts.replaceAll((stateName, count) -> count + 1);
                    } else {
                        counts.computeIfPresent(alert.getState(), (stateName, count) -> count + 1);
                    }
                });

        return counts.entrySet().stream()
                .map(entry -> new AlertCoverageDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void applyUpsert(AuthorityAlert alert, AuthorityAlertUpsertRequest request) {
        String normalizedCountry = northAmericaRegionService.normalizeCountry(request.country());
        String normalizedState = northAmericaRegionService.normalizeOptionalState(normalizedCountry, request.state());

        alert.setAlertType(request.alertType());
        alert.setSeverity(request.severity());
        alert.setIssuingAuthority(request.issuingAuthority().trim());
        alert.setTitle(request.title().trim());
        alert.setMessageBody(request.messageBody().trim());
        alert.setSuggestedMitigation(resolveMitigation(request));
        alert.setCountry(normalizedCountry);
        alert.setState(normalizedState);
        alert.setLinkedSpecies(request.linkedSpecies());
        alert.setSourceUrl(trimToNull(request.sourceUrl()));
        alert.setIssuedDate(request.issuedDate());
        alert.setExpiryDate(request.expiryDate());
        alert.setActive(request.active());
    }

    private String resolveMitigation(AuthorityAlertUpsertRequest request) {
        String trimmed = trimToNull(request.suggestedMitigation());
        if (trimmed != null) {
            return trimmed;
        }
        return DEFAULT_MITIGATIONS.get(request.alertType());
    }

    private AuthorityAlertResponse toResponse(AuthorityAlert alert, boolean highlighted) {
        return new AuthorityAlertResponse(
                alert.getId(),
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getIssuingAuthority(),
                alert.getTitle(),
                alert.getMessageBody(),
                alert.getSuggestedMitigation(),
                alert.getCountry(),
                alert.getState(),
                alert.getLinkedSpecies(),
                alert.getSourceUrl(),
                alert.getIssuedDate(),
                alert.getExpiryDate(),
                alert.getActive(),
                highlighted,
                alert.getCreatedAt(),
                alert.getUpdatedAt()
        );
    }

    private Comparator<AuthorityAlert> alertComparator(boolean prioritizeFarmUrgency) {
        Comparator<AuthorityAlert> comparator = Comparator
                .comparing((AuthorityAlert alert) -> severityRank(alert.getSeverity()))
                .thenComparing(AuthorityAlert::getIssuedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AuthorityAlert::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

        if (!prioritizeFarmUrgency) {
            return comparator;
        }

        return Comparator
                .comparing((AuthorityAlert alert) -> isHighlightedForFarm(alert) ? 0 : 1)
                .thenComparing(comparator);
    }

    private int severityRank(AuthorityAlertSeverity severity) {
        return switch (severity) {
            case EMERGENCY -> 0;
            case WARNING -> 1;
            case WATCH -> 2;
            case ADVISORY -> 3;
        };
    }

    private boolean isHighlightedForFarm(AuthorityAlert alert) {
        return alert.getAlertType() == AuthorityAlertType.OUTBREAK || alert.getSeverity() == AuthorityAlertSeverity.EMERGENCY;
    }

    private void validateDates(LocalDate issuedDate, LocalDate expiryDate) {
        if (expiryDate != null && expiryDate.isBefore(issuedDate)) {
            throw new BadRequestException("Expiry date cannot be before issued date.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static Map<AuthorityAlertType, String> buildDefaultMitigations() {
        Map<AuthorityAlertType, String> messages = new EnumMap<>(AuthorityAlertType.class);
        messages.put(AuthorityAlertType.NEW_DETECTION, "Increase monitoring frequency and confirm any suspicious findings immediately.");
        messages.put(AuthorityAlertType.ADVISORY, "Review the advisory details and align current monitoring and hygiene practices.");
        messages.put(AuthorityAlertType.OUTBREAK, "Inspect affected production areas urgently and isolate suspect material where feasible.");
        messages.put(AuthorityAlertType.QUARANTINE, "Follow quarantine restrictions exactly and pause movements that could spread the threat.");
        messages.put(AuthorityAlertType.ERADICATION_COMPLETE, "Resume normal operations carefully while maintaining verification monitoring.");
        messages.put(AuthorityAlertType.OTHER, "Review the notice and apply local authority guidance to farm operations.");
        return Map.copyOf(messages);
    }
}

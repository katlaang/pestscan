package mofo.com.pestscout.analytics.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.farm.config.LicensePolicyProperties;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.service.LicenseService;
import mofo.com.pestscout.scouting.model.ScoutingObservation;
import mofo.com.pestscout.scouting.model.ScoutingSession;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RawDataPdfExportService {

    private final AnalyticsAccessService analyticsAccessService;
    private final ScoutingSessionRepository sessionRepository;
    private final ScoutingObservationRepository observationRepository;
    private final LicenseService licenseService;
    private final LicensePolicyProperties licensePolicyProperties;
    private final SimplePdfDocumentBuilder pdfDocumentBuilder = new SimplePdfDocumentBuilder();

    @Transactional(readOnly = true)
    public GeneratedPdfDocument exportFarmRawDataPdf(UUID farmId) {
        Farm farm = analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId);

        List<ScoutingSession> sessions = sessionRepository.findByFarmId(farmId).stream()
                .sorted(Comparator.comparing(ScoutingSession::getSessionDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        List<UUID> sessionIds = sessions.stream().map(ScoutingSession::getId).toList();
        List<ScoutingObservation> observations = sessionIds.isEmpty()
                ? List.of()
                : observationRepository.findBySessionIdIn(sessionIds).stream()
                .sorted(Comparator
                        .comparing((ScoutingObservation observation) -> observation.getSession().getSessionDate(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(observation -> observation.getSession().getId())
                        .thenComparing(ScoutingObservation::getBayIndex, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ScoutingObservation::getBenchIndex, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(observation -> observation.getSpeciesCode().name()))
                .toList();

        List<String> lines = new ArrayList<>();
        lines.add("Farm: " + farm.getName());
        lines.add("Farm tag: " + safe(farm.getFarmTag()));
        lines.add("License type: " + safe(farm.getLicenseType()));
        lines.add("License start date: " + safe(farm.getLicenseStartDate()));
        lines.add("License expiry date: " + safe(farm.getLicenseExpiryDate()));
        lines.add("Dashboard access visible until: " + safe(farm.getLicenseGracePeriodEnd()));
        lines.add("Licensed hectares: " + safe(farm.getLicensedAreaHectares()));
        lines.add("Effective licensed hectares: " + licenseService.resolveEffectiveLicensedArea(farm));
        lines.add("Generated on: " + LocalDate.now());
        lines.add("");
        lines.add("Sessions");
        lines.add("-------");

        if (sessions.isEmpty()) {
            lines.add("No scouting sessions were found for this farm.");
        } else {
            for (ScoutingSession session : sessions) {
                lines.add(
                        "Session " + session.getId()
                                + " | date=" + safe(session.getSessionDate())
                                + " | status=" + safe(session.getStatus())
                                + " | location=" + resolveLocation(session)
                                + " | scout=" + resolveScout(session)
                );
                if (session.getNotes() != null && !session.getNotes().isBlank()) {
                    lines.add("  notes: " + session.getNotes().trim());
                }
            }
        }

        lines.add("");
        lines.add("Observations");
        lines.add("------------");

        if (observations.isEmpty()) {
            lines.add("No observations were found for this farm.");
        } else {
            for (ScoutingObservation observation : observations) {
                lines.add(
                        safe(observation.getSession().getSessionDate())
                                + " | " + observation.getSpeciesCode().getDisplayName()
                                + " | category=" + observation.getCategory().name()
                                + " | count=" + safe(observation.getCount())
                                + " | bay=" + safe(observation.getBayLabel(), observation.getBayIndex())
                                + " | bench=" + safe(observation.getBenchLabel(), observation.getBenchIndex())
                                + " | spot=" + safe(observation.getSpotIndex())
                );
                if (observation.getNotes() != null && !observation.getNotes().isBlank()) {
                    lines.add("  notes: " + observation.getNotes().trim());
                }
            }
        }

        String fileName = "farm-raw-data-" + farmId + "-" + LocalDate.now() + ".pdf";
        byte[] content = pdfDocumentBuilder.build("PestScout Raw Farm Data Export", lines);
        return new GeneratedPdfDocument(fileName, content);
    }

    public String buildDownloadUrl(UUID farmId) {
        String baseUrl = licensePolicyProperties.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return "/api/farms/" + farmId + "/license/raw-data-export.pdf";
        }
        return baseUrl.replaceAll("/+$", "") + "/api/farms/" + farmId + "/license/raw-data-export.pdf";
    }

    private String resolveLocation(ScoutingSession session) {
        if (session.getGreenhouse() != null) {
            return "Greenhouse: " + session.getGreenhouse().getName();
        }
        if (session.getFieldBlock() != null) {
            return "Field block: " + session.getFieldBlock().getName();
        }
        return session.getFarm().getName();
    }

    private String resolveScout(ScoutingSession session) {
        if (session.getScout() == null) {
            return "unassigned";
        }
        String firstName = session.getScout().getFirstName();
        String lastName = session.getScout().getLastName();
        String fullName = ((firstName != null ? firstName.trim() : "") + " " + (lastName != null ? lastName.trim() : "")).trim();
        return fullName.isBlank() ? session.getScout().getEmail() : fullName;
    }

    private String safe(Object value) {
        return value == null ? "n/a" : String.valueOf(value);
    }

    private String safe(Object preferred, Object fallback) {
        if (preferred != null) {
            String preferredText = String.valueOf(preferred).trim();
            if (!preferredText.isBlank()) {
                return preferredText;
            }
        }
        return safe(fallback);
    }

    public record GeneratedPdfDocument(String fileName, byte[] content) {
    }
}

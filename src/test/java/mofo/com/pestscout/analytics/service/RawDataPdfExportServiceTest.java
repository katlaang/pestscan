package mofo.com.pestscout.analytics.service;

import mofo.com.pestscout.farm.config.LicensePolicyProperties;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.LicenseType;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.service.LicenseService;
import mofo.com.pestscout.scouting.model.*;
import mofo.com.pestscout.scouting.repository.ScoutingObservationRepository;
import mofo.com.pestscout.scouting.repository.ScoutingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RawDataPdfExportServiceTest {

    private final LicensePolicyProperties licensePolicyProperties = new LicensePolicyProperties();
    @Mock
    private AnalyticsAccessService analyticsAccessService;
    @Mock
    private ScoutingSessionRepository sessionRepository;
    @Mock
    private ScoutingObservationRepository observationRepository;
    @Mock
    private LicenseService licenseService;
    private RawDataPdfExportService rawDataPdfExportService;

    @BeforeEach
    void setUp() {
        rawDataPdfExportService = new RawDataPdfExportService(
                analyticsAccessService,
                sessionRepository,
                observationRepository,
                licenseService,
                licensePolicyProperties
        );
    }

    @Test
    void exportFarmRawDataPdf_returnsPdfBytesAndFileName() {
        UUID farmId = UUID.randomUUID();
        Farm farm = Farm.builder()
                .id(farmId)
                .name("North Farm")
                .farmTag("CA-NORTH")
                .subscriptionStatus(SubscriptionStatus.ACTIVE)
                .licenseType(LicenseType.PAID)
                .licenseStartDate(LocalDate.of(2026, 1, 1))
                .licenseExpiryDate(LocalDate.of(2026, 12, 31))
                .licenseGracePeriodEnd(LocalDate.of(2027, 1, 30))
                .licensedAreaHectares(new BigDecimal("12.00"))
                .build();

        ScoutingSession session = ScoutingSession.builder()
                .farm(farm)
                .sessionDate(LocalDate.of(2026, 3, 16))
                .status(SessionStatus.COMPLETED)
                .build();
        session.setId(UUID.randomUUID());

        ScoutingSessionTarget target = ScoutingSessionTarget.builder()
                .session(session)
                .build();

        ScoutingObservation observation = ScoutingObservation.builder()
                .session(session)
                .sessionTarget(target)
                .speciesCode(SpeciesCode.THRIPS)
                .bayIndex(1)
                .benchIndex(2)
                .spotIndex(1)
                .count(8)
                .notes("Hotspot near intake vents")
                .build();

        when(analyticsAccessService.loadFarmAndEnsureAnalyticsAccess(farmId)).thenReturn(farm);
        when(sessionRepository.findByFarmId(farmId)).thenReturn(List.of(session));
        when(observationRepository.findBySessionIdIn(List.of(session.getId()))).thenReturn(List.of(observation));
        when(licenseService.resolveEffectiveLicensedArea(farm)).thenReturn(new BigDecimal("12.00"));

        RawDataPdfExportService.GeneratedPdfDocument document = rawDataPdfExportService.exportFarmRawDataPdf(farmId);

        assertThat(document.fileName()).startsWith("farm-raw-data-" + farmId);
        assertThat(new String(document.content(), 0, 8, StandardCharsets.US_ASCII)).startsWith("%PDF-1.4");
        assertThat(document.content().length).isGreaterThan(200);
    }
}

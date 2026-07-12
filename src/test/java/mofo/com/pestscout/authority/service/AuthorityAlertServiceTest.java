package mofo.com.pestscout.authority.service;

import mofo.com.pestscout.authority.dto.AuthorityAlertResponse;
import mofo.com.pestscout.authority.dto.AuthorityAlertUpsertRequest;
import mofo.com.pestscout.authority.model.AuthorityAlert;
import mofo.com.pestscout.authority.model.AuthorityAlertSeverity;
import mofo.com.pestscout.authority.model.AuthorityAlertType;
import mofo.com.pestscout.authority.repository.AuthorityAlertRepository;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import mofo.com.pestscout.region.service.NorthAmericaRegionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorityAlertService Unit Tests")
class AuthorityAlertServiceTest {

    @Mock
    private AuthorityAlertRepository authorityAlertRepository;

    @Mock
    private AuthorityAlertAccessService authorityAlertAccessService;

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private FarmAccessService farmAccessService;

    private NorthAmericaRegionService northAmericaRegionService;

    @InjectMocks
    private AuthorityAlertService authorityAlertService;

    @BeforeEach
    void setUp() {
        northAmericaRegionService = new NorthAmericaRegionService();
        authorityAlertService = new AuthorityAlertService(
                authorityAlertRepository,
                authorityAlertAccessService,
                northAmericaRegionService,
                farmRepository,
                farmAccessService
        );
    }

    @Test
    void createAlert_FillsDefaultMitigationAndCanonicalRegion() {
        AuthorityAlertUpsertRequest request = new AuthorityAlertUpsertRequest(
                AuthorityAlertType.OUTBREAK,
                AuthorityAlertSeverity.WARNING,
                "CFIA",
                "Late blight outbreak",
                "Confirmed outbreak in monitored production zone.",
                null,
                "canada",
                "ontario",
                null,
                null,
                LocalDate.now(),
                null,
                true
        );

        when(authorityAlertRepository.save(any(AuthorityAlert.class))).thenAnswer(invocation -> {
            AuthorityAlert alert = invocation.getArgument(0);
            alert.setId(UUID.randomUUID());
            return alert;
        });

        AuthorityAlertResponse response = authorityAlertService.createAlert(request);

        ArgumentCaptor<AuthorityAlert> captor = ArgumentCaptor.forClass(AuthorityAlert.class);
        verify(authorityAlertRepository).save(captor.capture());
        AuthorityAlert saved = captor.getValue();

        assertThat(saved.getCountry()).isEqualTo("Canada");
        assertThat(saved.getState()).isEqualTo("Ontario");
        assertThat(saved.getSuggestedMitigation()).isNotBlank();
        assertThat(response.country()).isEqualTo("Canada");
    }

    @Test
    void getFarmAlerts_ReturnsCountryWideAndStateAlertsWithPriorityOrdering() {
        UUID farmId = UUID.randomUUID();
        Farm farm = Farm.builder()
                .id(farmId)
                .name("Ontario Farm")
                .country("Canada")
                .province("Ontario")
                .licensedAreaHectares(BigDecimal.ONE)
                .build();

        AuthorityAlert outbreak = AuthorityAlert.builder()
                .id(UUID.randomUUID())
                .alertType(AuthorityAlertType.OUTBREAK)
                .severity(AuthorityAlertSeverity.WARNING)
                .issuingAuthority("CFIA")
                .title("Ontario outbreak")
                .messageBody("Outbreak details")
                .suggestedMitigation("Urgent mitigation")
                .country("Canada")
                .state("Ontario")
                .issuedDate(LocalDate.now())
                .active(true)
                .build();

        AuthorityAlert advisory = AuthorityAlert.builder()
                .id(UUID.randomUUID())
                .alertType(AuthorityAlertType.ADVISORY)
                .severity(AuthorityAlertSeverity.ADVISORY)
                .issuingAuthority("CFIA")
                .title("National advisory")
                .messageBody("Advisory details")
                .suggestedMitigation("Review procedures")
                .country("Canada")
                .state(null)
                .issuedDate(LocalDate.now().minusDays(1))
                .active(true)
                .build();

        AuthorityAlert foreignAlert = AuthorityAlert.builder()
                .id(UUID.randomUUID())
                .alertType(AuthorityAlertType.OUTBREAK)
                .severity(AuthorityAlertSeverity.EMERGENCY)
                .issuingAuthority("USDA")
                .title("US outbreak")
                .messageBody("Foreign region")
                .suggestedMitigation("N/A")
                .country("United States")
                .state("California")
                .issuedDate(LocalDate.now())
                .active(true)
                .build();

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(authorityAlertRepository.findByDeletedFalseAndActiveTrue()).thenReturn(List.of(advisory, foreignAlert, outbreak));

        List<AuthorityAlertResponse> alerts = authorityAlertService.getFarmAlerts(farmId);

        assertThat(alerts).hasSize(2);
        assertThat(alerts.getFirst().title()).isEqualTo("Ontario outbreak");
        assertThat(alerts.getFirst().highlighted()).isTrue();
        assertThat(alerts.get(1).title()).isEqualTo("National advisory");
        verify(farmAccessService).requireViewAccess(farm);
    }
}

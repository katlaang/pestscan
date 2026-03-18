package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.dto.CreateGreenhouseRequest;
import mofo.com.pestscout.farm.dto.GreenhouseBayRequest;
import mofo.com.pestscout.farm.dto.GreenhouseDto;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.Greenhouse;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.GreenhouseRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GreenhouseServiceTest {

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private GreenhouseRepository greenhouseRepository;

    @Mock
    private FarmAccessService farmAccessService;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private GreenhouseService greenhouseService;

    @Test
    void createGreenhouse_normalizesTags() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setId(farmId);

        CreateGreenhouseRequest request = new CreateGreenhouseRequest(
                "Main",
                "Desc",
                2,
                3,
                1,
                List.of(" east ", "east"),
                List.of(" bench1 ", "bench1")
        );

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(greenhouseRepository.save(any(Greenhouse.class))).thenAnswer(invocation -> {
            Greenhouse g = invocation.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });

        GreenhouseDto dto = greenhouseService.createGreenhouse(farmId, request);

        assertThat(dto.bayTags()).containsExactly("east", "Bay-2");
        assertThat(dto.benchTags()).containsExactly("bench1", "Bed-2", "Bed-3");
        assertThat(dto.bays()).hasSize(2);
        assertThat(dto.bays().getFirst().bedTags()).containsExactly("bench1", "Bed-2", "Bed-3");
        verify(farmAccessService).requireSuperAdmin();
        verify(cacheService).evictFarmCachesAfterCommit(farmId);
    }

    @Test
    void createGreenhouse_usesFarmDefaultsWhenCountsOmitted() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setId(farmId);
        farm.setDefaultBayCount(8);
        farm.setDefaultBenchesPerBay(6);
        farm.setDefaultSpotChecksPerBench(4);

        CreateGreenhouseRequest request = new CreateGreenhouseRequest(
                "Defaulted",
                "Uses farm defaults",
                null,
                null,
                null,
                List.of(),
                List.of()
        );

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(greenhouseRepository.save(any(Greenhouse.class))).thenAnswer(invocation -> {
            Greenhouse greenhouse = invocation.getArgument(0);
            greenhouse.setId(UUID.randomUUID());
            return greenhouse;
        });

        GreenhouseDto dto = greenhouseService.createGreenhouse(farmId, request);

        assertThat(dto.bayCount()).isEqualTo(8);
        assertThat(dto.benchesPerBay()).isEqualTo(6);
        assertThat(dto.spotChecksPerBench()).isEqualTo(4);
    }

    @Test
    void createGreenhouse_withConfiguredBays_MapsBedsPerBay() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setId(farmId);

        CreateGreenhouseRequest request = new CreateGreenhouseRequest(
                "House A",
                "Custom layout",
                null,
                null,
                2,
                List.of(),
                List.of(),
                new BigDecimal("1.50"),
                List.of(
                        new GreenhouseBayRequest("Bay-A", 2),
                        new GreenhouseBayRequest("Bay-B", 4)
                )
        );

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(greenhouseRepository.save(any(Greenhouse.class))).thenAnswer(invocation -> {
            Greenhouse greenhouse = invocation.getArgument(0);
            greenhouse.setId(UUID.randomUUID());
            return greenhouse;
        });

        GreenhouseDto dto = greenhouseService.createGreenhouse(farmId, request);

        assertThat(dto.bayCount()).isEqualTo(2);
        assertThat(dto.benchesPerBay()).isEqualTo(4);
        assertThat(dto.bays()).hasSize(2);
        assertThat(dto.bays().get(0).bayTag()).isEqualTo("Bay-A");
        assertThat(dto.bays().get(0).bedCount()).isEqualTo(2);
        assertThat(dto.bays().get(1).bayTag()).isEqualTo("Bay-B");
        assertThat(dto.bays().get(1).bedCount()).isEqualTo(4);
    }

    @Test
    void deleteGreenhouse_throwsWhenMissing() {
        UUID greenhouseId = UUID.randomUUID();
        when(greenhouseRepository.findById(greenhouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> greenhouseService.deleteGreenhouse(greenhouseId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.dto.CreateGreenhouseRequest;
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

        assertThat(dto.bayTags()).containsExactly("east");
        assertThat(dto.benchTags()).containsExactly("bench1");
        verify(farmAccessService).requireSuperAdmin();
        verify(cacheService).evictFarmCaches(farmId);
    }

    @Test
    void deleteGreenhouse_throwsWhenMissing() {
        UUID greenhouseId = UUID.randomUUID();
        when(greenhouseRepository.findById(greenhouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> greenhouseService.deleteGreenhouse(greenhouseId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

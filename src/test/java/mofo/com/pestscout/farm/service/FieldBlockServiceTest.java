package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.common.service.CacheService;
import mofo.com.pestscout.farm.dto.CreateFieldBlockRequest;
import mofo.com.pestscout.farm.dto.FieldBlockDto;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.FieldBlock;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.repository.FieldBlockRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldBlockServiceTest {

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private FieldBlockRepository fieldBlockRepository;

    @Mock
    private FarmAccessService farmAccessService;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private FieldBlockService fieldBlockService;

    @Test
    void createFieldBlock_savesNormalizedTags() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setId(farmId);

        CreateFieldBlockRequest request = new CreateFieldBlockRequest("Block A", 2, 1, Arrays.asList(" north ", "north", null), true);

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(fieldBlockRepository.save(any(FieldBlock.class))).thenAnswer(invocation -> {
            FieldBlock block = invocation.getArgument(0);
            block.setId(UUID.randomUUID());
            return block;
        });

        FieldBlockDto dto = fieldBlockService.createFieldBlock(farmId, request);

        assertThat(dto.name()).isEqualTo("Block A");
        assertThat(dto.bayTags()).containsExactly("north");
        verify(cacheService).evictFarmCachesAfterCommit(farmId);
        verify(farmAccessService).requireSuperAdmin();
    }

    @Test
    void deleteFieldBlock_throwsWhenMissing() {
        UUID blockId = UUID.randomUUID();
        when(fieldBlockRepository.findById(blockId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fieldBlockService.deleteFieldBlock(blockId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createFieldBlock_usesFarmDefaultsWhenCountsOmitted() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setId(farmId);
        farm.setDefaultBayCount(12);
        farm.setDefaultSpotChecksPerBench(5);

        CreateFieldBlockRequest request = new CreateFieldBlockRequest("Field A", null, null, List.of(), true);

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(fieldBlockRepository.save(any(FieldBlock.class))).thenAnswer(invocation -> {
            FieldBlock block = invocation.getArgument(0);
            block.setId(UUID.randomUUID());
            return block;
        });

        FieldBlockDto dto = fieldBlockService.createFieldBlock(farmId, request);

        assertThat(dto.bayCount()).isEqualTo(12);
        assertThat(dto.spotChecksPerBay()).isEqualTo(5);
    }

    @Test
    void createFieldBlock_persistsCropType() {
        UUID farmId = UUID.randomUUID();
        Farm farm = new Farm();
        farm.setId(farmId);

        CreateFieldBlockRequest request = new CreateFieldBlockRequest(
                "Field Tomatoes",
                4,
                2,
                List.of("Row-1"),
                true,
                new BigDecimal("3.25"),
                "Tomato"
        );

        when(farmRepository.findById(farmId)).thenReturn(Optional.of(farm));
        when(fieldBlockRepository.save(any(FieldBlock.class))).thenAnswer(invocation -> {
            FieldBlock block = invocation.getArgument(0);
            block.setId(UUID.randomUUID());
            return block;
        });

        FieldBlockDto dto = fieldBlockService.createFieldBlock(farmId, request);

        assertThat(dto.cropType()).isEqualTo("Tomato");
        assertThat(dto.areaHectares()).isEqualByComparingTo("3.25");
    }
}

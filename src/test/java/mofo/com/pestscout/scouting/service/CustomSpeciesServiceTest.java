package mofo.com.pestscout.scouting.service;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.FarmAccessService;
import mofo.com.pestscout.scouting.dto.CreateCustomSpeciesRequest;
import mofo.com.pestscout.scouting.dto.CustomSpeciesDto;
import mofo.com.pestscout.scouting.model.CustomSpeciesDefinition;
import mofo.com.pestscout.scouting.model.ObservationCategory;
import mofo.com.pestscout.scouting.repository.CustomSpeciesDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("CustomSpeciesService Unit Tests")
class CustomSpeciesServiceTest {

    @Mock
    private CustomSpeciesDefinitionRepository customSpeciesDefinitionRepository;

    @Mock
    private FarmRepository farmRepository;

    @Mock
    private FarmAccessService farmAccessService;

    @InjectMocks
    private CustomSpeciesService customSpeciesService;

    private Farm farm;
    private CustomSpeciesDefinition savedSpecies;

    @BeforeEach
    void setUp() {
        User owner = User.builder()
                .id(UUID.randomUUID())
                .role(Role.MANAGER)
                .email("owner@example.com")
                .build();

        farm = Farm.builder()
                .id(UUID.randomUUID())
                .name("Test Farm")
                .owner(owner)
                .build();

        savedSpecies = CustomSpeciesDefinition.builder()
                .id(UUID.randomUUID())
                .farm(farm)
                .category(ObservationCategory.PEST)
                .name("Leaf miner")
                .code("LEAF_MINER")
                .normalizedName("leaf miner")
                .build();
    }

    @Test
    @DisplayName("Should create and reuse normalized custom species names")
    void createCustomSpecies_WithDuplicates_ReusesExistingRecords() {
        CreateCustomSpeciesRequest request = new CreateCustomSpeciesRequest(
                ObservationCategory.PEST,
                List.of("Leaf miner", "  leaf miner  ", "Aphid")
        );

        when(farmRepository.findById(farm.getId())).thenReturn(Optional.of(farm));
        when(customSpeciesDefinitionRepository.findByFarmIdAndCategoryAndNormalizedName(farm.getId(), ObservationCategory.PEST, "leaf miner"))
                .thenReturn(Optional.of(savedSpecies));
        when(customSpeciesDefinitionRepository.findByFarmIdAndCategoryAndNormalizedName(farm.getId(), ObservationCategory.PEST, "aphid"))
                .thenReturn(Optional.empty());
        when(customSpeciesDefinitionRepository.existsByFarmIdAndCategoryAndCode(farm.getId(), ObservationCategory.PEST, "APHID"))
                .thenReturn(false);
        when(customSpeciesDefinitionRepository.save(any(CustomSpeciesDefinition.class)))
                .thenAnswer(invocation -> {
                    CustomSpeciesDefinition definition = invocation.getArgument(0);
                    definition.setId(UUID.randomUUID());
                    return definition;
                });

        List<CustomSpeciesDto> result = customSpeciesService.createCustomSpecies(farm.getId(), request);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CustomSpeciesDto::name).containsExactly("Leaf miner", "Aphid");
        assertThat(result).extracting(CustomSpeciesDto::code).containsExactly("LEAF_MINER", "APHID");
        verify(farmAccessService).requireViewAccess(farm);
    }

    @Test
    @DisplayName("Should generate underscore codes and suffix collisions")
    void createCustomSpecies_WithSpacedNames_GeneratesCodes() {
        CreateCustomSpeciesRequest request = new CreateCustomSpeciesRequest(
                ObservationCategory.DISEASE,
                List.of("Powdery mildew", "Powdery-mildew plus")
        );

        when(farmRepository.findById(farm.getId())).thenReturn(Optional.of(farm));
        when(customSpeciesDefinitionRepository.findByFarmIdAndCategoryAndNormalizedName(farm.getId(), ObservationCategory.DISEASE, "powdery mildew"))
                .thenReturn(Optional.empty());
        when(customSpeciesDefinitionRepository.findByFarmIdAndCategoryAndNormalizedName(farm.getId(), ObservationCategory.DISEASE, "powdery mildew plus"))
                .thenReturn(Optional.empty());
        when(customSpeciesDefinitionRepository.existsByFarmIdAndCategoryAndCode(farm.getId(), ObservationCategory.DISEASE, "POWDERY_MILDEW"))
                .thenReturn(false);
        when(customSpeciesDefinitionRepository.existsByFarmIdAndCategoryAndCode(farm.getId(), ObservationCategory.DISEASE, "POWDERY_MILDEW_PLUS"))
                .thenReturn(false);
        when(customSpeciesDefinitionRepository.save(any(CustomSpeciesDefinition.class)))
                .thenAnswer(invocation -> {
                    CustomSpeciesDefinition definition = invocation.getArgument(0);
                    definition.setId(UUID.randomUUID());
                    return definition;
                });

        List<CustomSpeciesDto> result = customSpeciesService.createCustomSpecies(farm.getId(), request);

        assertThat(result).extracting(CustomSpeciesDto::code)
                .containsExactly("POWDERY_MILDEW", "POWDERY_MILDEW_PLUS");
    }

    @Test
    @DisplayName("Should reject empty custom species names")
    void createCustomSpecies_WithBlankNames_ThrowsBadRequest() {
        CreateCustomSpeciesRequest request = new CreateCustomSpeciesRequest(
                ObservationCategory.DISEASE,
                List.of("   ", "")
        );

        when(farmRepository.findById(farm.getId())).thenReturn(Optional.of(farm));

        assertThatThrownBy(() -> customSpeciesService.createCustomSpecies(farm.getId(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Provide at least one non-empty");
    }
}

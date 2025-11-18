package mofo.com.pestscout.farm.service;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.auth.repository.UserRepository;
import mofo.com.pestscout.common.exception.ConflictException;
import mofo.com.pestscout.common.exception.ResourceNotFoundException;
import mofo.com.pestscout.farm.dto.CreateFarmRequest;
import mofo.com.pestscout.farm.dto.FarmResponse;
import mofo.com.pestscout.farm.dto.UpdateFarmRequest;
import mofo.com.pestscout.farm.model.Farm;
import mofo.com.pestscout.farm.model.SubscriptionStatus;
import mofo.com.pestscout.farm.model.SubscriptionTier;
import mofo.com.pestscout.farm.repository.FarmRepository;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.farm.security.FarmAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FarmServiceTest {

    @Mock
    private FarmRepository farmRepository;
    @Mock
    private FarmAccessService farmAccessService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private UserRepository userRepository;

    private FarmService farmService;

    @BeforeEach
    void setUp() {
        farmService = new FarmService(farmRepository, farmAccessService, currentUserService, userRepository);
    }

    @Test
    void createFarmBuildsEntityAndPersists() {
        UUID ownerId = UUID.randomUUID();
        User owner = new User();
        owner.setId(ownerId);

        CreateFarmRequest request = new CreateFarmRequest(
                "Test Farm",
                "description",
                "ext",
                "addr",
                "city",
                "prov",
                "postal",
                "country",
                ownerId,
                null,
                "contact",
                "contact@example.com",
                "123",
                SubscriptionStatus.ACTIVE,
                SubscriptionTier.PREMIUM,
                "billing@example.com",
                BigDecimal.ONE,
                10,
                BigDecimal.ZERO,
                null,
                1,
                2,
                3,
                null,
                null,
                null,
                null,
                null
        );

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(farmRepository.save(any(Farm.class))).thenAnswer(invocation -> {
            Farm farm = invocation.getArgument(0);
            farm.setId(UUID.randomUUID());
            return farm;
        });
        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SUPER_ADMIN);

        FarmResponse response = farmService.createFarm(request);

        ArgumentCaptor<Farm> savedCaptor = ArgumentCaptor.forClass(Farm.class);
        verify(farmRepository).save(savedCaptor.capture());
        Farm savedFarm = savedCaptor.getValue();

        assertThat(savedFarm.getOwner()).isEqualTo(owner);
        assertThat(savedFarm.getName()).isEqualTo("Test Farm");
        assertThat(response.name()).isEqualTo("Test Farm");
    }

    @Test
    void createFarmRejectsDuplicateNames() {
        CreateFarmRequest request = new CreateFarmRequest(
                "Duplicate",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                SubscriptionStatus.ACTIVE,
                SubscriptionTier.BASIC,
                null,
                BigDecimal.ONE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(farmRepository.findByNameIgnoreCase("Duplicate")).thenReturn(Optional.of(new Farm()));

        assertThatThrownBy(() -> farmService.createFarm(request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void listFarmsSortsByNameForCurrentRole() {
        UUID farmAId = UUID.randomUUID();
        UUID farmBId = UUID.randomUUID();
        Farm farmB = Farm.builder().name("Zeta").build();
        farmB.setId(farmBId);
        Farm farmA = Farm.builder().name("Alpha").build();
        farmA.setId(farmAId);

        when(farmAccessService.getCurrentUserRole()).thenReturn(Role.SUPER_ADMIN);
        when(farmRepository.findAll()).thenReturn(List.of(farmB, farmA));

        List<FarmResponse> responses = farmService.listFarms();

        assertThat(responses).extracting(FarmResponse::name)
                .containsExactly("Alpha", "Zeta");
    }

    @Test
    void getFarmThrowsWhenMissing() {
        UUID farmId = UUID.randomUUID();
        when(farmRepository.findById(farmId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> farmService.getFarm(farmId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

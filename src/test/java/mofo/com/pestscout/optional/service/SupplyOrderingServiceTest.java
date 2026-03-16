package mofo.com.pestscout.optional.service;

import mofo.com.pestscout.auth.model.Role;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.optional.dto.OptionalCapabilityDtos.TreatmentRecommendationItem;
import mofo.com.pestscout.optional.dto.SupplyOrderingDtos.CreateSupplyOrderFromDraftRequest;
import mofo.com.pestscout.optional.dto.SupplyOrderingDtos.SupplyOrderDraftResponse;
import mofo.com.pestscout.optional.dto.SupplyOrderingDtos.SupplyOrderResponse;
import mofo.com.pestscout.optional.model.SupplyOrderRequest;
import mofo.com.pestscout.optional.repository.SupplyOrderRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplyOrderingServiceTest {

    @Mock
    private OptionalCapabilityAccessService accessService;

    @Mock
    private mofo.com.pestscout.common.feature.FeatureAccessService featureAccessService;

    @Mock
    private TreatmentRecommendationEngine treatmentRecommendationEngine;

    @Mock
    private SupplyOrderRequestRepository supplyOrderRequestRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private SupplyOrderingService supplyOrderingService;

    @Test
    void buildDraft_aggregatesRecommendationsIntoOrderLines() {
        UUID farmId = UUID.randomUUID();

        when(treatmentRecommendationEngine.generateForFarm(farmId)).thenReturn(List.of(
                recommendation("GH-1", "THRIPS", "BIO-THRIPS-PRED", "Predatory mite sachets", "pack", "2.00", "10.00"),
                recommendation("GH-2", "THRIPS", "BIO-THRIPS-PRED", "Predatory mite sachets", "pack", "3.00", "10.00"),
                recommendation("GH-1", "POWDERY_MILDEW", "FUNGI-PROTECT", "Protective fungicide pack", "pack", "1.00", "20.00")
        ));

        SupplyOrderDraftResponse draft = supplyOrderingService.buildDraft(farmId);

        assertThat(draft.items()).hasSize(2);
        assertThat(draft.estimatedTotal()).isEqualByComparingTo("70.00");
        assertThat(draft.vendorHint()).isEqualTo("Preferred crop protection supplier");
        assertThat(draft.items().getFirst().sku()).isEqualTo("BIO-THRIPS-PRED");
        assertThat(draft.items().getFirst().quantity()).isEqualByComparingTo("5.00");
    }

    @Test
    void submitOrderFromDraft_persistsSubmittedOrder() {
        UUID farmId = UUID.randomUUID();
        User currentUser = User.builder()
                .email("manager@example.com")
                .password("secret")
                .phoneNumber("123")
                .customerNumber("C-100")
                .role(Role.MANAGER)
                .firstName("Maya")
                .lastName("Lee")
                .build();
        currentUser.setId(UUID.randomUUID());

        when(treatmentRecommendationEngine.generateForFarm(farmId)).thenReturn(List.of(
                recommendation("GH-1", "THRIPS", "BIO-THRIPS-PRED", "Predatory mite sachets", "pack", "2.00", "10.00")
        ));
        when(currentUserService.getCurrentUser()).thenReturn(currentUser);
        when(supplyOrderRequestRepository.save(any(SupplyOrderRequest.class))).thenAnswer(invocation -> {
            SupplyOrderRequest orderRequest = invocation.getArgument(0);
            orderRequest.setId(UUID.randomUUID());
            orderRequest.setCreatedAt(LocalDateTime.of(2026, 3, 16, 10, 0));
            return orderRequest;
        });

        SupplyOrderResponse response = supplyOrderingService.submitOrderFromDraft(
                new CreateSupplyOrderFromDraftRequest(farmId, null, "Rush order")
        );

        assertThat(response.status()).isEqualTo("SUBMITTED");
        assertThat(response.vendorName()).isEqualTo("Preferred biological controls supplier");
        assertThat(response.items()).hasSize(1);
        assertThat(response.estimatedTotal()).isEqualByComparingTo("20.00");

        ArgumentCaptor<SupplyOrderRequest> orderCaptor = ArgumentCaptor.forClass(SupplyOrderRequest.class);
        verify(supplyOrderRequestRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getRequestedByName()).isEqualTo("Maya Lee");
        assertThat(orderCaptor.getValue().getItems()).hasSize(1);
    }

    private TreatmentRecommendationItem recommendation(
            String sectionName,
            String speciesCode,
            String sku,
            String itemName,
            String unitOfMeasure,
            String quantity,
            String unitPrice
    ) {
        return new TreatmentRecommendationItem(
                speciesCode,
                speciesCode,
                "PEST",
                sectionName,
                10,
                "HIGH",
                "HIGH",
                "BIOLOGICAL_CONTROL",
                "Apply treatment",
                "Recommendation for " + sectionName,
                sku,
                itemName,
                new BigDecimal(quantity),
                unitOfMeasure,
                new BigDecimal(unitPrice)
        );
    }
}

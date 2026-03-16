package mofo.com.pestscout.optional.service;

import lombok.RequiredArgsConstructor;
import mofo.com.pestscout.auth.model.User;
import mofo.com.pestscout.common.exception.BadRequestException;
import mofo.com.pestscout.common.feature.FeatureAccessService;
import mofo.com.pestscout.common.feature.FeatureKey;
import mofo.com.pestscout.farm.security.CurrentUserService;
import mofo.com.pestscout.optional.dto.OptionalCapabilityDtos.TreatmentRecommendationItem;
import mofo.com.pestscout.optional.dto.SupplyOrderingDtos.CreateSupplyOrderFromDraftRequest;
import mofo.com.pestscout.optional.dto.SupplyOrderingDtos.SupplyOrderDraftResponse;
import mofo.com.pestscout.optional.dto.SupplyOrderingDtos.SupplyOrderLineDto;
import mofo.com.pestscout.optional.dto.SupplyOrderingDtos.SupplyOrderResponse;
import mofo.com.pestscout.optional.model.SupplyOrderItem;
import mofo.com.pestscout.optional.model.SupplyOrderRequest;
import mofo.com.pestscout.optional.model.SupplyOrderStatus;
import mofo.com.pestscout.optional.repository.SupplyOrderRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SupplyOrderingService {

    private final OptionalCapabilityAccessService accessService;
    private final FeatureAccessService featureAccessService;
    private final TreatmentRecommendationEngine treatmentRecommendationEngine;
    private final SupplyOrderRequestRepository supplyOrderRequestRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public SupplyOrderDraftResponse buildDraft(UUID farmId) {
        accessService.loadFarmAndEnsureManager(farmId);
        featureAccessService.assertEnabled(FeatureKey.SUPPLY_ORDERING, farmId);
        return buildDraftInternal(farmId);
    }

    @Transactional
    public SupplyOrderResponse submitOrderFromDraft(CreateSupplyOrderFromDraftRequest request) {
        accessService.loadFarmAndEnsureManager(request.farmId());
        featureAccessService.assertEnabled(FeatureKey.SUPPLY_ORDERING, request.farmId());

        SupplyOrderDraftResponse draft = buildDraftInternal(request.farmId());
        if (draft.items().isEmpty()) {
            throw new BadRequestException("No recommended supply items are available to order for this farm.");
        }

        User currentUser = currentUserService.getCurrentUser();
        LocalDateTime submittedAt = LocalDateTime.now();

        SupplyOrderRequest orderRequest = SupplyOrderRequest.builder()
                .farmId(request.farmId())
                .requestedByUserId(currentUser.getId())
                .requestedByName(resolveDisplayName(currentUser))
                .vendorName(hasText(request.vendorName()) ? request.vendorName().trim() : draft.vendorHint())
                .status(SupplyOrderStatus.SUBMITTED)
                .currencyCode(draft.currencyCode())
                .estimatedTotal(draft.estimatedTotal())
                .notes(hasText(request.notes()) ? request.notes().trim() : null)
                .submittedAt(submittedAt)
                .build();

        for (SupplyOrderLineDto item : draft.items()) {
            orderRequest.addItem(SupplyOrderItem.builder()
                    .sku(item.sku())
                    .itemName(item.itemName())
                    .unitOfMeasure(item.unitOfMeasure())
                    .quantity(item.quantity())
                    .unitPrice(item.unitPrice())
                    .lineTotal(item.lineTotal())
                    .rationale(item.rationale())
                    .sourceSpeciesCode(item.sourceSpeciesCode())
                    .build());
        }

        SupplyOrderRequest saved = supplyOrderRequestRepository.save(orderRequest);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SupplyOrderResponse> listOrders(UUID farmId) {
        accessService.loadFarmAndEnsureManager(farmId);
        featureAccessService.assertEnabled(FeatureKey.SUPPLY_ORDERING, farmId);

        return supplyOrderRequestRepository.findByFarmIdOrderByCreatedAtDesc(farmId).stream()
                .map(this::toResponse)
                .toList();
    }

    private SupplyOrderDraftResponse buildDraftInternal(UUID farmId) {
        List<TreatmentRecommendationItem> recommendations = treatmentRecommendationEngine.generateForFarm(farmId);
        Map<String, DraftAccumulator> accumulators = new LinkedHashMap<>();

        for (TreatmentRecommendationItem recommendation : recommendations) {
            if (!hasText(recommendation.skuHint())
                    || !hasText(recommendation.supplyItemName())
                    || recommendation.suggestedOrderQuantity() == null
                    || recommendation.estimatedUnitPrice() == null) {
                continue;
            }

            DraftAccumulator accumulator = accumulators.get(recommendation.skuHint());
            if (accumulator == null) {
                accumulators.put(recommendation.skuHint(), new DraftAccumulator(recommendation));
            } else {
                accumulator.add(recommendation);
            }
        }

        List<SupplyOrderLineDto> items = accumulators.values().stream()
                .map(DraftAccumulator::toLineItem)
                .sorted(Comparator.comparing(SupplyOrderLineDto::itemName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        BigDecimal estimatedTotal = items.stream()
                .map(SupplyOrderLineDto::lineTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        List<String> advisoryNotes = new ArrayList<>();
        if (items.isEmpty()) {
            advisoryNotes.add("No recommended supply items were generated from recent scouting observations.");
        } else {
            advisoryNotes.add("Draft quantities are aggregated from automated treatment recommendations.");
            advisoryNotes.add("Review vendor pricing and inventory before submission.");
        }

        return new SupplyOrderDraftResponse(
                farmId,
                chooseVendorHint(items),
                "USD",
                estimatedTotal,
                items,
                advisoryNotes
        );
    }

    private SupplyOrderResponse toResponse(SupplyOrderRequest orderRequest) {
        List<SupplyOrderLineDto> items = orderRequest.getItems().stream()
                .map(item -> new SupplyOrderLineDto(
                        item.getSku(),
                        item.getItemName(),
                        item.getUnitOfMeasure(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal(),
                        item.getRationale(),
                        item.getSourceSpeciesCode()
                ))
                .sorted(Comparator.comparing(SupplyOrderLineDto::itemName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new SupplyOrderResponse(
                orderRequest.getId(),
                orderRequest.getFarmId(),
                orderRequest.getVendorName(),
                orderRequest.getStatus().name(),
                orderRequest.getCurrencyCode(),
                orderRequest.getEstimatedTotal(),
                orderRequest.getNotes(),
                orderRequest.getCreatedAt(),
                orderRequest.getSubmittedAt(),
                items
        );
    }

    private String chooseVendorHint(List<SupplyOrderLineDto> items) {
        boolean biologicalOnly = items.stream().allMatch(item -> item.sku().startsWith("BIO-"));
        if (biologicalOnly && !items.isEmpty()) {
            return "Preferred biological controls supplier";
        }

        boolean diseaseInputs = items.stream().anyMatch(item -> item.sku().startsWith("FUNGI-"));
        if (diseaseInputs) {
            return "Preferred crop protection supplier";
        }

        return "Preferred agricultural inputs supplier";
    }

    private String resolveDisplayName(User user) {
        if (hasText(user.getFirstName()) || hasText(user.getLastName())) {
            return ((user.getFirstName() != null ? user.getFirstName().trim() : "")
                    + " "
                    + (user.getLastName() != null ? user.getLastName().trim() : "")).trim();
        }
        return user.getEmail();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class DraftAccumulator {

        private final String sku;
        private final String itemName;
        private final String unitOfMeasure;
        private final BigDecimal unitPrice;
        private final List<String> rationales = new ArrayList<>();
        private final List<String> speciesCodes = new ArrayList<>();
        private BigDecimal quantity;

        private DraftAccumulator(TreatmentRecommendationItem seed) {
            this.sku = seed.skuHint();
            this.itemName = seed.supplyItemName();
            this.unitOfMeasure = seed.unitOfMeasure();
            this.unitPrice = seed.estimatedUnitPrice();
            this.quantity = BigDecimal.ZERO;
            add(seed);
        }

        private void add(TreatmentRecommendationItem recommendation) {
            quantity = quantity.add(recommendation.suggestedOrderQuantity());
            rationales.add(recommendation.sectionName() + ": " + recommendation.rationale());
            speciesCodes.add(recommendation.speciesCode());
        }

        private SupplyOrderLineDto toLineItem() {
            BigDecimal normalizedQuantity = quantity.setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = normalizedQuantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            return new SupplyOrderLineDto(
                    sku,
                    itemName,
                    unitOfMeasure,
                    normalizedQuantity,
                    unitPrice,
                    lineTotal,
                    rationales.getFirst(),
                    String.join(",", speciesCodes.stream().distinct().toList())
            );
        }
    }
}

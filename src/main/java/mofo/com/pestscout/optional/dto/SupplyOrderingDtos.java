package mofo.com.pestscout.optional.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class SupplyOrderingDtos {

    private SupplyOrderingDtos() {
    }

    public record CreateSupplyOrderFromDraftRequest(
            @NotNull UUID farmId,
            String vendorName,
            String notes
    ) {
    }

    public record SupplyOrderDraftResponse(
            UUID farmId,
            String vendorHint,
            String currencyCode,
            BigDecimal estimatedTotal,
            List<SupplyOrderLineDto> items,
            List<String> advisoryNotes
    ) {
    }

    public record SupplyOrderResponse(
            UUID orderId,
            UUID farmId,
            String vendorName,
            String status,
            String currencyCode,
            BigDecimal estimatedTotal,
            String notes,
            LocalDateTime createdAt,
            LocalDateTime submittedAt,
            List<SupplyOrderLineDto> items
    ) {
    }

    public record SupplyOrderLineDto(
            String sku,
            String itemName,
            String unitOfMeasure,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal,
            String rationale,
            String sourceSpeciesCode
    ) {
    }
}

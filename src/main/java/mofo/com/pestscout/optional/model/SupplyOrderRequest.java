package mofo.com.pestscout.optional.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "supply_order_requests")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SupplyOrderRequest extends BaseEntity {

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Column(name = "requested_by_name", nullable = false, length = 255)
    private String requestedByName;

    @Column(name = "vendor_name", length = 255)
    private String vendorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SupplyOrderStatus status;

    @Builder.Default
    @Column(name = "currency_code", nullable = false, length = 8)
    private String currencyCode = "USD";

    @Column(name = "estimated_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedTotal;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Builder.Default
    @OneToMany(mappedBy = "orderRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SupplyOrderItem> items = new ArrayList<>();

    public void addItem(SupplyOrderItem item) {
        items.add(item);
        item.setOrderRequest(this);
    }
}

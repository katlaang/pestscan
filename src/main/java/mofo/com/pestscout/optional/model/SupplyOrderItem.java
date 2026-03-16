package mofo.com.pestscout.optional.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mofo.com.pestscout.common.model.BaseEntity;

import java.math.BigDecimal;

@Entity
@Table(name = "supply_order_items")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SupplyOrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_request_id", nullable = false)
    private SupplyOrderRequest orderRequest;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "unit_of_measure", nullable = false, length = 32)
    private String unitOfMeasure;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "rationale", length = 2000)
    private String rationale;

    @Column(name = "source_species_code", length = 255)
    private String sourceSpeciesCode;
}

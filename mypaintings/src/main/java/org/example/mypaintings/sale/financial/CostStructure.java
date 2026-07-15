package org.example.mypaintings.sale.financial;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;


@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CostStructure {

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "base_price_amount", precision = 10, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "base_price_currency", length = 3))
    })
    private Money basePrice;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "platform_fee_amount", precision = 10, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "platform_fee_currency", length = 3))
    })
    private Money platformFee;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "shipping_fee_amount", precision = 10, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "shipping_fee_currency", length = 3))
    })
    private Money shippingFee;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "platform_tax_amount", precision = 10, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "platform_tax_currency", length = 3))
    })
    private Money platformTax;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "total_price_amount", precision = 10, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "total_price_currency", length = 3))
    })
    private Money totalPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal appliedVatRate;

    @Column(precision = 10, scale = 2)
    private BigDecimal appliedCommissionRate;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "applied_shipping_cost_amount", precision = 10, scale = 2)),
            @AttributeOverride(name = "currency", column = @Column(name = "applied_shipping_cost_currency", length = 3))
    })
    private Money appliedShippingCost;

}
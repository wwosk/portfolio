package org.example.mypaintings.sale.financial;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
@ToString
public class Money implements Comparable<Money> {

    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;
    private static final int DEFAULT_SCALE = 2;

    @NotNull(message = "Amount cannot be null.")
    @PositiveOrZero(message = "Amount must be greater than or equal to zero.")
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @NotBlank(message = "Currency code cannot be blank.")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter ISO code (e.g., PLN, EUR).")
    @Column(length = 3, nullable = false)
    private String currency;

    public Money(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }

        this.amount = amount.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
        this.currency = normalizeAndValidateCurrency(currency);
    }


    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public static Money pln(BigDecimal amount) {
        return new Money(amount, "PLN");
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        validateSameCurrency(other);
        BigDecimal newAmount = this.amount.subtract(other.amount);
        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Resulting amount cannot be negative.");
        }
        return new Money(newAmount, this.currency);
    }

    public Money multiply(BigDecimal factor) {
        if (factor == null) {
            throw new IllegalArgumentException("Factor cannot be null");
        }
        if (factor.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Factor cannot be negative");
        }
        return new Money(this.amount.multiply(factor), this.currency);
    }


    public long toCents() {
        return amount.multiply(BigDecimal.valueOf(100)).longValue();
    }

    public String getCurrencyLowercase() {
        return currency.toLowerCase();
    }

    @Override
    public int compareTo(Money other) {
        validateSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    @PrePersist
    @PreUpdate
    private void prePersist() {
        this.currency = normalizeAndValidateCurrency(this.currency);
    }

    private String normalizeAndValidateCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("Currency code cannot be blank.");
        }

        String normalizedCode = currencyCode.trim().toUpperCase();

        if (!normalizedCode.matches("^[A-Z]{3}$")) {
            throw new IllegalArgumentException("Invalid currency code format: " + currencyCode);
        }

        try {

            Currency.getInstance(normalizedCode);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported or unknown ISO currency code: " + currencyCode, e);
        }

        return normalizedCode;
    }

    private void validateSameCurrency(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("Other Money cannot be null");
        }
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    String.format("Currency mismatch: cannot operate on %s and %s", this.currency, other.currency)
            );
        }
    }
}
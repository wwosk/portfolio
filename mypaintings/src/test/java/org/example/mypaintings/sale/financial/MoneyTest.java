package org.example.mypaintings.sale.financial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Money Test Suite")
class MoneyTest {

    // ------------------------------------------------------------------
    // Construction & Factories
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Construction and Factories")
    class Creation {

        @Test
        @DisplayName("constructor sets amount and currency correctly")
        void constructor_setsFields() {
            // given
            BigDecimal rawAmount = new BigDecimal("12.345");
            String currencyCode = "PLN";

            // when
            Money money = new Money(rawAmount, currencyCode);

            // then
            assertThat(money.getAmount()).isEqualByComparingTo("12.35");
            assertThat(money.getCurrency()).isEqualTo("PLN");
        }

        @Test
        @DisplayName("of() factory creates valid object")
        void of_createsObject() {
            // given
            BigDecimal amount = new BigDecimal("99.99");
            String currency = "EUR";

            // when
            Money money = Money.of(amount, currency);

            // then
            assertThat(money.getAmount()).isEqualByComparingTo("99.99");
            assertThat(money.getCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("pln() factory creates PLN currency object")
        void pln_factory() {
            // given
            BigDecimal amount = new BigDecimal("10.00");

            // when
            Money money = Money.pln(amount);

            // then
            assertThat(money.getCurrency()).isEqualTo("PLN");
            assertThat(money.getAmount()).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("zero() factory creates zero amount with given currency")
        void zero_factory() {
            // given
            String currency = "USD";

            // when
            Money zero = Money.zero(currency);

            // then
            assertThat(zero.getAmount()).isEqualByComparingTo("0.00");
            assertThat(zero.getCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("accepts zero amount (PositiveOrZero constraint)")
        void acceptsZeroAmount() {
            // when & then
            assertThatCode(() -> new Money(BigDecimal.ZERO, "PLN"))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {"PLN", "pln", "pLn", " pln ", "\tPLN\t","PLN\u0000"})
        @DisplayName("normalizes currency input using trim and upper case")
        void normalizesCurrency(String input) {
            // when
            Money money = new Money(BigDecimal.ONE, input);

            // then
            assertThat(money.getCurrency()).isEqualTo("PLN");
        }

        @Test
        @DisplayName("normalized currency makes equivalent money objects equal and share hash code")
        void normalizedCurrency_makesMoneyEqual() {
            // given
            Money a = Money.pln(BigDecimal.ONE);
            Money b = new Money(BigDecimal.ONE, " pln ");

            // when & then
            assertThat(a).isEqualTo(b);
            assertThat(a).hasSameHashCodeAs(b);
        }
    }

    // ------------------------------------------------------------------
    // Arithmetic Operations
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Arithmetic Operations")
    class Arithmetic {

        @Test
        @DisplayName("add() sums amounts of the same currency")
        void add_sameCurrency() {
            // given
            Money a = Money.pln(new BigDecimal("10.50"));
            Money b = Money.pln(new BigDecimal("4.25"));

            // when
            Money result = a.add(b);

            // then
            assertThat(result.getAmount()).isEqualByComparingTo("14.75");
            assertThat(result.getCurrency()).isEqualTo("PLN");
        }

        @Test
        @DisplayName("add() works across different currency string variants")
        void add_worksAcrossCurrencyVariants() {
            // given
            Money a = new Money(BigDecimal.ONE, " pln ");
            Money b = Money.pln(BigDecimal.ONE);

            // when
            Money result = a.add(b);

            // then
            assertThat(result.getAmount()).isEqualByComparingTo("2.00");
            assertThat(result.getCurrency()).isEqualTo("PLN");
        }

        @Test
        @DisplayName("subtract() subtracts amounts correctly")
        void subtract_sameCurrency() {
            // given
            Money a = Money.pln(new BigDecimal("10.50"));
            Money b = Money.pln(new BigDecimal("4.25"));

            // when
            Money result = a.subtract(b);

            // then
            assertThat(result.getAmount()).isEqualByComparingTo("6.25");
        }

        @Test
        @DisplayName("subtract() resulting in zero is allowed")
        void subtract_toZero() {
            // given
            Money a = Money.pln(new BigDecimal("5.00"));
            Money b = Money.pln(new BigDecimal("5.00"));

            // when
            Money result = a.subtract(b);

            // then
            assertThat(result.getAmount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("multiply() multiplies amount by factor")
        void multiply_byFactor() {
            // given
            Money money = Money.pln(new BigDecimal("10.00"));
            BigDecimal factor = new BigDecimal("1.5");

            // when
            Money result = money.multiply(factor);

            // then
            assertThat(result.getAmount()).isEqualByComparingTo("15.00");
        }

        @Test
        @DisplayName("operations preserve currency code")
        void operations_preserveCurrency() {
            // given
            Money eur = Money.of(new BigDecimal("1.00"), "EUR");

            // when & then
            assertThat(eur.add(eur).getCurrency()).isEqualTo("EUR");
            assertThat(eur.multiply(BigDecimal.TEN).getCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("applies HALF_UP rounding during addition")
        void rounding_halfUp() {
            // given
            Money a = new Money(new BigDecimal("0.005"), "PLN");
            Money b = new Money(new BigDecimal("0.004"), "PLN");

            // when & then
            assertThat(a.getAmount()).isEqualByComparingTo("0.01");
            assertThat(b.getAmount()).isEqualByComparingTo("0.00");
        }
    }

    // ------------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Helpers (toCents and getCurrencyLowercase)")
    class Helpers {

        @Test
        @DisplayName("toCents() returns exact amount in cents")
        void toCents() {
            // when & then
            assertThat(Money.pln(new BigDecimal("10.50")).toCents()).isEqualTo(1050L);
            assertThat(Money.pln(new BigDecimal("0.01")).toCents()).isEqualTo(1L);
            assertThat(Money.pln(BigDecimal.ZERO).toCents()).isEqualTo(0L);
        }

        @Test
        @DisplayName("getCurrencyLowercase() returns lower-case currency code")
        void currencyLowercase() {
            // when & then
            assertThat(Money.pln(BigDecimal.ONE).getCurrencyLowercase()).isEqualTo("pln");
        }
    }

    // ------------------------------------------------------------------
    // Comparison & Equality
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Comparison and Equality")
    class Comparison {

        @Test
        @DisplayName("compareTo compares amounts of the same currency")
        void compareTo() {
            // given
            Money small = Money.pln(new BigDecimal("1.00"));
            Money big = Money.pln(new BigDecimal("2.00"));
            Money equal = Money.pln(new BigDecimal("1.00"));

            // when & then
            assertThat(small.compareTo(big)).isNegative();
            assertThat(big.compareTo(small)).isPositive();
            assertThat(small.compareTo(equal)).isZero();
        }

        @Test
        @DisplayName("equals and hashCode operate on semantic value")
        void equalsHashCode() {
            // given
            Money a = Money.pln(new BigDecimal("5.00"));
            Money b = Money.pln(new BigDecimal("5.0000"));

            // when & then
            assertThat(a).isEqualTo(b);
            assertThat(a).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(Money.pln(new BigDecimal("5.01")));
            assertThat(a).isNotEqualTo(Money.of(new BigDecimal("5.00"), "EUR"));
        }
    }

    // ------------------------------------------------------------------
    // Validation Edge Cases (Constructor & Factories)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Constructor Validation")
    class Validation {

        @Test
        @DisplayName("throws exception for null amount")
        void nullAmount_throws() {
            // when & then
            assertThatThrownBy(() -> new Money(null, "PLN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount cannot be null");
        }

        @Test
        @DisplayName("throws exception for negative amount")
        void negativeAmount_throws() {
            // when & then
            assertThatThrownBy(() -> new Money(new BigDecimal("-0.01"), "PLN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount cannot be negative");
        }

        @Test
        @DisplayName("throws exception for null currency")
        void nullCurrency_throws() {
            // when & then
            assertThatThrownBy(() -> new Money(BigDecimal.ONE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency code cannot be blank");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t"})
        @DisplayName("throws exception for blank or whitespace currency")
        void blankCurrency_throws(String currency) {
            // when & then
            assertThatThrownBy(() -> new Money(BigDecimal.ONE, currency))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency code cannot be blank");
        }

        @ParameterizedTest
        @ValueSource(strings = {"PL", "PLNN", "PL1", "12P", "P L", "PLÑ", "PŁN"})
        @DisplayName("throws exception for malformed currency format")
        void malformedCurrency_throws(String currency) {
            // when & then
            assertThatThrownBy(() -> new Money(BigDecimal.ONE, currency))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid currency code format");
        }

        @ParameterizedTest
        @ValueSource(strings = {"XYZ", "ABC", "ZZZ", "AAA", "QQQ"})
        @DisplayName("throws exception for unknown ISO currency code")
        void unknownIsoCurrency_throws(String currency) {
            // when & then
            assertThatThrownBy(() -> new Money(BigDecimal.ONE, currency))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported or unknown ISO currency code");
        }

        @Test
        @DisplayName("throws format error when NBSP surrounds currency code")
        void nbspAroundCurrency_throwsFormatError() {
            // when & then
            assertThatThrownBy(() -> new Money(BigDecimal.ONE, "\u00A0PLN\u00A0"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid currency code format");
        }

        @Test
        @DisplayName("throws format error when zero-width space is inside currency code")
        void zeroWidthSpaceInCurrency_throwsFormatError() {
            // when & then
            assertThatThrownBy(() -> new Money(BigDecimal.ONE, "PL\u200BN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid currency code format");
        }

        @Test
        @DisplayName("zero() throws exception for null currency")
        void zero_nullCurrency_throws() {
            // when & then
            assertThatThrownBy(() -> Money.zero(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency code cannot be blank");
        }

        @Test
        @DisplayName("of() throws exception for null currency")
        void of_nullCurrency_throws() {
            // when & then
            assertThatThrownBy(() -> Money.of(BigDecimal.ONE, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency code cannot be blank");
        }

        @Test
        @DisplayName("pln() throws exception for null amount")
        void pln_nullAmount_throws() {
            // when & then
            assertThatThrownBy(() -> Money.pln(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Amount cannot be null");
        }
    }

    // ------------------------------------------------------------------
    // Operation Validation Edge Cases
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Operation Validation")
    class OperationValidation {

        @Test
        @DisplayName("add() throws exception on currency mismatch")
        void add_currencyMismatch() {
            // given
            Money pln = Money.pln(BigDecimal.ONE);
            Money eur = Money.of(BigDecimal.ONE, "EUR");

            // when & then
            assertThatThrownBy(() -> pln.add(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
        }

        @Test
        @DisplayName("add() throws exception for null argument")
        void add_null() {
            // when & then
            assertThatThrownBy(() -> Money.pln(BigDecimal.ONE).add(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Other Money cannot be null");
        }

        @Test
        @DisplayName("subtract() throws exception when resulting amount is negative")
        void subtract_negativeResult_throws() {
            // given
            Money a = Money.pln(new BigDecimal("5.00"));
            Money b = Money.pln(new BigDecimal("10.00"));

            // when & then
            assertThatThrownBy(() -> a.subtract(b))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Resulting amount cannot be negative");
        }

        @Test
        @DisplayName("subtract() throws exception on currency mismatch")
        void subtract_currencyMismatch() {
            // given
            Money pln = Money.pln(new BigDecimal("10.00"));
            Money eur = Money.of(new BigDecimal("1.00"), "EUR");

            // when & then
            assertThatThrownBy(() -> pln.subtract(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
        }

        @Test
        @DisplayName("multiply() throws exception for null factor")
        void multiply_null() {
            // when & then
            assertThatThrownBy(() -> Money.pln(BigDecimal.ONE).multiply(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Factor cannot be null");
        }

        @Test
        @DisplayName("multiply() throws exception for negative factor")
        void multiply_negativeFactor_throws() {
            // when & then
            assertThatThrownBy(() -> Money.pln(BigDecimal.TEN).multiply(new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Factor cannot be negative");
        }

        @Test
        @DisplayName("multiply() by zero returns zero amount")
        void multiply_byZero() {
            // when
            Money result = Money.pln(new BigDecimal("99.99")).multiply(BigDecimal.ZERO);

            // then
            assertThat(result.getAmount()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("compareTo() throws exception on currency mismatch")
        void compareTo_currencyMismatch() {
            // given
            Money pln = Money.pln(BigDecimal.ONE);
            Money eur = Money.of(BigDecimal.ONE, "EUR");

            // when & then
            assertThatThrownBy(() -> pln.compareTo(eur))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
        }

        @Test
        @DisplayName("compareTo() throws exception for null argument")
        void compareTo_null() {
            // when & then
            assertThatThrownBy(() -> Money.pln(BigDecimal.ONE).compareTo(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Other Money cannot be null");
        }
    }

    // ------------------------------------------------------------------
    // Scale & Precision
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Scale and Precision")
    class ScaleAndPrecision {

        @Test
        @DisplayName("amount scale is always normalized to 2 decimal places")
        void amountAlwaysScaledToTwo() {
            // when & then
            assertThat(new Money(new BigDecimal("1"), "PLN").getAmount().scale()).isEqualTo(2);
            assertThat(new Money(new BigDecimal("1.23456"), "PLN").getAmount().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("rounds half up for 0.005 up to 0.01")
        void roundsHalfUp() {
            // when & then
            assertThat(Money.pln(new BigDecimal("0.005")).getAmount())
                    .isEqualByComparingTo("0.01");
        }

        @Test
        @DisplayName("rounds down below half for 0.004 down to 0.00")
        void roundsDownBelowHalf() {
            // when & then
            assertThat(Money.pln(new BigDecimal("0.004")).getAmount())
                    .isEqualByComparingTo("0.00");
        }
    }
}

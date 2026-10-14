package com.kramp.productservice.domain;

import java.math.BigDecimal;
import java.util.Currency;

/** Monetary amounts, not percentages. The upstream owns price calculation. */
public record Pricing(BigDecimal basePrice, BigDecimal discountAmount,
                      BigDecimal finalPrice, Currency currency) {
}

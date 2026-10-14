package com.kramp.productservice.domain;

import java.util.Objects;

/** Catalog is required; null pricing or availability represents missing optional data. */
public record AggregatedProduct(Market market, Product product, Pricing pricing,
                                Availability availability, boolean personalized) {
    public AggregatedProduct {
        Objects.requireNonNull(market);
        Objects.requireNonNull(product);
    }
}

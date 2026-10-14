package com.kramp.productservice.api;

import com.kramp.productservice.domain.Availability;
import com.kramp.productservice.domain.AggregatedProduct;
import com.kramp.productservice.domain.Pricing;
import com.kramp.productservice.domain.Product;
import java.util.Objects;

/** Optional components always have an explicit status; missing data is null, never zero. */
public record ProductResponse(String market, Product product, PriceComponent pricing,
                              StockComponent availability, Personalization personalization) {
    public ProductResponse {
        Objects.requireNonNull(market);
        Objects.requireNonNull(product);
        Objects.requireNonNull(pricing);
        Objects.requireNonNull(availability);
        Objects.requireNonNull(personalization);
    }

    public static ProductResponse from(AggregatedProduct result) {
        return new ProductResponse(result.market().code(), result.product(),
                result.pricing() == null ? PriceComponent.unavailable() : PriceComponent.available(result.pricing()),
                result.availability() == null ? StockComponent.unknown() : StockComponent.available(result.availability()),
                result.personalized() ? Personalization.PERSONALIZED : Personalization.STANDARD);
    }

    public enum PriceStatus { AVAILABLE, UNAVAILABLE }
    public enum StockStatus { AVAILABLE, UNKNOWN }
    public enum Personalization { STANDARD, PERSONALIZED }

    public record PriceComponent(PriceStatus status, Pricing data) {
        public PriceComponent {
            Objects.requireNonNull(status);
            if ((status == PriceStatus.AVAILABLE) != (data != null)) {
                throw new IllegalArgumentException("Price status must agree with data presence.");
            }
        }
        public static PriceComponent available(Pricing data) {
            return new PriceComponent(PriceStatus.AVAILABLE, data);
        }
        public static PriceComponent unavailable() {
            return new PriceComponent(PriceStatus.UNAVAILABLE, null);
        }
    }

    public record StockComponent(StockStatus status, Availability data) {
        public StockComponent {
            Objects.requireNonNull(status);
            if ((status == StockStatus.AVAILABLE) != (data != null)) {
                throw new IllegalArgumentException("Stock status must agree with data presence.");
            }
        }
        public static StockComponent available(Availability data) {
            return new StockComponent(StockStatus.AVAILABLE, data);
        }
        public static StockComponent unknown() {
            return new StockComponent(StockStatus.UNKNOWN, null);
        }
    }
}

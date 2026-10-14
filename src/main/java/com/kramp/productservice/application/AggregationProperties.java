package com.kramp.productservice.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("aggregation")
public record AggregationProperties(
        @DefaultValue("400ms") Duration requestTimeout,
        @DefaultValue("150ms") Duration catalogTimeout,
        @DefaultValue("180ms") Duration pricingTimeout,
        @DefaultValue("200ms") Duration availabilityTimeout,
        @DefaultValue("120ms") Duration customerTimeout) {
    public AggregationProperties {
        for (Duration value : new Duration[]{requestTimeout, catalogTimeout, pricingTimeout,
                availabilityTimeout, customerTimeout}) {
            if (value == null || value.isZero() || value.isNegative() || value.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new IllegalArgumentException("Aggregation timeouts must be positive and at most 30 seconds.");
            }
        }
    }
}

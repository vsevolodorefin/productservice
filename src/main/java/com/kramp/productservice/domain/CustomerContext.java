package com.kramp.productservice.domain;

import java.util.Map;
import java.util.Objects;

/** Standard context is used when no customer is supplied or lookup fails. */
public record CustomerContext(String segment, Map<String, String> preferences, boolean personalized) {
    public CustomerContext {
        Objects.requireNonNull(segment);
        preferences = Map.copyOf(preferences);
    }

    public static CustomerContext standard() {
        return new CustomerContext("STANDARD", Map.of(), false);
    }
}

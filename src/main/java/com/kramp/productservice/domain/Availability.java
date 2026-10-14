package com.kramp.productservice.domain;

import java.time.LocalDate;

/** A known stock level of zero means out of stock. Delivery date may be unknown. */
public record Availability(long stockLevel, String warehouseLocation,
                           LocalDate expectedDeliveryDate) {
    public Availability {
        if (stockLevel < 0) {
            throw new IllegalArgumentException("Stock level must not be negative.");
        }
    }
}

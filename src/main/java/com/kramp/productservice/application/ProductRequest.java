package com.kramp.productservice.application;

import com.kramp.productservice.domain.Market;

public record ProductRequest(String productId, Market market, String customerId) {
}

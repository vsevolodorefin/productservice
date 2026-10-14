package com.kramp.productservice.upstream;

import com.kramp.productservice.domain.*;

public interface PricingClient {
    Pricing getPricing(String productId, Market market, CustomerContext customerContext);
}

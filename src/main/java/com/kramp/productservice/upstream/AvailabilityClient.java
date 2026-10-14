package com.kramp.productservice.upstream;

import com.kramp.productservice.domain.Availability;
import com.kramp.productservice.domain.Market;

public interface AvailabilityClient {
    Availability getAvailability(String productId, Market market);
}

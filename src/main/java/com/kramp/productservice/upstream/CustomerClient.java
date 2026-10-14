package com.kramp.productservice.upstream;

import com.kramp.productservice.domain.CustomerContext;
import com.kramp.productservice.domain.Market;

public interface CustomerClient {
    CustomerContext getCustomer(String customerId, Market market);
}

package com.kramp.productservice.application;

import com.kramp.productservice.domain.AggregatedProduct;
import org.springframework.stereotype.Service;

/** Temporary implementation until the aggregation step is implemented. */
@Service
public class PendingProductQuery implements ProductQuery {
    @Override
    public AggregatedProduct getProduct(ProductRequest request) {
        throw new AggregationNotImplementedException();
    }
}

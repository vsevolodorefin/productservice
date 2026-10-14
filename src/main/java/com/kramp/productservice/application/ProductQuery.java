package com.kramp.productservice.application;

import com.kramp.productservice.domain.AggregatedProduct;

public interface ProductQuery {
    AggregatedProduct getProduct(ProductRequest request);
}

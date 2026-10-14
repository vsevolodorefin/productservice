package com.kramp.productservice.upstream;

import com.kramp.productservice.domain.Market;
import com.kramp.productservice.domain.Product;

public interface CatalogClient {
    Product getProduct(String productId, Market market);
}

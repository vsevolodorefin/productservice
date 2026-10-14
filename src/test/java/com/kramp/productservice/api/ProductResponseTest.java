package com.kramp.productservice.api;

import com.kramp.productservice.domain.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductResponseTest {
    @Test
    void rejectsInconsistentComponentStates() {
        var pricing = new Pricing(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN, Market.PL_PL.currency());
        assertThrows(IllegalArgumentException.class, () -> ProductResponse.PriceComponent.available(null));
        assertThrows(IllegalArgumentException.class, () -> new ProductResponse.PriceComponent(
                ProductResponse.PriceStatus.UNAVAILABLE, pricing));
        assertThrows(IllegalArgumentException.class, () -> ProductResponse.StockComponent.available(null));
        assertThrows(IllegalArgumentException.class, () -> new ProductResponse.StockComponent(
                ProductResponse.StockStatus.UNKNOWN, new Availability(0, "Warsaw", null)));
    }
}

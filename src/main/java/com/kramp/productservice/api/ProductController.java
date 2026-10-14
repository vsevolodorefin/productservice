package com.kramp.productservice.api;

import com.kramp.productservice.application.ProductQuery;
import com.kramp.productservice.application.ProductRequest;
import com.kramp.productservice.domain.Market;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductQuery productQuery;

    public ProductController(ProductQuery productQuery) {
        this.productQuery = productQuery;
    }

    @GetMapping("/{productId}")
    public ProductResponse getProduct(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,99}") String productId,
            @RequestParam @NotBlank @Size(max = 10) String market,
            @RequestParam(required = false)
            @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,99}") String customerId) {
        return ProductResponse.from(productQuery.getProduct(
                new ProductRequest(productId, Market.fromCode(market), customerId)));
    }
}

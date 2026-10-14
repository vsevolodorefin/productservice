package com.kramp.productservice.api;

import com.kramp.productservice.application.*;
import com.kramp.productservice.domain.*;
import com.kramp.productservice.upstream.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductControllerTest {
    MockMvc mvc;
    StubProductQuery query;

    @BeforeEach
    void setUp() {
        query = new StubProductQuery();
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new ProductController(query))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private final Product product = new Product("P-123", "Filtr", "Opis", Map.of(), List.of());

    @Test
    void returnsLocalizedPersonalizedProductAndPassesCustomerContext() throws Exception {
        var pricing = new Pricing(new BigDecimal("100.00"), new BigDecimal("10.00"),
                new BigDecimal("90.00"), Market.PL_PL.currency());
        query.result = new AggregatedProduct(
                Market.PL_PL, product, pricing, new Availability(0, "Warsaw", null), true);
        mvc.perform(get("/api/v1/products/P-123").param("market", "pl-PL").param("customerId", "C-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.market").value("pl-PL"))
                .andExpect(jsonPath("$.product.name").value("Filtr"))
                .andExpect(jsonPath("$.pricing.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.pricing.data.currency").value("PLN"))
                .andExpect(jsonPath("$.pricing.data.finalPrice").value(90.0))
                .andExpect(jsonPath("$.availability.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.availability.data.stockLevel").value(0))
                .andExpect(jsonPath("$.personalization").value("PERSONALIZED"));
        org.junit.jupiter.api.Assertions.assertEquals(new ProductRequest("P-123", Market.PL_PL, "C-1"), query.request);
    }

    @Test
    void partialResponseHasExplicitStatesAndNullData() throws Exception {
        query.result = new AggregatedProduct(Market.DE_DE, product, null, null, false);
        mvc.perform(get("/api/v1/products/P-123").param("market", "de-DE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricing.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.pricing.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.availability.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.availability.data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.personalization").value("STANDARD"));
        org.junit.jupiter.api.Assertions.assertEquals(new ProductRequest("P-123", Market.DE_DE, null), query.request);
    }

    @ParameterizedTest
    @CsvSource({"nl-NL,NL_NL", "de-DE,DE_DE", "pl-PL,PL_PL"})
    void acceptsSupportedMarkets(String code, Market market) throws Exception {
        query.result = new AggregatedProduct(market, product, null, null, false);
        mvc.perform(get("/api/v1/products/P-123").param("market", code)).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(new ProductRequest("P-123", market, null), query.request);
    }

    @Test
    void rejectsMissingMarket() throws Exception {
        mvc.perform(get("/api/v1/products/P-123"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        org.junit.jupiter.api.Assertions.assertNull(query.request);
    }

    @Test
    void rejectsUnsupportedMarket() throws Exception {
        mvc.perform(get("/api/v1/products/P-123").param("market", "en-US"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.type").value("urn:productservice:problem:unsupported-market"));
        org.junit.jupiter.api.Assertions.assertNull(query.request);
    }

    @Test
    void rejectsBlankMarketAndCustomer() throws Exception {
        mvc.perform(get("/api/v1/products/P-123").param("market", " ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/P-123").param("market", "pl-PL").param("customerId", ""))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertNull(query.request);
    }

    @Test
    void rejectsInvalidAndOversizedIdentifiers() throws Exception {
        mvc.perform(get("/api/v1/products/bad id").param("market", "pl-PL")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/" + "a".repeat(101)).param("market", "pl-PL")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products/P-123").param("market", "pl-PL").param("customerId", "a".repeat(101)))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertNull(query.request);
    }

    @Test
    void mapsRequiredSourceFailuresToProblemDetails() throws Exception {
        assertFailure(new ProductNotFoundException("P-123"), 404, "product-not-found");
        assertFailure(new UpstreamUnavailableException("catalog", null), 503, "upstream-unavailable");
        assertFailure(new UpstreamTimeoutException("catalog", null), 504, "upstream-timeout");
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        query.failure = new RuntimeException("secret upstream information");
        mvc.perform(get("/api/v1/products/P-123").param("market", "pl-PL"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    private void assertFailure(RuntimeException failure, int status, String code) throws Exception {
        query.failure = failure;
        mvc.perform(get("/api/v1/products/P-123").param("market", "pl-PL"))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:productservice:problem:" + code))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.instance").value("/api/v1/products/P-123"));
    }
    private static class StubProductQuery implements ProductQuery {
        AggregatedProduct result;
        ProductRequest request;
        RuntimeException failure;

        @Override
        public AggregatedProduct getProduct(ProductRequest request) {
            this.request = request;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}

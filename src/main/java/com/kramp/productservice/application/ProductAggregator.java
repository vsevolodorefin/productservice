package com.kramp.productservice.application;

import com.kramp.productservice.domain.*;
import com.kramp.productservice.upstream.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProductAggregator implements ProductQuery {
    private static final Logger log = LoggerFactory.getLogger(ProductAggregator.class);
    private final CatalogClient catalog;
    private final PricingClient pricing;
    private final AvailabilityClient availability;
    private final CustomerClient customer;
    private final UpstreamCalls calls;
    private final AggregationProperties settings;

    public ProductAggregator(CatalogClient catalog, PricingClient pricing, AvailabilityClient availability,
                             CustomerClient customer, UpstreamCalls calls, AggregationProperties settings) {
        this.catalog = catalog;
        this.pricing = pricing;
        this.availability = availability;
        this.customer = customer;
        this.calls = calls;
        this.settings = settings;
    }

    @Override
    public AggregatedProduct getProduct(ProductRequest request) {
        try (var scope = calls.open(settings.requestTimeout())) {
            var product = scope.start("catalog", settings.catalogTimeout(),
                    () -> catalog.getProduct(request.productId(), request.market()));
            var stock = scope.start("availability", settings.availabilityTimeout(),
                    () -> availability.getAvailability(request.productId(), request.market()))
                    .exceptionally(failure -> optionalFallback("availability", failure, null));
            CompletableFuture<CustomerContext> context = request.customerId() == null
                    ? CompletableFuture.completedFuture(CustomerContext.standard())
                    : scope.start("customer", settings.customerTimeout(),
                            () -> customer.getCustomer(request.customerId(), request.market()))
                            .exceptionally(failure -> optionalFallback("customer", failure, CustomerContext.standard()));
            var price = context.thenCompose(resolved -> scope.start("pricing", settings.pricingTimeout(),
                    () -> pricing.getPricing(request.productId(), request.market(), resolved)))
                    .exceptionally(failure -> optionalFallback("pricing", failure, null));

            // Catalog is checked first so its failure cancels optional work immediately.
            Product requiredProduct = requiredCatalog(product);
            return new AggregatedProduct(request.market(), requiredProduct, price.join(), stock.join(),
                    context.join().personalized());
        }
    }

    private static Product requiredCatalog(CompletableFuture<Product> result) {
        try {
            return result.join();
        } catch (CompletionException exception) {
            Throwable cause = unwrap(exception);
            if (cause instanceof ProductNotFoundException notFound) { throw notFound; }
            if (cause instanceof UpstreamTimeoutException timeout) { throw timeout; }
            if (cause instanceof UpstreamUnavailableException unavailable) { throw unavailable; }
            throw new UpstreamUnavailableException("catalog", cause);
        }
    }

    private static <T> T optionalFallback(String source, Throwable failure, T fallback) {
        Throwable cause = unwrap(failure);
        if (!(cause instanceof CancellationException)) {
            log.warn("Optional upstream {} failed ({}); using fallback", source, cause.getClass().getSimpleName());
        }
        return fallback;
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }
}

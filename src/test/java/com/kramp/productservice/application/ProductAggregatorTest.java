package com.kramp.productservice.application;

import com.kramp.productservice.domain.*;
import com.kramp.productservice.upstream.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductAggregatorTest {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(2);
    private final UpstreamCalls calls = new UpstreamCalls(executor, scheduler);
    private final Product product = new Product("P-123", "Filtr oleju", "Opis", Map.of(), List.of());
    private final Pricing standardPrice = new Pricing(new BigDecimal("100.00"), BigDecimal.ZERO,
            new BigDecimal("100.00"), Market.PL_PL.currency());
    private final Availability stock = new Availability(42, "Warsaw", null);
    private final ProductRequest anonymous = new ProductRequest("P-123", Market.PL_PL, null);
    private final ProductRequest identified = new ProductRequest("P-123", Market.PL_PL, "C-1");
    private final CustomerContext vip = new CustomerContext("VIP", Map.of(), true);
    private CatalogClient catalog = (id, market) -> product;
    private PricingClient pricing = (id, market, context) -> standardPrice;
    private AvailabilityClient availability = (id, market) -> stock;
    private CustomerClient customer = (id, market) -> vip;
    private AggregationProperties settings = new AggregationProperties(Duration.ofSeconds(2),
            Duration.ofSeconds(1), Duration.ofMillis(500), Duration.ofMillis(500), Duration.ofMillis(100));

    @AfterEach
    void shutdown() throws Exception {
        executor.shutdownNow();
        scheduler.shutdownNow();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS), "Upstream work must terminate");
        assertTrue(scheduler.awaitTermination(2, TimeUnit.SECONDS));
    }

    private ProductAggregator aggregator() {
        return new ProductAggregator(catalog, pricing, availability, customer, calls, settings);
    }

    @Test
    void anonymousCustomerSkipsLookupAndUsesStandardPricing() {
        customer = (id, market) -> { fail("Customer lookup must be skipped"); return null; };
        pricing = (id, market, context) -> {
            assertEquals(CustomerContext.standard(), context);
            return standardPrice;
        };
        var result = aggregator().getProduct(anonymous);
        assertEquals(product, result.product());
        assertEquals(stock, result.availability());
        assertEquals(standardPrice, result.pricing());
        assertFalse(result.personalized());
    }

    @Test
    void independentSourcesRunConcurrentlyAndPriceUsesResolvedCustomer() {
        var otherSourcesStarted = new CountDownLatch(2);
        var customerResolved = new AtomicBoolean();
        catalog = (id, market) -> {
            await(otherSourcesStarted);
            return product;
        };
        availability = (id, market) -> { otherSourcesStarted.countDown(); return stock; };
        customer = (id, market) -> {
            otherSourcesStarted.countDown();
            customerResolved.set(true);
            return vip;
        };
        pricing = (id, market, context) -> {
            assertTrue(customerResolved.get());
            assertEquals(vip, context);
            return standardPrice;
        };
        assertTrue(aggregator().getProduct(identified).personalized());
    }

    @Test
    void pricingFailureDoesNotLoseProductOrStock() {
        pricing = (id, market, context) -> { throw new UpstreamUnavailableException("pricing", null); };
        var result = aggregator().getProduct(anonymous);
        assertNull(result.pricing());
        assertEquals(product, result.product());
        assertEquals(stock, result.availability());
    }

    @Test
    void availabilityFailureDoesNotLosePrice() {
        availability = (id, market) -> { throw new UpstreamUnavailableException("availability", null); };
        var result = aggregator().getProduct(anonymous);
        assertNull(result.availability());
        assertEquals(standardPrice, result.pricing());
    }

    @Test
    void simultaneousOptionalFailuresStillReturnCatalogAndStandardContext() {
        pricing = (id, market, context) -> { throw new UpstreamUnavailableException("pricing", null); };
        availability = (id, market) -> { throw new UpstreamUnavailableException("availability", null); };
        customer = (id, market) -> { throw new UpstreamUnavailableException("customer", null); };
        var result = aggregator().getProduct(identified);
        assertEquals(product, result.product());
        assertNull(result.pricing());
        assertNull(result.availability());
        assertFalse(result.personalized());
    }

    @Test
    void customerFailurePassesStandardContextToPricing() {
        var received = new AtomicReference<CustomerContext>();
        customer = (id, market) -> { throw new UpstreamUnavailableException("customer", null); };
        pricing = (id, market, context) -> { received.set(context); return standardPrice; };
        var result = aggregator().getProduct(identified);
        assertEquals(CustomerContext.standard(), received.get());
        assertEquals(standardPrice, result.pricing());
        assertFalse(result.personalized());
    }

    @Test
    void customerTimeoutInterruptsLookupAndStillFetchesStandardPrice() {
        var interrupted = new CountDownLatch(1);
        customer = (id, market) -> blockUntilInterrupted(interrupted);
        pricing = (id, market, context) -> {
            assertEquals(CustomerContext.standard(), context);
            return standardPrice;
        };
        var result = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> aggregator().getProduct(identified));
        assertFalse(result.personalized());
        assertEquals(standardPrice, result.pricing());
        await(interrupted);
    }

    @Test
    void pricingAndAvailabilityTimeoutsInterruptBothWorkers() {
        var interrupted = new CountDownLatch(2);
        pricing = (id, market, context) -> blockUntilInterrupted(interrupted);
        availability = (id, market) -> blockUntilInterrupted(interrupted);
        var result = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> aggregator().getProduct(anonymous));
        assertNull(result.pricing());
        assertNull(result.availability());
        await(interrupted);
    }

    @Test
    void missingProductIsNotAnUpstreamOutage() {
        catalog = (id, market) -> { throw new ProductNotFoundException(id); };
        assertThrows(ProductNotFoundException.class, () -> aggregator().getProduct(anonymous));
    }

    @Test
    void catalogFailureCancelsInFlightOptionalWork() {
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        availability = (id, market) -> { started.countDown(); return blockUntilInterrupted(interrupted); };
        catalog = (id, market) -> {
            await(started);
            throw new UpstreamUnavailableException("catalog", null);
        };
        assertThrows(UpstreamUnavailableException.class, () -> aggregator().getProduct(anonymous));
        await(interrupted);
    }

    @Test
    void catalogTimeoutFailsRequestAndInterruptsWorker() {
        var interrupted = new CountDownLatch(1);
        catalog = (id, market) -> blockUntilInterrupted(interrupted);
        assertThrows(UpstreamTimeoutException.class, () -> aggregator().getProduct(anonymous));
        await(interrupted);
    }

    @Test
    void overallBudgetCapsCustomerAndDependentPriceRatherThanAddingTheirTimeouts() {
        settings = new AggregationProperties(Duration.ofMillis(250), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(100));
        var interrupted = new CountDownLatch(2);
        customer = (id, market) -> blockUntilInterrupted(interrupted);
        pricing = (id, market, context) -> blockUntilInterrupted(interrupted);
        long started = System.nanoTime();
        var result = aggregator().getProduct(identified);
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofMillis(800)) < 0,
                "The request budget must cap the dependent pricing call");
        assertNull(result.pricing());
        assertFalse(result.personalized());
        await(interrupted);
    }

    @Test
    void expiredScopeDoesNotStartNewUpstreamWork() {
        try (var scope = calls.open(Duration.ofNanos(1))) {
            var result = scope.start("pricing", Duration.ofSeconds(1), () -> {
                fail("Expired request must not call upstream"); return standardPrice;
            });
            var failure = assertThrows(CompletionException.class, result::join);
            assertInstanceOf(UpstreamTimeoutException.class, failure.getCause());
        }
    }

    private static <T> T blockUntilInterrupted(CountDownLatch interrupted) {
        try {
            new CountDownLatch(1).await();
            throw new AssertionError("Unexpected release");
        } catch (InterruptedException exception) {
            interrupted.countDown();
            Thread.currentThread().interrupt();
            throw new UpstreamUnavailableException("test", exception);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(2, TimeUnit.SECONDS), "Expected asynchronous event did not occur");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}

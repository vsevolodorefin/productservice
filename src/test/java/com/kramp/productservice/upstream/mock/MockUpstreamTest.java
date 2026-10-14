package com.kramp.productservice.upstream.mock;

import com.kramp.productservice.domain.*;
import com.kramp.productservice.upstream.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class MockUpstreamTest {
    private final MockUpstreamConfiguration configuration = new MockUpstreamConfiguration();
    private final MockProperties.Source reliable = source(1, MockProperties.Mode.NORMAL);
    private final MockProperties settings = new MockProperties(reliable, reliable, reliable, reliable);

    @ParameterizedTest
    @CsvSource({"NL_NL,Oliefilter,EUR,25.00,Varsseveld", "DE_DE,Ölfilter,EUR,24.00,Berlin", "PL_PL,Filtr oleju,PLN,100.00,Warsaw"})
    void localizesProductPriceAndWarehouse(Market market, String name, String currency, String amount, String warehouse) {
        assertEquals(name, configuration.catalogClient(settings).getProduct("P-123", market).name());
        var price = configuration.pricingClient(settings).getPricing("P-123", market, CustomerContext.standard());
        assertEquals(currency, price.currency().getCurrencyCode());
        assertEquals(new BigDecimal(amount), price.finalPrice());
        var clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
        var stock = configuration.availabilityClient(settings, clock).getAvailability("P-123", market);
        assertEquals(warehouse, stock.warehouseLocation());
        assertEquals(LocalDate.of(2026, 10, 16), stock.expectedDeliveryDate());
    }

    @Test
    void vipDiscountIsConsistentAndStandardContextHasNoDiscount() {
        var customer = configuration.customerClient(settings).getCustomer("C-1", Market.PL_PL);
        var price = configuration.pricingClient(settings).getPricing("P-123", Market.PL_PL, customer);
        assertTrue(customer.personalized());
        assertEquals(new BigDecimal("10.00"), price.discountAmount());
        assertEquals(new BigDecimal("90.00"), price.finalPrice());
        assertEquals(price.basePrice(), price.discountAmount().add(price.finalPrice()));
        assertEquals(CustomerContext.standard(), configuration.customerClient(settings).getCustomer("unknown", Market.PL_PL));
    }

    @Test
    void knownZeroStockDiffersFromMissingProduct() {
        assertEquals(0, configuration.availabilityClient(settings, Clock.systemUTC())
                .getAvailability("P-456", Market.PL_PL).stockLevel());
        assertThrows(ProductNotFoundException.class,
                () -> configuration.catalogClient(settings).getProduct("missing", Market.PL_PL));
    }

    @Test
    void reliabilityBoundariesAndForcedFailureAreDeterministic() {
        assertDoesNotThrow(() -> MockSimulation.execute("catalog", reliable));
        assertThrows(UpstreamUnavailableException.class,
                () -> MockSimulation.execute("catalog", source(0, MockProperties.Mode.NORMAL)));
        assertThrows(UpstreamUnavailableException.class,
                () -> MockSimulation.execute("catalog", source(1, MockProperties.Mode.FAIL)));
    }

    @Test
    void mockSleepCanBeInterrupted() throws Exception {
        var interrupted = new java.util.concurrent.CountDownLatch(1);
        Thread thread = Thread.ofVirtual().start(() -> {
            try {
                MockSimulation.execute("catalog", new MockProperties.Source(Duration.ZERO, Duration.ZERO,
                        1, MockProperties.Mode.SLOW, Duration.ofSeconds(10)));
            } catch (UpstreamUnavailableException exception) {
                if (Thread.currentThread().isInterrupted()) { interrupted.countDown(); }
            }
        });
        thread.interrupt();
        assertTrue(interrupted.await(2, java.util.concurrent.TimeUnit.SECONDS));
        thread.join(2000);
        assertFalse(thread.isAlive());
    }

    @Test
    void invalidReliabilityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> source(Double.NaN, MockProperties.Mode.NORMAL));
        assertThrows(IllegalArgumentException.class, () -> source(1.1, MockProperties.Mode.NORMAL));
    }

    private static MockProperties.Source source(double reliability, MockProperties.Mode mode) {
        return new MockProperties.Source(Duration.ZERO, Duration.ZERO, reliability, mode, Duration.ofSeconds(2));
    }
}

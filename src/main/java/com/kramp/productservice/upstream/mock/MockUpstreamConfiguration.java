package com.kramp.productservice.upstream.mock;

import com.kramp.productservice.domain.*;
import com.kramp.productservice.upstream.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MockProperties.class)
public class MockUpstreamConfiguration {
    @Bean
    Clock deliveryClock() { return Clock.systemUTC(); }

    @Bean
    CatalogClient catalogClient(MockProperties settings) {
        return (id, market) -> {
            MockSimulation.execute("catalog", settings.catalog());
            if (!id.equals("P-123") && !id.equals("P-456")) { throw new ProductNotFoundException(id); }
            boolean filter = id.equals("P-123");
            String name = switch (market) {
                case PL_PL -> filter ? "Filtr oleju" : "Pasek klinowy";
                case DE_DE -> filter ? "Ölfilter" : "Keilriemen";
                case NL_NL -> filter ? "Oliefilter" : "V-riem";
            };
            String description = switch (market) {
                case PL_PL -> "Część zamienna do maszyn rolniczych";
                case DE_DE -> "Ersatzteil für Landmaschinen";
                case NL_NL -> "Reserveonderdeel voor landbouwmachines";
            };
            // No fabricated image URL: an empty list explicitly means no demo images.
            return new Product(id, name, description, Map.of("sku", id), List.of());
        };
    }

    @Bean
    PricingClient pricingClient(MockProperties settings) {
        return (id, market, context) -> {
            MockSimulation.execute("pricing", settings.pricing());
            BigDecimal base = switch (market) {
                case PL_PL -> new BigDecimal("100.00");
                case DE_DE -> new BigDecimal("24.00");
                case NL_NL -> new BigDecimal("25.00");
            };
            if (id.equals("P-456")) { base = base.multiply(new BigDecimal("2")); }
            BigDecimal rate = context.personalized() && context.segment().equals("VIP")
                    ? new BigDecimal("0.10") : BigDecimal.ZERO;
            BigDecimal discount = base.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            return new Pricing(base, discount, base.subtract(discount), market.currency());
        };
    }

    @Bean
    AvailabilityClient availabilityClient(MockProperties settings, Clock deliveryClock) {
        return (id, market) -> {
            MockSimulation.execute("availability", settings.availability());
            String warehouse = switch (market) {
                case PL_PL -> "Warsaw";
                case DE_DE -> "Berlin";
                case NL_NL -> "Varsseveld";
            };
            boolean inStock = id.equals("P-123");
            return new Availability(inStock ? 42 : 0, warehouse,
                    inStock ? LocalDate.now(deliveryClock).plusDays(2) : null);
        };
    }

    @Bean
    CustomerClient customerClient(MockProperties settings) {
        return (id, market) -> {
            MockSimulation.execute("customer", settings.customer());
            return id.equals("C-1") || id.equals("customer-123")
                    ? new CustomerContext("VIP", Map.of("language", market.locale().getLanguage()), true)
                    : CustomerContext.standard();
        };
    }
}

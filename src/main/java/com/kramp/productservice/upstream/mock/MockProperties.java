package com.kramp.productservice.upstream.mock;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("mock")
public record MockProperties(@DefaultValue Source catalog, @DefaultValue Source pricing,
                             @DefaultValue Source availability, @DefaultValue Source customer) {
    public enum Mode { NORMAL, FAIL, SLOW }

    public record Source(@DefaultValue("50ms") Duration latency,
                         @DefaultValue("10ms") Duration jitter,
                         @DefaultValue("1.0") double reliability,
                         @DefaultValue("NORMAL") Mode mode,
                         @DefaultValue("2s") Duration slowLatency) {
        public Source {
            for (Duration value : new Duration[]{latency, jitter, slowLatency}) {
                if (value == null || value.isNegative() || value.compareTo(Duration.ofMinutes(1)) > 0) {
                    throw new IllegalArgumentException("Mock durations must be between zero and one minute.");
                }
            }
            if (!Double.isFinite(reliability) || reliability < 0 || reliability > 1 || mode == null) {
                throw new IllegalArgumentException("Mock reliability must be in [0, 1] and mode must be set.");
            }
        }
    }
}

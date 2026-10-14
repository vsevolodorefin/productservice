package com.kramp.productservice.upstream.mock;

import com.kramp.productservice.upstream.UpstreamUnavailableException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

final class MockSimulation {
    private MockSimulation() { }

    static void execute(String source, MockProperties.Source settings) {
        long jitter = settings.jitter().toNanos();
        long delay = settings.mode() == MockProperties.Mode.SLOW ? settings.slowLatency().toNanos()
                : Math.max(0, settings.latency().toNanos()
                        + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1));
        try {
            TimeUnit.NANOSECONDS.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UpstreamUnavailableException(source, exception);
        }
        if (settings.mode() == MockProperties.Mode.FAIL
                || (settings.mode() == MockProperties.Mode.NORMAL
                && ThreadLocalRandom.current().nextDouble() >= settings.reliability())) {
            throw new UpstreamUnavailableException(source, null);
        }
    }
}

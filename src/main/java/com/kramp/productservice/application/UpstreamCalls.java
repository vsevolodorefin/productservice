package com.kramp.productservice.application;

import com.kramp.productservice.upstream.UpstreamTimeoutException;
import com.kramp.productservice.upstream.UpstreamUnavailableException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Independent deadlines stop both waiting and interruptible upstream work. */
@Component
public class UpstreamCalls {
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;

    public UpstreamCalls(@Qualifier("upstreamExecutor") ExecutorService executor,
                         @Qualifier("upstreamScheduler") ScheduledExecutorService scheduler) {
        this.executor = executor;
        this.scheduler = scheduler;
    }

    public RequestScope open(Duration budget) {
        return new RequestScope(System.nanoTime() + budget.toNanos());
    }

    public final class RequestScope implements AutoCloseable {
        private final long deadline;
        private final ConcurrentLinkedQueue<Call<?>> calls = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean closed = new AtomicBoolean();

        private RequestScope(long deadline) {
            this.deadline = deadline;
        }

        public <T> CompletableFuture<T> start(String source, Duration timeout, Supplier<T> action) {
            var result = new CompletableFuture<T>();
            var worker = new FutureTask<Void>(() -> {
                try {
                    T value = Objects.requireNonNull(action.get(), "Upstream returned null");
                    result.complete(value);
                } catch (Exception exception) {
                    result.completeExceptionally(exception);
                }
                return null;
            });
            var call = new Call<>(result, worker);
            calls.add(call);
            long remaining = Math.min(timeout.toNanos(), deadline - System.nanoTime());
            if (closed.get()) {
                call.cancel();
            } else if (remaining <= 0) {
                result.completeExceptionally(new UpstreamTimeoutException(source, null));
                worker.cancel(true);
            } else {
                try {
                    var timer = scheduler.schedule(() -> {
                        if (result.completeExceptionally(new UpstreamTimeoutException(source, null))) {
                            worker.cancel(true);
                        }
                    }, remaining, TimeUnit.NANOSECONDS);
                    result.whenComplete((value, failure) -> timer.cancel(false));
                    executor.execute(worker);
                } catch (RejectedExecutionException exception) {
                    result.completeExceptionally(new UpstreamUnavailableException(source, exception));
                    worker.cancel(true);
                }
            }
            return result;
        }

        @Override
        public void close() {
            closed.set(true);
            calls.forEach(Call::cancel);
        }
    }

    private record Call<T>(CompletableFuture<T> result, FutureTask<Void> worker) {
        void cancel() {
            result.cancel(false);
            worker.cancel(true);
        }
    }
}

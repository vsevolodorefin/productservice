/**
 * Blocking upstream ports. Implementations return non-null data or throw a typed failure.
 * The aggregator owns concurrency, timeouts and optional-source fallback.
 * Pricing receives a standard context when customer lookup is absent or fails.
 */
package com.kramp.productservice.upstream;

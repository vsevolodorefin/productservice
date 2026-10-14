package com.kramp.productservice.upstream;

public class UpstreamTimeoutException extends RuntimeException {
    public UpstreamTimeoutException(String source, Throwable cause) {
        super("Upstream timed out: " + source, cause);
    }
}

package com.kramp.productservice.upstream;

public class UpstreamUnavailableException extends RuntimeException {
    public UpstreamUnavailableException(String source, Throwable cause) {
        super("Upstream unavailable: " + source, cause);
    }
}

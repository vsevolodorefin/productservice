package com.kramp.productservice.domain;

public class UnsupportedMarketException extends RuntimeException {
    public UnsupportedMarketException(String code) {
        super("Unsupported market. Supported values: nl-NL, de-DE, pl-PL.");
    }
}

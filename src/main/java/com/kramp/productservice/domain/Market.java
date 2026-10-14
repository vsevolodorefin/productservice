package com.kramp.productservice.domain;

import java.util.Currency;
import java.util.Locale;
import java.util.Arrays;

public enum Market {
    NL_NL("nl-NL", "EUR"), DE_DE("de-DE", "EUR"), PL_PL("pl-PL", "PLN");

    private final String code;
    private final Currency currency;

    Market(String code, String currency) {
        this.code = code;
        this.currency = Currency.getInstance(currency);
    }

    public String code() { return code; }
    public Locale locale() { return Locale.forLanguageTag(code); }
    public Currency currency() { return currency; }

    public static Market fromCode(String code) {
        return Arrays.stream(values()).filter(market -> market.code.equals(code))
                .findFirst().orElseThrow(() -> new UnsupportedMarketException(code));
    }
}

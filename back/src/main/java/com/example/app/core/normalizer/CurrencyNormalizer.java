package com.example.app.core.normalizer;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Normaliseur pour les devises (ISO 4217 en 3 lettres majuscules).
 */
@Component
public class CurrencyNormalizer implements ValueNormalizer {

    public static final String TYPE = "currency";

    private static final Map<String, String> SYMBOL_MAP = new HashMap<>();

    static {
        SYMBOL_MAP.put("$", "USD");
        SYMBOL_MAP.put("US$", "USD");
        SYMBOL_MAP.put("USD", "USD");
        SYMBOL_MAP.put("DOLLARS", "USD");
        SYMBOL_MAP.put("DOLLAR", "USD");
        SYMBOL_MAP.put("€", "EUR");
        SYMBOL_MAP.put("EUR", "EUR");
        SYMBOL_MAP.put("EURO", "EUR");
        SYMBOL_MAP.put("EUROS", "EUR");
        SYMBOL_MAP.put("£", "GBP");
        SYMBOL_MAP.put("GBP", "GBP");
        SYMBOL_MAP.put("POUND", "GBP");
        SYMBOL_MAP.put("POUNDS", "GBP");
        SYMBOL_MAP.put("¥", "JPY");
        SYMBOL_MAP.put("JPY", "JPY");
        SYMBOL_MAP.put("YEN", "JPY");
        SYMBOL_MAP.put("CHF", "CHF");
        SYMBOL_MAP.put("RUB", "RUB");
        SYMBOL_MAP.put("ROUBLE", "RUB");
        SYMBOL_MAP.put("RUBLE", "RUB");
        SYMBOL_MAP.put("IQD", "IQD");
    }

    @Override
    public String getSupportedType() {
        return TYPE;
    }

    @Override
    public String normalize(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }

        String cleaned = rawValue.trim();
        String upper = cleaned.toUpperCase();

        String match = SYMBOL_MAP.get(upper);
        if (match != null) {
            return match;
        }

        if (cleaned.length() == 3) {
            return upper;
        }

        return cleaned;
    }
}

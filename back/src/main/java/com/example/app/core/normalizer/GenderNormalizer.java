package com.example.app.core.normalizer;

import java.util.Map;

/**
 * Normalisation du genre vers le vocabulaire FTM ({@code male}, {@code female}) à partir des civilités
 * et abréviations courantes (« Madame », « M. », « Herr », « F »...). Valeur inconnue : rien n'est publié.
 */
public class GenderNormalizer implements ValueNormalizer {

    public static final String TYPE = "gender";

    private static final Map<String, String> VALUES = Map.ofEntries(
            Map.entry("male", "male"), Map.entry("m", "male"), Map.entry("m.", "male"),
            Map.entry("monsieur", "male"), Map.entry("mr", "male"), Map.entry("mr.", "male"),
            Map.entry("homme", "male"), Map.entry("herr", "male"), Map.entry("h", "male"),
            Map.entry("female", "female"), Map.entry("f", "female"), Map.entry("madame", "female"),
            Map.entry("mme", "female"), Map.entry("mme.", "female"), Map.entry("mrs", "female"),
            Map.entry("ms", "female"), Map.entry("femme", "female"), Map.entry("frau", "female"));

    @Override
    public String getSupportedType() {
        return TYPE;
    }

    @Override
    public String normalize(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        return VALUES.get(rawValue.trim().toLowerCase());
    }
}

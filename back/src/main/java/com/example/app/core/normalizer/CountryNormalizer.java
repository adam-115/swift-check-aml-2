package com.example.app.core.normalizer;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Normaliseur pour les pays et juridictions (ISO 3166-1 alpha-2 en majuscules).
 */
@Component
public class CountryNormalizer implements ValueNormalizer {

    public static final String TYPE = "country";

    private static final Map<String, String> CODE_MAP = new HashMap<>();

    static {
        // 1. Initialisation automatique depuis la JVM pour tous les pays ISO
        for (String alpha2 : Locale.getISOCountries()) {
            CODE_MAP.put(alpha2.toUpperCase(), alpha2.toUpperCase());

            Locale locale = Locale.of("", alpha2);
            try {
                String alpha3 = locale.getISO3Country();
                if (alpha3 != null && !alpha3.isEmpty()) {
                    CODE_MAP.put(alpha3.toUpperCase(), alpha2.toUpperCase());
                }
            } catch (Exception ignored) {
            }

            // Noms usuels anglais et français
            String englishName = locale.getDisplayCountry(Locale.ENGLISH);
            if (englishName != null && !englishName.isEmpty()) {
                CODE_MAP.put(englishName.toLowerCase().trim(), alpha2.toUpperCase());
            }

            String frenchName = locale.getDisplayCountry(Locale.FRENCH);
            if (frenchName != null && !frenchName.isEmpty()) {
                CODE_MAP.put(frenchName.toLowerCase().trim(), alpha2.toUpperCase());
            }
        }

        // 2. Alias et variantes courantes dans les listes de sanctions
        addAlias("russia", "RU");
        addAlias("russian federation", "RU");
        addAlias("fédération de russie", "RU");
        addAlias("usa", "US");
        addAlias("united states", "US");
        addAlias("united states of america", "US");
        addAlias("états-unis", "US");
        addAlias("uk", "GB");
        addAlias("great britain", "GB");
        addAlias("united kingdom", "GB");
        addAlias("royaume-uni", "GB");
        addAlias("iran", "IR");
        addAlias("iran, islamic republic of", "IR");
        addAlias("republic of iraq", "IQ");
        addAlias("syria", "SY");
        addAlias("syrian arab republic", "SY");
        addAlias("north korea", "KP");
        addAlias("dprk", "KP");
        addAlias("korea, democratic people's republic of", "KP");
        addAlias("south korea", "KR");
        addAlias("korea, republic of", "KR");
        addAlias("uae", "AE");
        addAlias("united arab emirates", "AE");
        addAlias("belarus", "BY");
        addAlias("biélorussie", "BY");
        addAlias("myanmar", "MM");
        addAlias("burma", "MM");
    }

    private static void addAlias(String name, String alpha2) {
        CODE_MAP.put(name.toLowerCase().trim(), alpha2.toUpperCase());
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

        // 1. Déjà un code alpha-2 valide (2 lettres majuscules)
        if (cleaned.length() == 2) {
            String upper = cleaned.toUpperCase();
            if (CODE_MAP.containsKey(upper)) {
                return CODE_MAP.get(upper);
            }
            return upper;
        }

        // 2. Recherche par code alpha-3 ou nom complet
        String match = CODE_MAP.get(cleaned.toUpperCase());
        if (match != null) {
            return match;
        }

        match = CODE_MAP.get(cleaned.toLowerCase());
        if (match != null) {
            return match;
        }

        return cleaned;
    }
}

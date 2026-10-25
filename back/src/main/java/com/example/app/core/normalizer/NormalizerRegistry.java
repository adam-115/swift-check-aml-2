package com.example.app.core.normalizer;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Registre centralisé pour la normalisation des valeurs selon leur type sémantique FTM.
 */
@Component
public class NormalizerRegistry {

    private final Map<String, ValueNormalizer> normalizers = new HashMap<>();

    public NormalizerRegistry() {
        // Enregistrement des normaliseurs standards par défaut
        register(new EntityIdNormalizer());
        register(new DateNormalizer());
        register(new CountryNormalizer());
        register(new CurrencyNormalizer());
    }

    public NormalizerRegistry(Collection<ValueNormalizer> customNormalizers) {
        this();
        if (customNormalizers != null) {
            for (ValueNormalizer normalizer : customNormalizers) {
                register(normalizer);
            }
        }
    }

    public void register(ValueNormalizer normalizer) {
        if (normalizer != null && normalizer.getSupportedType() != null) {
            normalizers.put(normalizer.getSupportedType().toLowerCase().trim(), normalizer);
        }
    }

    /**
     * Normalise une valeur brute selon son type sémantique FTM.
     *
     * @param type     type sémantique ("date", "country", "currency", "entity", etc.)
     * @param rawValue valeur brute
     * @return valeur normalisée, ou la valeur d'origine si aucun type/normaliseur correspondant
     */
    public String normalize(String type, String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return rawValue;
        }

        if (type == null || type.trim().isEmpty()) {
            return rawValue.trim();
        }

        ValueNormalizer normalizer = normalizers.get(type.toLowerCase().trim());
        if (normalizer != null) {
            return normalizer.normalize(rawValue.trim());
        }

        return rawValue.trim();
    }
}

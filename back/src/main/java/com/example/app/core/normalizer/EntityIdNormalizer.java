package com.example.app.core.normalizer;

import com.example.app.core.HashUtils;
import org.springframework.stereotype.Component;

/**
 * Normaliseur pour les références d'entités (clés étrangères).
 * Hache l'identifiant cible en SHA-1 pour maintenir l'intégrité relationnelle du graphe FTM.
 */
@Component
public class EntityIdNormalizer implements ValueNormalizer {

    public static final String TYPE = "entity";

    @Override
    public String getSupportedType() {
        return TYPE;
    }

    @Override
    public String normalize(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        return HashUtils.sha1(rawValue.trim());
    }
}

package com.example.app.core.normalizer;

/**
 * Interface définissant un normaliseur de valeurs pour un type sémantique FollowTheMoney
 * (ex: "date", "country", "currency", "entity").
 */
public interface ValueNormalizer {

    /**
     * Type sémantique FTM pris en charge (ex: "date", "country", "currency", "entity").
     */
    String getSupportedType();

    /**
     * Standardise et nettoie la valeur brute extraite de la source.
     *
     * @param rawValue la valeur brute issue de la source
     * @return la valeur normalisée selon les standards ISO / FTM
     */
    String normalize(String rawValue);
}

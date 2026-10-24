package com.example.app.core;

import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.StandardCharsets;

/**
 * Classe utilitaire pour le hachage et la signature (SHA-1).
 */
public final class HashUtils {

    private HashUtils() {
        // Empêche l'instanciation de la classe utilitaire
    }

    /**
     * Calcule l'empreinte / signature SHA-1 hexadécimale d'une chaîne de caractères.
     *
     * @param input la chaîne de caractères en entrée
     * @return l'empreinte SHA-1 sous forme hexadécimale (40 caractères), ou null si input est null
     */
    public static String sha1(String input) {
        if (input == null) {
            return null;
        }
        return DigestUtils.sha1Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Alias de calcul SHA-1.
     */
    public static String computeSha1(String input) {
        return sha1(input);
    }

    /**
     * Alias sémantique pour générer une signature SHA-1.
     */
    public static String sign(String input) {
        return sha1(input);
    }
}

package com.example.app.api;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Corps JSON de toute réponse d'erreur de l'API, lu par l'interface pour afficher {@link #message}.
 *
 * @param status  code HTTP
 * @param error   libellé court du code HTTP
 * @param message explication pour l'utilisateur
 * @param path    adresse appelée
 * @param fields  erreurs par champ (validation), vide sinon
 */
public record ApiError(LocalDateTime timestamp,
                       int status,
                       String error,
                       String message,
                       String path,
                       Map<String, String> fields) {
}

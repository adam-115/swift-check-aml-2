package com.example.app.api;

/**
 * Action impossible dans l'état actuel (source désactivée, exécution déjà en cours) : réponse HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

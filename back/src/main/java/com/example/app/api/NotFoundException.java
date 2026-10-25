package com.example.app.api;

/**
 * Élément demandé introuvable : réponse HTTP 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String what, Object id) {
        return new NotFoundException(what + " introuvable : " + id);
    }
}

package com.example.app.ingestion;

import lombok.Getter;

/**
 * Erreur pendant la génération FTM d'une source, rattachée à l'étape qui a échoué.
 * Le message est écrit pour l'utilisateur : il est affiché tel quel dans le détail de l'exécution.
 */
@Getter
public class IngestionException extends RuntimeException {

    /** Étapes du pipeline, dans l'ordre. */
    public enum Step {
        /** Lecture de la configuration (chemins, options, fichier de mapping). */
        CONFIGURATION,
        /** Téléchargement ou copie du fichier source. */
        DOWNLOAD,
        /** Conversion du fichier source en enregistrements JSON. */
        CONVERSION,
        /** Application du fichier de mapping. */
        MAPPING,
        /** Écriture du fichier NDJSON. */
        EXPORT
    }

    private final Step step;

    public IngestionException(Step step, String message) {
        super(message);
        this.step = step;
    }

    public IngestionException(Step step, String message, Throwable cause) {
        super(message, cause);
        this.step = step;
    }
}

package com.example.app.core;

/**
 * État d'une exécution de source.
 */
public enum ExecutionStatus {
    /** Génération en cours. */
    RUNNING,
    /** Fichier FTM produit. */
    SUCCESS,
    /** Échec : téléchargement, conversion, mapping ou écriture. */
    FAILED
}

package com.example.app.execution;

/**
 * État d'une exécution de source.
 */
public enum ExecutionStatus {
    /** En cours. */
    RUNNING,
    /** Fichier FTM généré. */
    SUCCESS,
    /** Erreur : rien n'a été généré, l'étape et le message sont enregistrés. */
    FAILED
}

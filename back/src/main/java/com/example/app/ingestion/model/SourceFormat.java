package com.example.app.ingestion.model;

/**
 * Supported raw input data formats for dynamic ingestion.
 */
public enum SourceFormat {
    XML,
    JSON,
    CSV,
    XLSX,
    HTML,
    /** Document PDF (pas encore de convertisseur dans ce pipeline). */
    PDF
}

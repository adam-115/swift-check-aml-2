package com.example.app.core;

/**
 * Type de source lue (T1 à T7) : dit comment la source est publiée, donc à quoi s'attendre en
 * maintenance. Le format technique du fichier est porté par
 * {@link com.example.app.ingestion.model.SourceFormat}.
 */
public enum ParserType {
    /** T1 : fichier open data (CSV, XML, JSON). */
    OPEN_DATA_FILE,
    /** T2 : API REST ou OData. */
    API,
    /** T3 : point d'accès SPARQL (Wikidata, Legilux). */
    SPARQL,
    /** T4 : journal officiel (nominations et cessations de fonctions). */
    OFFICIAL_GAZETTE,
    /** T5 : page web officielle. */
    WEB_PAGE,
    /** T6 : document PDF. */
    PDF_DOCUMENT,
    /** T7 : liste PEP officielle ou registre de déclarations d'intérêts. */
    DECLARATION_REGISTER
}

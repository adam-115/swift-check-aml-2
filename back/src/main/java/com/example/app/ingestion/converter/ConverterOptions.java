package com.example.app.ingestion.converter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Options de conversion d'une source vers JSON, stockées en JSON dans
 * {@code DataSourceConfig.converterOptions}. Toutes sont facultatives.
 *
 * <pre>
 * {
 *   "encoding": "windows-1252",
 *   "html": {
 *     "recordSelector": "div.member",
 *     "fields": { "name": "h3", "role": ".function", "url": "a@href", "roles": "all:li" }
 *   }
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConverterOptions {

    public static final ConverterOptions DEFAULT = new ConverterOptions();

    /** Encodage du fichier texte (CSV, HTML) ; UTF-8 par défaut. */
    private String encoding;

    /** Extraction d'une page HTML par sélecteurs CSS. */
    private HtmlExtraction html;

    /**
     * Adresse d'origine du fichier, renseignée à l'exécution : les liens relatifs d'une page HTML
     * ({@code a@href}) sont rendus absolus par rapport à elle.
     */
    private String baseUrl;

    /** Copie des options avec l'adresse d'origine du fichier. */
    public ConverterOptions withBaseUrl(String url) {
        return new ConverterOptions(encoding, html, url);
    }

    public Charset charset() {
        return encoding == null || encoding.isBlank() ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    }

    /**
     * Un enregistrement par élément trouvé par {@link #recordSelector} ; chaque champ est lu dans cet élément.
     *
     * <p>Syntaxe d'un champ : {@code sélecteur} (texte), {@code sélecteur@attribut}, {@code @attribut}
     * (attribut de l'élément lui-même), {@code :self} (texte de l'élément), {@code :ownText} (texte propre de
     * l'élément, sans ses enfants), et le préfixe {@code all:} pour garder toutes les valeurs dans une liste.
     * {@code closest(css) sélecteur} lit dans l'ancêtre le plus proche, {@code prev(css) sélecteur} dans
     * l'élément précédent le plus proche (ex. {@code prev(h2)} : le titre de la section), {@code next(css)
     * sélecteur} dans l'élément voisin suivant le plus proche.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HtmlExtraction {

        private String recordSelector;

        @Builder.Default
        private Map<String, String> fields = new LinkedHashMap<>();
    }
}

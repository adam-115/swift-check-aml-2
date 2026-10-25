package com.example.app.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Champ calculé ajouté à un enregistrement source avant le mapping FTM.
 *
 * <p>Deux façons de le calculer :
 * <ul>
 *   <li>{@link #template} : texte où chaque {@code {chemin}} est remplacé par la valeur du chemin JsonPath
 *       ({@code {$.pph_nom}}) ou du champ ({@code {pph_nom}}) ; sans accolades, c'est une constante ;</li>
 *   <li>{@link #from} : valeur d'un chemin, éventuellement transformée par {@link #regex}
 *       (groupe {@link #group}, ou remplacement par {@link #replacement}).</li>
 * </ul>
 * Le résultat peut ensuite être mis en minuscules ou réduit en identifiant ({@link #slug}).
 * Un champ vide n'est pas ajouté, sauf si {@link #defaultValue} est renseigné.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DerivedField {

    /** Nom du champ ajouté à l'enregistrement, lisible ensuite avec {@code $.<name>}. */
    private String name;

    /** Gabarit avec des {@code {chemin}} ; exclusif avec {@link #from}. */
    private String template;

    /** Avec {@link #template} : le champ reste vide si l'une des valeurs du gabarit est vide. */
    private boolean requireAll;

    /** Chemin JsonPath ou nom du champ source. */
    private String from;

    /**
     * Chemin du champ qui contient l'adresse d'un document lié : le champ reçoit le texte de ce document
     * (téléchargé une fois puis gardé en cache). Ex. la liste des actes d'un journal officiel, puis le texte
     * de chaque acte.
     */
    private String fetch;

    /**
     * Valeur gardée quand {@link #from} en trouve plusieurs : {@code first} (par défaut), {@code last},
     * {@code max} ou {@code min} (ordre alphabétique, donc chronologique pour des dates ISO).
     */
    private String pick;

    /** Expression régulière appliquée à la valeur ; sans correspondance, le champ est vide. */
    private String regex;

    /** Groupe de {@link #regex} à garder (1 par défaut). */
    private Integer group;

    /** Si renseigné, {@link #regex} sert à remplacer (replaceAll) au lieu d'extraire. */
    private String replacement;

    /** Met le résultat en minuscules. */
    private boolean lowercase;

    /** Réduit le résultat en identifiant : minuscules, sans accents, mots séparés par des tirets. */
    private boolean slug;

    /** Valeur utilisée quand le calcul donne un résultat vide. */
    private String defaultValue;

    /**
     * Si renseigné, le résultat est découpé selon cette expression régulière et le champ devient une liste
     * (ex. {@code "role.pep;gov.national"} avec {@code ";"}) : chaque morceau donne une valeur FTM.
     */
    private String split;
}

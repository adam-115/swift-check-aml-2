package com.example.app.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Découpe un enregistrement en plusieurs : une copie par correspondance de {@link #regex} dans le champ
 * {@link #from}. Sert quand une phrase cite plusieurs personnes (« Madame A, Monsieur B et Madame C,
 * conseillers »). La copie reçoit {@code <as>} (correspondance entière) et {@code <as>_1}, {@code <as>_2}...
 * (groupes). Un enregistrement sans correspondance est écarté.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordExplode {

    /** Chemin JsonPath ou nom du champ découpé. */
    private String from;

    /** Expression régulière ; chaque correspondance donne un enregistrement. */
    private String regex;

    /**
     * Autre mode : chemins JsonPath de tableaux d'objets (ex. {@code $.membresConseil[*]}). Chaque élément
     * donne une copie où {@code <as>} est l'objet (lisible avec {@code $.<as>.nom}) et {@code <as>_path}
     * le chemin dont il vient.
     */
    private java.util.List<String> paths;

    /** Nom du champ qui reçoit la correspondance. */
    private String as;

    /**
     * Facultatif : nom d'un champ calculé après lequel découper (ex. le texte d'un document téléchargé).
     * Les champs calculés jusqu'à celui-ci inclus sont faits avant le découpage, les suivants après.
     * Sans valeur, le découpage a lieu avant tous les champs calculés.
     */
    private String after;
}

package com.example.app.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Condition qu'un enregistrement source doit remplir pour être mappé.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordFilter {

    public enum Operator {
        /** La valeur existe et n'est pas vide. */
        NOT_EMPTY,
        /** La valeur est égale à {@code value} (sans tenir compte de la casse). */
        EQUALS,
        /** La valeur est différente de {@code value}, ou absente. */
        NOT_EQUALS,
        /** La valeur correspond à l'expression régulière {@code value} (recherche, insensible à la casse). */
        MATCHES,
        /** La valeur ne correspond pas à l'expression régulière {@code value}, ou est absente. */
        NOT_MATCHES,
        /**
         * La valeur est une date d'il y a moins de {@code value} années, ou est absente (fonction en cours).
         * Sert à garder les anciens titulaires pendant la durée de vigilance.
         */
        WITHIN_YEARS
    }

    /** Chemin JsonPath ou nom du champ testé. */
    private String path;

    private Operator operator;

    private String value;
}

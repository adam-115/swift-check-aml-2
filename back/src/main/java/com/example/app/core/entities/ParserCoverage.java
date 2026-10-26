package com.example.app.core.entities;

import com.example.app.core.Completeness;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fonction PEP couverte par une source de données, et à quel point.
 * Ex. « Membre du Conseil d'État » : conseil-etat.public.lu (complète, en fonction seulement)
 * et Legilux (partielle, avec les anciens membres). Base de la page « Couverture PEP ».
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "parser_coverage",
        uniqueConstraints = @UniqueConstraint(name = "uk_parser_coverage",
                columnNames = {"data_source_config_id", "pep_function_id"}))
public class ParserCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "data_source_config_id", nullable = false)
    private DataSourceConfig dataSourceConfig;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pep_function_id", nullable = false)
    private PepFunction pepFunction;

    @Enumerated(EnumType.STRING)
    @Column(name = "completeness", nullable = false, length = 20)
    private Completeness completeness;

    /** Vrai si la source donne aussi les anciens titulaires (vigilance après la fin des fonctions). */
    @Column(name = "includes_former", nullable = false)
    private boolean includesFormer;
}

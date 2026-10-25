package com.example.app.core.pep;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fonction précise d'une catégorie PEP, ex. « Député » à la Chambre des Députés.
 * Les sources qui la couvrent sont déclarées dans {@link com.example.app.core.ParserCoverage}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pep_function")
public class PepFunction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private PepCategory category;

    @Column(name = "label", nullable = false, length = 500)
    private String label;

    /** Institution où s'exerce la fonction, ex. « Chambre des Députés ». */
    @Column(name = "institution")
    private String institution;

    /** Nombre approximatif de titulaires en fonction ; {@code null} si inconnu. */
    @Column(name = "expected_holders")
    private Integer expectedHolders;
}

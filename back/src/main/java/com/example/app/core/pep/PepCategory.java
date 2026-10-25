package com.example.app.core.pep;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Catégorie de fonctions PEP d'un pays, ex. {@code LU-b} « Parlementaires et Conseil d'État ».
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pep_category")
public class PepCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "country_iso", nullable = false)
    private Country country;

    /** Code unique : pays + lettre de la liste officielle, ex. {@code LU-b}, ou {@code LU-MUN}. */
    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "label", nullable = false, length = 500)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private PepCategoryKind kind;

    /** Classe de risque : 1 = la plus élevée, 4 = la plus faible. */
    @Column(name = "risk_class", nullable = false)
    private int riskClass;

    /** Ordre d'affichage dans la page « Couverture PEP ». */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Texte qui définit la catégorie, ex. « Directive (UE) 2015/849, art. 3, point 9 b) ». */
    @Column(name = "legal_reference", length = 500)
    private String legalReference;
}

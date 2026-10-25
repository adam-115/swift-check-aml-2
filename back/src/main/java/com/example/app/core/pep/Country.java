package com.example.app.core.pep;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Pays couvert (UE + Suisse). Donnée de référence remplie par Liquibase.
 * {@link #retentionMonths} est la durée minimale pendant laquelle une personne reste PEP après la fin
 * de ses fonctions ; c'est la valeur proposée par défaut pour les sources PEP du pays.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "country")
public class Country {

    /** Code ISO 3166-1 alpha-2 en majuscules, ex. {@code LU}. */
    @Id
    @Column(name = "iso", length = 2)
    private String iso;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "eu_member", nullable = false)
    private boolean euMember;

    /** Texte de loi qui définit les PEP dans ce pays. */
    @Column(name = "legal_basis", length = 500)
    private String legalBasis;

    @Column(name = "retention_months", nullable = false)
    private int retentionMonths;

    /**
     * Liste officielle des fonctions PEP déclarée par le pays à la Commission (art. 20 bis de la
     * directive 2015/849), ex. {@code JO C/2023/724}.
     */
    @Column(name = "official_list_reference")
    private String officialListReference;

    @Column(name = "official_list_date")
    private LocalDate officialListDate;

    @Column(name = "official_list_url", length = 1024)
    private String officialListUrl;
}

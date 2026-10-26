package com.example.app.core.repository;

import com.example.app.core.entities.PepCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA des catégories de fonctions PEP.
 */
@Repository
public interface PepCategoryRepository extends JpaRepository<PepCategory, Long> {

    Optional<PepCategory> findByCode(String code);

    List<PepCategory> findByCountryIsoOrderBySortOrderAsc(String countryIso);

    boolean existsByCountryIso(String countryIso);
}

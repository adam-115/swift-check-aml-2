package com.example.app.core.repository;

import com.example.app.core.entities.ParserCoverage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA de la couverture PEP des sources.
 */
@Repository
public interface ParserCoverageRepository extends JpaRepository<ParserCoverage, Long> {

    List<ParserCoverage> findByDataSourceConfigId(Long dataSourceConfigId);

    List<ParserCoverage> findByPepFunctionId(Long pepFunctionId);

    List<ParserCoverage> findByPepFunctionCategoryCountryIso(String countryIso);

    long countByPepFunctionId(Long pepFunctionId);
}

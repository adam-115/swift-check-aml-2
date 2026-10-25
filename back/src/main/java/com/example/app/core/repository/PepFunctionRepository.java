package com.example.app.core.repository;

import com.example.app.core.pep.PepFunction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository Spring Data JPA des fonctions PEP.
 */
@Repository
public interface PepFunctionRepository extends JpaRepository<PepFunction, Long> {

    List<PepFunction> findByCategoryCode(String categoryCode);

    List<PepFunction> findByCategoryCountryIso(String countryIso);

    Optional<PepFunction> findByCategoryCodeAndLabel(String categoryCode, String label);
}

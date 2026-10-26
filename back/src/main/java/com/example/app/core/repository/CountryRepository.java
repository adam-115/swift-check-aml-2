package com.example.app.core.repository;

import com.example.app.core.entities.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA des pays couverts.
 */
@Repository
public interface CountryRepository extends JpaRepository<Country, String> {

    List<Country> findAllByOrderByNameAsc();
}

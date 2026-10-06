package com.applyflow.repository;

import com.applyflow.entity.Company;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/** Deleting unused companies (with their contacts) is done by {@code CascadeDeleter}. */
public interface CompanyRepository extends MongoRepository<Company, Long> {

    Optional<Company> findByNormalizedName(String normalizedName);

    List<Company> findByDomainIgnoreCase(String domain);

    List<Company> findAllByOrderByNameAsc();
}

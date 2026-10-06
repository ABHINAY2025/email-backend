package com.applyflow.repository;

import com.applyflow.entity.Company;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/** Deleting unused companies (with their contacts) is done by {@code CascadeDeleter}. Scoped by owner. */
public interface CompanyRepository extends MongoRepository<Company, Long> {

    Optional<Company> findByIdAndUserId(Long id, Long userId);

    Optional<Company> findByUserIdAndNormalizedName(Long userId, String normalizedName);

    List<Company> findByUserIdAndDomainIgnoreCase(Long userId, String domain);

    List<Company> findByUserIdOrderByNameAsc(Long userId);

    List<Company> findByUserId(Long userId);
}

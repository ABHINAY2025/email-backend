package com.applyflow.repository;

import com.applyflow.entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /** Emails are stored lower-case. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /** The legacy/env-bootstrapped admin (the only kind of user without an email). */
    Optional<User> findFirstByEmailIsNullOrderByIdAsc();
}

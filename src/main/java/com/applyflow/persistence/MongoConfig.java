package com.applyflow.persistence;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

import java.util.concurrent.TimeUnit;

@Configuration
public class MongoConfig {

    /**
     * Multi-document transactions (MongoDB replica set / Atlas). Backs {@code @Transactional} and the
     * {@code TransactionTemplate}s; the repositories and {@code MongoTemplate} join the transaction automatically.
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    /** Fail fast (instead of the 30s driver default) when the database is unreachable. */
    @Bean
    public MongoClientSettingsBuilderCustomizer applyFlowMongoTimeouts() {
        return builder -> builder
                .applyToClusterSettings(c -> c.serverSelectionTimeout(10, TimeUnit.SECONDS))
                .applyToSocketSettings(s -> s.connectTimeout(10, TimeUnit.SECONDS));
    }
}

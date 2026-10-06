package com.applyflow.service;

import com.applyflow.config.AppProperties;
import com.applyflow.dto.CommonDtos.HealthResponse;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final MongoTemplate mongoTemplate;
    private final AppProperties props;

    public HealthService(MongoTemplate mongoTemplate, AppProperties props) {
        this.mongoTemplate = mongoTemplate;
        this.props = props;
    }

    public HealthResponse health() {
        String db;
        try {
            // {ping: 1} against the application's own database.
            Document result = mongoTemplate.executeCommand(new Document("ping", 1));
            db = result != null && result.get("ok") instanceof Number ok && ok.doubleValue() == 1.0 ? "UP" : "DOWN";
        } catch (RuntimeException e) {
            db = "DOWN";
        }
        return new HealthResponse("UP", db, props.version());
    }
}

package com.applyflow.persistence;

import com.applyflow.entity.ApplicationEventEntity;
import com.applyflow.entity.Company;
import com.applyflow.entity.Contact;
import com.applyflow.entity.EmailAccount;
import com.applyflow.entity.EmailClassificationLog;
import com.applyflow.entity.EmailMatchSuggestion;
import com.applyflow.entity.EmailMessage;
import com.applyflow.entity.JobApplication;
import com.applyflow.entity.Note;
import com.applyflow.entity.Notification;
import com.applyflow.entity.StatusHistory;
import com.applyflow.entity.SyncJob;
import com.applyflow.entity.User;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Creates every collection and index up front (after all beans are created, before the web server starts and before
 * any runner touches the data). Collections must exist before multi-document transactions write to them, and index
 * creation is not allowed inside a transaction. Also makes sure every id sequence is ahead of existing documents.
 */
@Component
public class MongoSchemaInitializer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MongoSchemaInitializer.class);

    static final List<Class<? extends MongoEntity>> DOCUMENTS = List.of(User.class, EmailAccount.class,
            Company.class, Contact.class, JobApplication.class, EmailMessage.class, EmailClassificationLog.class,
            ApplicationEventEntity.class, StatusHistory.class, Note.class, Notification.class, SyncJob.class,
            EmailMatchSuggestion.class);

    private final MongoTemplate template;
    private final MongoMappingContext mappingContext;
    private final SequenceGenerator sequences;

    public MongoSchemaInitializer(MongoTemplate template, MongoMappingContext mappingContext,
                                  SequenceGenerator sequences) {
        this.template = template;
        this.mappingContext = mappingContext;
        this.sequences = sequences;
    }

    @Override
    public void afterSingletonsInstantiated() {
        log.info("Using MongoDB database '{}'", template.getDb().getName());
        Set<String> existing = template.getCollectionNames();
        if (!existing.contains(SequenceGenerator.COUNTERS)) {
            template.createCollection(SequenceGenerator.COUNTERS);
        }
        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);
        int created = 0;
        for (Class<? extends MongoEntity> type : DOCUMENTS) {
            String collection = template.getCollectionName(type);
            if (!existing.contains(collection)) {
                template.createCollection(collection);
                created++;
            }
            IndexOperations indexOps = template.indexOps(type);
            resolver.resolveIndexFor(type).forEach(indexOps::ensureIndex);
            syncSequence(collection);
        }
        log.info("MongoDB schema ready: {} collections ({} created), indexes ensured", DOCUMENTS.size(), created);
    }

    /** Keeps the id counter ahead of any documents that already exist (e.g. imported data). */
    private void syncSequence(String collection) {
        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "_id")).limit(1);
        q.fields().include("_id");
        Document top = template.findOne(q, Document.class, collection);
        if (top != null && top.get("_id") instanceof Number n) {
            sequences.ensureAtLeast(collection, n.longValue());
        }
    }
}

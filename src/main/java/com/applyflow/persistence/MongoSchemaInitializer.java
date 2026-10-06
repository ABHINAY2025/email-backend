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
import com.applyflow.security.UserBootstrap;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Prepares the database at startup (after all beans are created, before the web server starts, the scheduler runs
 * or any runner touches the data):
 * <ol>
 *   <li>creates every collection and index up front (collections must exist before multi-document transactions write
 *       to them, and index creation is not allowed inside a transaction) and keeps every id sequence ahead of
 *       existing documents;</li>
 *   <li>replaces the former global unique indexes by their per-user equivalents (the new index is created first, then
 *       the old one is dropped, so uniqueness is never unenforced);</li>
 *   <li>creates/updates the env-bootstrapped admin user;</li>
 *   <li>assigns every owned document without an owner (data from the single-user version) to the admin.</li>
 * </ol>
 * Every step is idempotent and safe to run on each start.
 */
@Component
public class MongoSchemaInitializer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MongoSchemaInitializer.class);

    static final List<Class<? extends MongoEntity>> DOCUMENTS = List.of(User.class, EmailAccount.class,
            Company.class, Contact.class, JobApplication.class, EmailMessage.class, EmailClassificationLog.class,
            ApplicationEventEntity.class, StatusHistory.class, Note.class, Notification.class, SyncJob.class,
            EmailMatchSuggestion.class);

    /** Single-user unique indexes superseded by per-user ones (see the entities' {@code @CompoundIndex}es). */
    static final Map<Class<? extends MongoEntity>, String> LEGACY_INDEXES = Map.of(
            EmailAccount.class, "uq_email_accounts_email",
            Company.class, "uq_companies_normalized_name",
            Contact.class, "uq_contacts_company_email");

    private final MongoTemplate template;
    private final MongoMappingContext mappingContext;
    private final SequenceGenerator sequences;
    private final UserBootstrap userBootstrap;

    public MongoSchemaInitializer(MongoTemplate template, MongoMappingContext mappingContext,
                                  SequenceGenerator sequences, UserBootstrap userBootstrap) {
        this.template = template;
        this.mappingContext = mappingContext;
        this.sequences = sequences;
        this.userBootstrap = userBootstrap;
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
            dropLegacyIndex(type, indexOps);
            syncSequence(collection);
        }
        log.info("MongoDB schema ready: {} collections ({} created), indexes ensured", DOCUMENTS.size(), created);

        Long adminId = userBootstrap.ensureAdmin().getId();
        assignOrphansTo(adminId);
    }

    private void dropLegacyIndex(Class<? extends MongoEntity> type, IndexOperations indexOps) {
        String legacy = LEGACY_INDEXES.get(type);
        if (legacy == null) {
            return;
        }
        boolean present = indexOps.getIndexInfo().stream().map(IndexInfo::getName).anyMatch(legacy::equals);
        if (present) {
            indexOps.dropIndex(legacy);
            log.info("Dropped legacy global unique index {} on {} (replaced by a per-user index)", legacy,
                    template.getCollectionName(type));
        }
    }

    /** Ownership migration: documents written before multi-user support belong to the admin. Logs counts only. */
    private void assignOrphansTo(Long adminId) {
        long total = 0;
        for (Class<? extends MongoEntity> type : DOCUMENTS) {
            if (!OwnedEntity.class.isAssignableFrom(type)) {
                continue;
            }
            // {userId: null} matches documents where the field is missing or null.
            long n = template.updateMulti(query(where("userId").is(null)), Update.update("userId", adminId), type)
                    .getModifiedCount();
            if (n > 0) {
                log.info("Ownership migration: assigned {} {} document(s) to the admin user", n,
                        template.getCollectionName(type));
            }
            total += n;
        }
        if (total > 0) {
            log.info("Ownership migration complete: {} document(s) assigned to the admin user", total);
        }
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

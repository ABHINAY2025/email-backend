package com.applyflow.persistence;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.stereotype.Component;

/**
 * Numeric id sequences, one per collection, kept in the {@code counters} collection
 * ({@code {_id: "<collection>", seq: <last id>}}) and advanced atomically with {@code findAndModify $inc} (upsert).
 * <p>
 * The counter is deliberately updated <em>outside</em> any running Mongo transaction (it uses the plain database from
 * the factory, which is never bound to a client session): concurrent transactions therefore never write-conflict on
 * the shared counter document, and like SQL sequences an id is never handed out twice (a rolled-back insert just
 * leaves a gap).
 */
@Component
public class SequenceGenerator {

    public static final String COUNTERS = "counters";

    private final MongoDatabaseFactory databaseFactory;

    public SequenceGenerator(MongoDatabaseFactory databaseFactory) {
        this.databaseFactory = databaseFactory;
    }

    public long next(String collection) {
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().upsert(true)
                .returnDocument(ReturnDocument.AFTER);
        for (int attempt = 0; ; attempt++) {
            try {
                Document d = counters().findOneAndUpdate(Filters.eq("_id", collection), Updates.inc("seq", 1L),
                        options);
                if (d == null || !(d.get("seq") instanceof Number n)) {
                    throw new IllegalStateException("Could not allocate an id for " + collection);
                }
                return n.longValue();
            } catch (MongoWriteException e) {
                // Two first-time upserts can race on the same _id; the loser simply retries.
                if (attempt >= 2 || e.getError().getCode() != 11000) {
                    throw e;
                }
            }
        }
    }

    /** Makes sure the next id handed out is greater than {@code value} (used for pre-existing documents). */
    public void ensureAtLeast(String collection, long value) {
        counters().updateOne(Filters.eq("_id", collection), Updates.max("seq", value),
                new UpdateOptions().upsert(true));
    }

    private MongoCollection<Document> counters() {
        return databaseFactory.getMongoDatabase().getCollection(COUNTERS);
    }
}

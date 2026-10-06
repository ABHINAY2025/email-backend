package com.applyflow.persistence;

import com.applyflow.security.CurrentUser;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;

/**
 * Assigns sequence ids to new documents, stamps the owner of owned documents and runs their
 * {@link MongoEntity#beforeSave(boolean)} hook (timestamps) right before they are converted and written (insert or
 * save).
 */
@Component
public class MongoLifecycleListener extends AbstractMongoEventListener<Object> {

    private final SequenceGenerator sequences;

    public MongoLifecycleListener(SequenceGenerator sequences) {
        this.sequences = sequences;
    }

    @Override
    public void onBeforeConvert(BeforeConvertEvent<Object> event) {
        if (!(event.getSource() instanceof MongoEntity entity)) {
            return;
        }
        if (entity instanceof OwnedEntity owned && owned.getUserId() == null) {
            Long owner = CurrentUser.idOrNull();
            if (owner == null) {
                // Fail closed: an owner-less document would be invisible to everyone (or leak on a bad query).
                throw new IllegalStateException("Refusing to save " + entity.getClass().getSimpleName()
                        + " without an owner");
            }
            owned.setUserId(owner);
        }
        boolean isNew = entity.getId() == null;
        if (isNew) {
            entity.setId(sequences.next(event.getCollectionName()));
        }
        entity.beforeSave(isNew);
    }
}

package com.applyflow.persistence;

import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;

/**
 * Assigns sequence ids to new documents and runs their {@link MongoEntity#beforeSave(boolean)} hook (timestamps)
 * right before they are converted and written (insert or save).
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
        boolean isNew = entity.getId() == null;
        if (isNew) {
            entity.setId(sequences.next(event.getCollectionName()));
        }
        entity.beforeSave(isNew);
    }
}

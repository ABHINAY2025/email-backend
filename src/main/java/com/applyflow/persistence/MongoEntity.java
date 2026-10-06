package com.applyflow.persistence;

/**
 * A MongoDB document with a numeric {@code Long} id drawn from a per-collection sequence (see
 * {@link SequenceGenerator}). {@link #beforeSave(boolean)} replaces the former JPA {@code @PrePersist}/{@code @PreUpdate}
 * callbacks and is invoked right before the document is written.
 */
public interface MongoEntity {

    Long getId();

    void setId(Long id);

    /** @param isNew true when the document is being inserted for the first time (id was just assigned) */
    default void beforeSave(boolean isNew) {
    }
}

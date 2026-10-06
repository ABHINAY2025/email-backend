package com.applyflow.persistence;

/**
 * A document owned by exactly one user ({@code userId} = {@code users._id}). Every read and write of owned documents
 * is scoped by the owner. New documents without an owner are stamped by {@link MongoLifecycleListener} from the
 * current user (request or background scope); saving one without any owner fails.
 */
public interface OwnedEntity extends MongoEntity {

    Long getUserId();

    void setUserId(Long userId);

    /** Adopts the owner of a parent document when this document has none yet. */
    default void adoptOwner(OwnedEntity parent) {
        if (getUserId() == null && parent != null && parent.getUserId() != null) {
            setUserId(parent.getUserId());
        }
    }
}

package org.example.analyser.model;

public enum PersistenceFindingType {

    /**
     * A repository read call made from inside a loop - the
     * classic shape of an N+1 query bug (one query per loop
     * iteration instead of one batched query).
     */
    N_PLUS_ONE_QUERY_RISK,

    /**
     * An entity/table read or written by an unusually large
     * number of distinct classes - a shared-data coupling point
     * that makes the entity's schema hard to change safely.
     */
    SHARED_ENTITY_HOTSPOT
}

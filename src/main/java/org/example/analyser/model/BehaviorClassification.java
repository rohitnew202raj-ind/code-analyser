package org.example.analyser.model;

public enum BehaviorClassification {

    /**
     * Every database operation reachable from this entry point
     * is a read (or nothing was found at all) - no write, no
     * entity field mutation anywhere in the reachable flow.
     */
    READ_ONLY,

    /**
     * At least one write-shaped database operation or entity
     * field mutation is reachable from this entry point.
     */
    MUTATING
}

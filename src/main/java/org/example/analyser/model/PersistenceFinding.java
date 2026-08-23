package org.example.analyser.model;

import java.util.List;

/**
 * A single persistence-quality signal surfaced by one of the
 * persistence-intelligence analyzers (N+1 query risk, shared
 * entity hotspots) - a query over CRUD data the rest of the
 * analyzer already computed, not a new resolution step of its
 * own.
 */
public class PersistenceFinding {

    private PersistenceFindingType type;

    /*
     * The class(es) this finding is about - the calling class
     * for N_PLUS_ONE_QUERY_RISK, every distinct class touching
     * the entity for SHARED_ENTITY_HOTSPOT.
     */
    private List<String> classes;

    private String description;

    public PersistenceFinding() {
    }

    public PersistenceFinding(
            PersistenceFindingType type,
            List<String> classes,
            String description) {

        this.type = type;
        this.classes = classes;
        this.description = description;
    }

    public PersistenceFindingType getType() {
        return type;
    }

    public void setType(PersistenceFindingType type) {
        this.type = type;
    }

    public List<String> getClasses() {
        return classes;
    }

    public void setClasses(List<String> classes) {
        this.classes = classes;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

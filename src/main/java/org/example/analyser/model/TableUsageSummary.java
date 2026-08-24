package org.example.analyser.model;

import java.util.List;

/**
 * One table/entity and every class observed performing a CRUD
 * operation against it, ranked by how many distinct classes touch
 * it - an open ranking rather than {@code SharedEntityHotspotAnalyzer}'s
 * threshold-filtered finding, so the full picture is visible, not
 * just the entries that cross a fixed cutoff.
 */
public class TableUsageSummary {

    private String tableName;
    private String entityClass;
    private List<String> touchingClasses;

    public TableUsageSummary() {
    }

    public TableUsageSummary(
            String tableName,
            String entityClass,
            List<String> touchingClasses) {

        this.tableName = tableName;
        this.entityClass = entityClass;
        this.touchingClasses = touchingClasses;
    }

    public String getTableName() {
        return tableName;
    }

    public String getEntityClass() {
        return entityClass;
    }

    public List<String> getTouchingClasses() {
        return touchingClasses;
    }

    public int getTouchingClassCount() {
        return touchingClasses.size();
    }
}

package org.example.analyser.model;

import java.util.List;

/**
 * One method whose own body performs CRUD operations against 2 or
 * more distinct tables - a static proxy for "this is one
 * transaction touching multiple tables," the signal that splitting
 * the domains involved apart will need a saga/compensating-action
 * strategy instead of a single local database transaction.
 *
 * SCOPE (documented, not a full transaction tracer): this looks
 * only at a method's own direct repository calls, not at
 * {@code @Transactional} propagation into methods it calls in
 * turn, and doesn't distinguish an explicitly {@code @Transactional}
 * method from one that merely happens to touch multiple tables
 * without any transaction boundary at all - both are flagged the
 * same way, since the multi-table fact is what matters for a
 * split, regardless of whether a transaction annotation is
 * present.
 */
public class MultiTableTransaction {

    private String className;
    private String methodName;
    private String domain;
    private List<String> tables;
    private List<String> entities;
    private boolean spansMultipleDomains;

    public MultiTableTransaction() {
    }

    public MultiTableTransaction(
            String className,
            String methodName,
            String domain,
            List<String> tables,
            List<String> entities,
            boolean spansMultipleDomains) {

        this.className = className;
        this.methodName = methodName;
        this.domain = domain;
        this.tables = tables;
        this.entities = entities;
        this.spansMultipleDomains = spansMultipleDomains;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getDomain() {
        return domain;
    }

    public List<String> getTables() {
        return tables;
    }

    public List<String> getEntities() {
        return entities;
    }

    public boolean isSpansMultipleDomains() {
        return spansMultipleDomains;
    }
}

package org.example.analyser.model;

import java.util.List;

/**
 * A circular dependency between domains - the same signal
 * {@code CircularDependencyAnalyzer} finds among classes, one
 * level up. See {@code DomainCircularDependencyAnalyzer}.
 */
public class DomainCycle {

    private List<String> domains;
    private String description;

    public DomainCycle() {
    }

    public DomainCycle(List<String> domains, String description) {
        this.domains = domains;
        this.description = description;
    }

    public List<String> getDomains() {
        return domains;
    }

    public void setDomains(List<String> domains) {
        this.domains = domains;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

package org.example.analyser.model;

import java.util.List;

/**
 * A single architecture-quality signal surfaced by one of the
 * architecture-intelligence analyzers (circular dependencies,
 * god classes, repository bypass, dead components) - a query
 * over data the rest of the analyzer already computed correctly,
 * not a new resolution step of its own.
 */
public class ArchitectureFinding {

    private ArchitectureFindingType type;

    /*
     * The class(es) this finding is about - one class for
     * GOD_CLASS/DEAD_COMPONENT, two for REPOSITORY_BYPASS
     * (source, target), the whole cycle's members for
     * CIRCULAR_DEPENDENCY.
     */
    private List<String> classes;

    private String description;

    public ArchitectureFinding() {
    }

    public ArchitectureFinding(
            ArchitectureFindingType type,
            List<String> classes,
            String description) {

        this.type = type;
        this.classes = classes;
        this.description = description;
    }

    public ArchitectureFindingType getType() {
        return type;
    }

    public void setType(ArchitectureFindingType type) {
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

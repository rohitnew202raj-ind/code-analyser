package org.example.analyser.model;

public class DomainDependency {

    private String sourceDomain;
    private String targetDomain;
    private DependencyType type;
    private int count;

    public DomainDependency() {
    }

    public DomainDependency(
            String sourceDomain,
            String targetDomain,
            DependencyType type) {

        this.sourceDomain = sourceDomain;
        this.targetDomain = targetDomain;
        this.type = type;
        this.count = 1;
    }

    public String getSourceDomain() {
        return sourceDomain;
    }

    public void setSourceDomain(String sourceDomain) {
        this.sourceDomain = sourceDomain;
    }

    public String getTargetDomain() {
        return targetDomain;
    }

    public void setTargetDomain(String targetDomain) {
        this.targetDomain = targetDomain;
    }

    public DependencyType getType() {
        return type;
    }

    public void setType(DependencyType type) {
        this.type = type;
    }

    public int getCount() {
        return count;
    }

    public void incrementCount() {
        this.count++;
    }
}
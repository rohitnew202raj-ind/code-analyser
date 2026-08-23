package org.example.analyser.model;

/**
 * A microservice-extraction assessment for one domain, built
 * from the domain dependency graph {@code DomainDependencyAnalyzer}
 * already computes. See {@code DomainBoundaryAnalyzer} for how
 * the verdict is decided and its documented scope.
 */
public class DomainBoundaryInfo {

    private String domainName;
    private int classCount;
    private int outgoingDomainDependencies;
    private int incomingDomainDependencies;
    private int crossDomainEdgeCount;
    private DomainBoundaryVerdict verdict;
    private String reason;

    public DomainBoundaryInfo() {
    }

    public DomainBoundaryInfo(
            String domainName,
            int classCount,
            int outgoingDomainDependencies,
            int incomingDomainDependencies,
            int crossDomainEdgeCount,
            DomainBoundaryVerdict verdict,
            String reason) {

        this.domainName = domainName;
        this.classCount = classCount;
        this.outgoingDomainDependencies = outgoingDomainDependencies;
        this.incomingDomainDependencies = incomingDomainDependencies;
        this.crossDomainEdgeCount = crossDomainEdgeCount;
        this.verdict = verdict;
        this.reason = reason;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public int getClassCount() {
        return classCount;
    }

    public void setClassCount(int classCount) {
        this.classCount = classCount;
    }

    public int getOutgoingDomainDependencies() {
        return outgoingDomainDependencies;
    }

    public void setOutgoingDomainDependencies(
            int outgoingDomainDependencies) {

        this.outgoingDomainDependencies = outgoingDomainDependencies;
    }

    public int getIncomingDomainDependencies() {
        return incomingDomainDependencies;
    }

    public void setIncomingDomainDependencies(
            int incomingDomainDependencies) {

        this.incomingDomainDependencies = incomingDomainDependencies;
    }

    public int getCrossDomainEdgeCount() {
        return crossDomainEdgeCount;
    }

    public void setCrossDomainEdgeCount(int crossDomainEdgeCount) {
        this.crossDomainEdgeCount = crossDomainEdgeCount;
    }

    public DomainBoundaryVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(DomainBoundaryVerdict verdict) {
        this.verdict = verdict;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

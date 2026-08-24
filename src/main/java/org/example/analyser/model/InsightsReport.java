package org.example.analyser.model;

import java.util.List;
import java.util.Map;

/**
 * Output of {@code ArchitectureInsightsAnalyzer}: a set of
 * cross-cutting answers built entirely from data the analysis
 * pipeline already computes (domains, flows, CRUD operations,
 * domain boundaries) - no new parsing, just new questions asked of
 * existing data. Backs the "Architecture Insights" section of the
 * HTML report.
 */
public class InsightsReport {

    private List<DomainInfo> domains;
    private List<EndpointFlowSummary> endpointFlows;
    private Map<String, List<String>> tablesByDomain;
    private List<TableUsageSummary> sharedTableRanking;
    private List<DomainBoundaryInfo> extractionRanking;
    private List<MultiTableTransaction> multiTableTransactions;

    public InsightsReport() {
    }

    public InsightsReport(
            List<DomainInfo> domains,
            List<EndpointFlowSummary> endpointFlows,
            Map<String, List<String>> tablesByDomain,
            List<TableUsageSummary> sharedTableRanking,
            List<DomainBoundaryInfo> extractionRanking,
            List<MultiTableTransaction> multiTableTransactions) {

        this.domains = domains;
        this.endpointFlows = endpointFlows;
        this.tablesByDomain = tablesByDomain;
        this.sharedTableRanking = sharedTableRanking;
        this.extractionRanking = extractionRanking;
        this.multiTableTransactions = multiTableTransactions;
    }

    public List<DomainInfo> getDomains() {
        return domains;
    }

    public List<EndpointFlowSummary> getEndpointFlows() {
        return endpointFlows;
    }

    public Map<String, List<String>> getTablesByDomain() {
        return tablesByDomain;
    }

    public List<TableUsageSummary> getSharedTableRanking() {
        return sharedTableRanking;
    }

    public List<DomainBoundaryInfo> getExtractionRanking() {
        return extractionRanking;
    }

    public List<MultiTableTransaction> getMultiTableTransactions() {
        return multiTableTransactions;
    }
}

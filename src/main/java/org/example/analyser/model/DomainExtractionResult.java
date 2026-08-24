package org.example.analyser.model;

import java.util.List;
import java.util.Map;

/**
 * Output of {@code DomainAnalyzer.analyze}: the final domain
 * grouping plus which of the three extraction strategies produced
 * it and how confident each one scored, kept alongside the result
 * rather than discarded so callers (console output, report.json)
 * can show *why* a grouping was chosen, not just the grouping
 * itself.
 */
public class DomainExtractionResult {

    private final List<DomainInfo> domains;
    private final String strategy;
    private final Map<String, Double> strategyConfidence;

    public DomainExtractionResult(
            List<DomainInfo> domains,
            String strategy,
            Map<String, Double> strategyConfidence) {

        this.domains = domains;
        this.strategy = strategy;
        this.strategyConfidence = strategyConfidence;
    }

    public List<DomainInfo> getDomains() {
        return domains;
    }

    public String getStrategy() {
        return strategy;
    }

    public Map<String, Double> getStrategyConfidence() {
        return strategyConfidence;
    }
}

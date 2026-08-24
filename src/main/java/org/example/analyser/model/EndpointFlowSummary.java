package org.example.analyser.model;

import java.util.List;

/**
 * One entry point's full reachable picture, condensed for
 * reporting: its call chain (controller -> service -> repository,
 * in visitation order) and the tables it reads, writes, or hits
 * with an operation this tool can't classify as either (a
 * hand-written {@code @Query} - see {@code ArchitectureInsightsAnalyzer}).
 * Built directly from {@link FlowPath}, so it answers "what does
 * this API/job do end-to-end" for both REST endpoints and
 * batch/scheduled jobs alike - the same underlying data, just
 * different entry point kinds.
 */
public class EndpointFlowSummary {

    private String triggerLabel;
    private TriggerType triggerType;
    private String entryClassName;
    private String entryMethodName;
    private String domain;
    private List<String> callChain;
    private List<String> tablesRead;
    private List<String> tablesWritten;
    private List<String> tablesCustomQuery;
    private boolean truncated;

    public EndpointFlowSummary() {
    }

    public EndpointFlowSummary(
            String triggerLabel,
            TriggerType triggerType,
            String entryClassName,
            String entryMethodName,
            String domain,
            List<String> callChain,
            List<String> tablesRead,
            List<String> tablesWritten,
            List<String> tablesCustomQuery,
            boolean truncated) {

        this.triggerLabel = triggerLabel;
        this.triggerType = triggerType;
        this.entryClassName = entryClassName;
        this.entryMethodName = entryMethodName;
        this.domain = domain;
        this.callChain = callChain;
        this.tablesRead = tablesRead;
        this.tablesWritten = tablesWritten;
        this.tablesCustomQuery = tablesCustomQuery;
        this.truncated = truncated;
    }

    public String getTriggerLabel() {
        return triggerLabel;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public String getEntryClassName() {
        return entryClassName;
    }

    public String getEntryMethodName() {
        return entryMethodName;
    }

    public String getDomain() {
        return domain;
    }

    public List<String> getCallChain() {
        return callChain;
    }

    public List<String> getTablesRead() {
        return tablesRead;
    }

    public List<String> getTablesWritten() {
        return tablesWritten;
    }

    public List<String> getTablesCustomQuery() {
        return tablesCustomQuery;
    }

    public boolean isTruncated() {
        return truncated;
    }
}

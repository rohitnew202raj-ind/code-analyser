package org.example.analyser.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.ArchitectureFindingType;
import org.example.analyser.model.BeanResolution;
import org.example.analyser.model.BeanResolutionVerdict;
import org.example.analyser.model.BehaviorClassification;
import org.example.analyser.model.ClassCouplingInfo;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.DependencyGraph;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DomainBoundaryInfo;
import org.example.analyser.model.DomainBoundaryVerdict;
import org.example.analyser.model.DomainCycle;
import org.example.analyser.model.DomainDependency;
import org.example.analyser.model.DomainInfo;
import org.example.analyser.model.EntityMutationInfo;
import org.example.analyser.model.EndpointFlowSummary;
import org.example.analyser.model.EntryPointBehavior;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.FlowPath;
import org.example.analyser.model.InsightsReport;
import org.example.analyser.model.MethodCallInfo;
import org.example.analyser.model.MultiTableTransaction;
import org.example.analyser.model.PersistenceFinding;
import org.example.analyser.model.TableUsageSummary;
import org.example.analyser.model.TriggerType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes the analysis out as structured JSON, Graphviz DOT,
 * Mermaid, and a self-contained HTML summary report, alongside
 * the existing console report.
 *
 * The console-only output was fine for eyeballing a small
 * test project, but for an actual migration project you want
 * this feeding a spreadsheet, a graph-visualization tool, or
 * some other downstream process - not just scrollback.
 *
 * Mermaid is generated alongside the existing DOT files rather
 * than replacing them: DOT still needs Graphviz to render, but
 * a {@code .mmd} file pastes directly into a GitHub markdown
 * code fence, mermaid.live, or any editor's Mermaid plugin -
 * zero extra tooling for the common case of "paste this into a
 * PR description." GraphML is deliberately not produced; DOT
 * and Mermaid between them cover the realistic audience
 * (Graphviz tooling and Mermaid-native renderers) without a
 * third graph format only a couple of specialist tools consume.
 *
 * {@code sequence-diagrams/*.mmd} is the dynamic counterpart to
 * those static graphs: one Mermaid {@code sequenceDiagram} per
 * entry point, showing the actual call order {@code FlowEngine}
 * traced for it - what a dependency graph's unordered edges
 * can't show. Written as one file per entry point, same rationale
 * as everywhere else in this class: Mermaid source text, no
 * rendering pipeline of our own.
 *
 * The HTML report is a summary, not a full data dump: it covers
 * architecture and persistence findings, domain boundaries, and
 * the entry-point inventory (now including each entry point's
 * READ_ONLY/MUTATING behavior classification) -
 * the sections short enough to be genuinely more readable as an
 * HTML table than as {@code report.json}. The full class
 * inventory, method-call graph, CRUD, and entity-mutation data
 * stay in {@code report.json} only; at real-project scale
 * (hundreds to thousands of rows) dumping all of that into HTML
 * too would just be a slower, less useful copy of the same JSON,
 * not a more readable one. No PDF exporter is built separately -
 * the HTML report is deliberately plain, print-friendly output,
 * and "File > Print > Save as PDF" in any browser already covers
 * that need without a second rendering pipeline to maintain.
 *
 * {@code domain-extraction-map.html} is a second, purpose-built
 * HTML page alongside the summary report: an interactive
 * force-directed graph of the same domain dependency/boundary
 * data already in {@code domain-graph.mmd} and the "Domain
 * Boundary Analysis" table, but laid out and clickable rather
 * than a static edge list - click a domain to see exactly what
 * it depends on, what depends on it, and
 * {@code DomainBoundaryAnalyzer}'s own reason for its verdict.
 * It is a self-contained file (no external JS libraries, no
 * network calls beyond system fonts already covered by the
 * OS) so it opens directly from disk. The node/edge layout is
 * computed once, synchronously, in a small hand-rolled
 * force simulation embedded in the page - not a dependency on
 * D3 or any other charting library - so the file has no build
 * step and no version to keep in sync with this exporter.
 */
@Component
public class ReportExporter {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    public record AnalysisReport(
            List<ClassInfo> classes,
            List<DependencyInfo> dependencies,
            List<ClassCouplingInfo> coupling,
            List<DomainInfo> domains,
            List<DomainDependency> domainDependencies,
            List<EntryPointInfo> entryPoints,
            List<MethodCallInfo> methodCalls,
            List<CrudOperationInfo> crudOperations,
            List<EntityMutationInfo> entityMutations,
            List<FlowPath> flows,
            List<ArchitectureFinding> architectureFindings,
            List<DomainCycle> domainCycles,
            List<DomainBoundaryInfo> domainBoundaries,
            List<PersistenceFinding> persistenceFindings,
            List<EntryPointBehavior> entryPointBehaviors,
            List<BeanResolution> beanResolutions,
            String domainExtractionStrategy,
            Map<String, Double> domainExtractionConfidence) {
    }

    public void export(
            Path outputDirectory,
            AnalysisReport report,
            DependencyGraph dependencyGraph,
            InsightsReport insights) throws IOException {

        Files.createDirectories(outputDirectory);

        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(
                        outputDirectory.resolve("report.json")
                                .toFile(),
                        report
                );

        Files.writeString(
                outputDirectory.resolve("dependency-graph.dot"),
                toDependencyDot(dependencyGraph)
        );

        Files.writeString(
                outputDirectory.resolve("domain-graph.dot"),
                toDomainDot(report.domainDependencies())
        );

        Files.writeString(
                outputDirectory.resolve("dependency-graph.mmd"),
                toDependencyMermaid(dependencyGraph)
        );

        Files.writeString(
                outputDirectory.resolve("domain-graph.mmd"),
                toDomainMermaid(
                        report.domainDependencies(),
                        report.domainBoundaries()
                )
        );

        Files.writeString(
                outputDirectory.resolve("report.html"),
                toHtmlReport(report)
        );

        Files.writeString(
                outputDirectory.resolve("domain-extraction-map.html"),
                toDomainExtractionMapHtml(
                        report.domainDependencies(),
                        report.domainBoundaries()
                )
        );

        Files.writeString(
                outputDirectory.resolve("insights-report.html"),
                toInsightsReportHtml(insights)
        );

        writeSequenceDiagrams(outputDirectory, report.flows());
    }

    /**
     * One Mermaid {@code sequenceDiagram} per entry point,
     * written under {@code sequence-diagrams/} - the dynamic
     * counterpart to the static dependency/domain diagrams:
     * "in what order do these classes actually talk to each
     * other when this entry point runs," which a dependency
     * graph (edges with no ordering) can't show. Generated as
     * Mermaid source text for the same reason as the Phase 5
     * diagrams - paste into a GitHub markdown fence or
     * mermaid.live, no rendering pipeline of our own.
     *
     * One file per entry point rather than one combined file:
     * Mermaid's {@code sequenceDiagram} syntax describes exactly
     * one diagram, so a project with many entry points would
     * need many separate fenced blocks anyway - a directory of
     * small, individually pasteable files is more useful than
     * one large file nothing can render as a whole.
     */
    private void writeSequenceDiagrams(
            Path outputDirectory,
            List<FlowPath> flows) throws IOException {

        if (flows.isEmpty()) {
            return;
        }

        Path sequenceDiagramsDirectory =
                outputDirectory.resolve("sequence-diagrams");

        Files.createDirectories(sequenceDiagramsDirectory);

        Set<String> usedFileNames = new LinkedHashSet<>();

        for (FlowPath flow : flows) {

            String fileName =
                    sequenceDiagramFileName(
                            flow.getEntryPoint(),
                            usedFileNames
                    );

            Files.writeString(
                    sequenceDiagramsDirectory.resolve(fileName),
                    toSequenceDiagram(flow)
            );
        }
    }

    private String sequenceDiagramFileName(
            EntryPointInfo entryPoint,
            Set<String> usedFileNames) {

        String base =
                mermaidId(entryPoint.getClassName())
                        + "-" + mermaidId(entryPoint.getMethodName());

        String candidate = base;
        int suffix = 2;

        while (!usedFileNames.add(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }

        return candidate + ".mmd";
    }

    private String toSequenceDiagram(FlowPath flow) {

        EntryPointInfo entryPoint = flow.getEntryPoint();

        StringBuilder mermaid = new StringBuilder();
        mermaid.append("sequenceDiagram\n");

        Set<String> participants = new LinkedHashSet<>();
        participants.add(entryPoint.getClassName());

        for (MethodCallInfo step : flow.getSteps()) {
            participants.add(step.getSourceClass());
            participants.add(step.getTargetClass());
        }

        for (String participant : participants) {
            mermaid.append("    participant ")
                    .append(mermaidId(participant))
                    .append("\n");
        }

        for (MethodCallInfo step : flow.getSteps()) {

            mermaid.append("    ")
                    .append(mermaidId(step.getSourceClass()))
                    .append("->>")
                    .append(mermaidId(step.getTargetClass()))
                    .append(": ")
                    .append(mermaidLabel(step.getTargetMethod()))
                    .append("()\n");
        }

        if (flow.isTruncated()) {

            mermaid.append("    Note over ")
                    .append(mermaidId(entryPoint.getClassName()))
                    .append(": flow truncated - reachable call ")
                    .append("graph exceeded the safety limit\n");
        }

        return mermaid.toString();
    }

    private String toDependencyDot(DependencyGraph graph) {

        StringBuilder dot = new StringBuilder();

        dot.append("digraph dependencies {\n");
        dot.append("  rankdir=LR;\n");

        for (ClassInfo classInfo : graph.getNodes()) {

            dot.append("  \"")
                    .append(escape(classInfo.getName()))
                    .append("\" [label=\"")
                    .append(escape(classInfo.getName()))
                    .append("\\n(")
                    .append(escape(
                            String.valueOf(classInfo.getType())
                    ))
                    .append(")\"];\n");
        }

        for (DependencyInfo edge : graph.getEdges()) {

            dot.append("  \"")
                    .append(escape(edge.getSourceClass()))
                    .append("\" -> \"")
                    .append(escape(edge.getTargetClass()))
                    .append("\" [label=\"")
                    .append(escape(
                            String.valueOf(edge.getType())
                    ))
                    .append("\"];\n");
        }

        dot.append("}\n");

        return dot.toString();
    }

    private String toDomainDot(
            List<DomainDependency> domainDependencies) {

        StringBuilder dot = new StringBuilder();

        dot.append("digraph domains {\n");
        dot.append("  rankdir=LR;\n");

        for (DomainDependency dependency : domainDependencies) {

            dot.append("  \"")
                    .append(escape(dependency.getSourceDomain()))
                    .append("\" -> \"")
                    .append(escape(dependency.getTargetDomain()))
                    .append("\" [label=\"")
                    .append(escape(
                            String.valueOf(dependency.getType())
                    ))
                    .append(" (")
                    .append(dependency.getCount())
                    .append(")\"];\n");
        }

        dot.append("}\n");

        return dot.toString();
    }

    private String escape(String value) {

        if (value == null) {
            return "";
        }

        return value.replace("\"", "\\\"");
    }

    // ==========================================
    // MERMAID
    // ==========================================

    private String toDependencyMermaid(DependencyGraph graph) {

        StringBuilder mermaid = new StringBuilder();
        mermaid.append("flowchart LR\n");

        for (ClassInfo classInfo : graph.getNodes()) {

            mermaid.append("  ")
                    .append(mermaidId(classInfo.getName()))
                    .append("[\"")
                    .append(mermaidLabel(classInfo.getName()))
                    .append("<br/>(")
                    .append(mermaidLabel(
                            String.valueOf(classInfo.getType())
                    ))
                    .append(")\"]\n");
        }

        for (DependencyInfo edge : graph.getEdges()) {

            mermaid.append("  ")
                    .append(mermaidId(edge.getSourceClass()))
                    .append(" -->|")
                    .append(mermaidLabel(
                            String.valueOf(edge.getType())
                    ))
                    .append("| ")
                    .append(mermaidId(edge.getTargetClass()))
                    .append("\n");
        }

        return mermaid.toString();
    }

    private String toDomainMermaid(
            List<DomainDependency> domainDependencies,
            List<DomainBoundaryInfo> domainBoundaries) {

        StringBuilder mermaid = new StringBuilder();
        mermaid.append("flowchart LR\n");

        for (DomainBoundaryInfo boundary : domainBoundaries) {

            mermaid.append("  ")
                    .append(mermaidId(boundary.getDomainName()))
                    .append("[\"")
                    .append(mermaidLabel(boundary.getDomainName()))
                    .append("\"]\n");
        }

        for (DomainDependency dependency : domainDependencies) {

            mermaid.append("  ")
                    .append(mermaidId(dependency.getSourceDomain()))
                    .append(" -->|")
                    .append(mermaidLabel(
                            String.valueOf(dependency.getType())
                    ))
                    .append(" x")
                    .append(dependency.getCount())
                    .append("| ")
                    .append(mermaidId(dependency.getTargetDomain()))
                    .append("\n");
        }

        /*
         * Colors each domain node by its DomainBoundaryAnalyzer
         * verdict - the whole point of generating this alongside
         * (not just instead of) the plain DOT graph: a
         * Mermaid-native renderer can show at a glance which
         * domains are clean extraction candidates versus tangled
         * or cycle-blocked, which a plain edge list can't convey.
         */

        mermaid.append("\n");

        mermaid.append(
                "  classDef extractionCandidate "
                        + "fill:#c8e6c9,stroke:#2e7d32,color:#1b5e20;\n"
        );

        mermaid.append(
                "  classDef tangled "
                        + "fill:#ffcdd2,stroke:#c62828,color:#7f0000;\n"
        );

        mermaid.append(
                "  classDef blockedByCycle "
                        + "fill:#ffe0b2,stroke:#e65100,color:#7a3c00;\n"
        );

        for (DomainBoundaryInfo boundary : domainBoundaries) {

            mermaid.append("  class ")
                    .append(mermaidId(boundary.getDomainName()))
                    .append(" ")
                    .append(mermaidClassDefName(boundary.getVerdict()))
                    .append(";\n");
        }

        return mermaid.toString();
    }

    private String mermaidClassDefName(DomainBoundaryVerdict verdict) {

        return switch (verdict) {
            case EXTRACTION_CANDIDATE -> "extractionCandidate";
            case TANGLED -> "tangled";
            case BLOCKED_BY_CYCLE -> "blockedByCycle";
        };
    }

    private String mermaidId(String name) {

        if (name == null) {
            return "unknown";
        }

        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String mermaidLabel(String value) {

        if (value == null) {
            return "";
        }

        // Mermaid node/edge labels can't contain an unescaped
        // double quote; swapping to a single quote is simpler
        // and safer than trying to HTML-entity-escape inside
        // what's already a quoted bracket label.
        return value.replace("\"", "'");
    }

    // ==========================================
    // DOMAIN EXTRACTION MAP (interactive HTML)
    // ==========================================

    private record ExtractionMapNode(
            String id, int classes, String status, String reason) {
    }

    private record ExtractionMapEdge(String source, String target) {
    }

    private String toDomainExtractionMapHtml(
            List<DomainDependency> domainDependencies,
            List<DomainBoundaryInfo> domainBoundaries) {

        List<ExtractionMapNode> nodes = domainBoundaries.stream()
                .sorted(Comparator.comparing(DomainBoundaryInfo::getDomainName))
                .map(boundary -> new ExtractionMapNode(
                        boundary.getDomainName(),
                        boundary.getClassCount(),
                        extractionMapStatus(boundary.getVerdict()),
                        boundary.getReason()
                ))
                .toList();

        // Distinct by (source, target) only - DomainDependency
        // carries one row per dependency *type*
        // (SERVICE_DEPENDENCY, COMPONENT_DEPENDENCY, ...), but a
        // migration map only needs "does A depend on B at all,"
        // not each call's flavor.
        List<ExtractionMapEdge> edges = domainDependencies.stream()
                .map(dependency -> new ExtractionMapEdge(
                        dependency.getSourceDomain(),
                        dependency.getTargetDomain()
                ))
                .distinct()
                .toList();

        String nodesJson;
        String edgesJson;

        try {
            nodesJson = objectMapper.writeValueAsString(nodes);
            edgesJson = objectMapper.writeValueAsString(edges);
        } catch (JsonProcessingException impossible) {
            // Both records are plain strings/ints already produced
            // by earlier analysis stages - this can't realistically
            // fail. Fall back to an empty map rather than letting
            // one exporter output take down the rest of export().
            nodesJson = "[]";
            edgesJson = "[]";
        }

        return DOMAIN_EXTRACTION_MAP_TEMPLATE
                .replace("__NODES_JSON__", nodesJson)
                .replace("__EDGES_JSON__", edgesJson);
    }

    private String extractionMapStatus(DomainBoundaryVerdict verdict) {

        return switch (verdict) {
            case EXTRACTION_CANDIDATE -> "candidate";
            case TANGLED -> "tangled";
            case BLOCKED_BY_CYCLE -> "blocked";
        };
    }

    private static final String DOMAIN_EXTRACTION_MAP_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <title>Domain Extraction Map</title>
            <style>
              :root {
                --ground: #F6F7F9;
                --surface: #FFFFFF;
                --surface-2: #FBFCFD;
                --ink: #1A212B;
                --ink-soft: #5C6675;
                --ink-faint: #8B94A1;
                --line: #DFE3E9;
                --accent: #1F6F6B;
                --status-candidate: #2E9663;
                --status-candidate-bg: rgba(46,150,99,0.12);
                --status-tangled: #C67A1E;
                --status-tangled-bg: rgba(198,122,30,0.14);
                --status-blocked: #C24450;
                --status-blocked-bg: rgba(194,68,80,0.12);
                --font-display: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                --font-body: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                --radius-lg: 12px;
                --radius-md: 8px;
              }
              @media (prefers-color-scheme: dark) {
                :root {
                  --ground: #11151B;
                  --surface: #181E26;
                  --surface-2: #1D242D;
                  --ink: #E7EBF0;
                  --ink-soft: #93A0AF;
                  --ink-faint: #66707D;
                  --line: #2A323D;
                  --accent: #52C4BC;
                  --status-candidate: #45C688;
                  --status-candidate-bg: rgba(69,198,136,0.16);
                  --status-tangled: #E2963E;
                  --status-tangled-bg: rgba(226,150,62,0.16);
                  --status-blocked: #E3626C;
                  --status-blocked-bg: rgba(227,98,108,0.16);
                }
              }
              * { box-sizing: border-box; }
              html, body { background: var(--ground); }
              body { margin: 0; background: var(--ground); color: var(--ink); font-family: var(--font-body); }
              .page { max-width: 1400px; margin: 0 auto; padding: 28px 32px 56px; display: flex; flex-direction: column; gap: 24px; }
              .topbar { display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; flex-wrap: wrap; border-bottom: 1px solid var(--line); padding-bottom: 20px; }
              .title-block h1 { font-family: var(--font-display); font-size: 1.55rem; margin: 0 0 6px; letter-spacing: -0.01em; }
              .subtitle { margin: 0; color: var(--ink-soft); font-size: 0.875rem; }
              .stat-row { display: flex; gap: 12px; flex-wrap: wrap; }
              .stat-tile { background: var(--surface); border: 1px solid var(--line); border-left: 3px solid transparent; border-radius: var(--radius-md); padding: 10px 16px; min-width: 128px; display: flex; flex-direction: column; gap: 2px; }
              .stat-value { font-family: var(--font-display); font-size: 1.5rem; font-variant-numeric: tabular-nums; line-height: 1; }
              .stat-label { font-size: 0.7rem; color: var(--ink-soft); text-transform: uppercase; letter-spacing: 0.04em; }
              .stat-tile.candidate { border-left-color: var(--status-candidate); }
              .stat-tile.tangled   { border-left-color: var(--status-tangled); }
              .stat-tile.blocked   { border-left-color: var(--status-blocked); }
              .stat-tile.candidate .stat-value { color: var(--status-candidate); }
              .stat-tile.tangled .stat-value   { color: var(--status-tangled); }
              .stat-tile.blocked .stat-value   { color: var(--status-blocked); }
              .workspace { display: grid; grid-template-columns: 1fr 320px; gap: 24px; align-items: start; }
              @media (max-width: 880px) { .workspace { grid-template-columns: 1fr; } .rail { position: static; } }
              .graph-pane { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); padding: 16px; display: flex; flex-direction: column; gap: 10px; overflow-x: auto; }
              #graph { width: 100%; height: auto; display: block; }
              .graph-hint { margin: 0; font-size: 0.78rem; color: var(--ink-faint); }
              .rail { display: flex; flex-direction: column; gap: 16px; position: sticky; top: 24px; }
              .panel { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); padding: 16px 18px; }
              .panel h2 { margin: 0 0 10px; font-family: var(--font-display); font-size: 0.76rem; text-transform: uppercase; letter-spacing: 0.06em; color: var(--ink-soft); }
              .legend ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; font-size: 0.82rem; }
              .legend li { display: flex; gap: 8px; align-items: flex-start; line-height: 1.4; }
              .dot { width: 10px; height: 10px; border-radius: 50%; margin-top: 4px; flex: none; }
              .dot.candidate { background: var(--status-candidate); }
              .dot.tangled   { background: var(--status-tangled); }
              .dot.blocked   { background: var(--status-blocked); }
              .legend .note { margin: 10px 0 0; padding-top: 10px; border-top: 1px solid var(--line); font-size: 0.76rem; color: var(--ink-faint); line-height: 1.5; }
              .detail-empty { font-size: 0.85rem; color: var(--ink-soft); line-height: 1.55; margin: 0; }
              .detail-header { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; margin-bottom: 10px; }
              .detail-name { font-family: var(--font-display); font-size: 1.05rem; }
              .badge { display: inline-flex; align-items: center; gap: 6px; padding: 3px 9px; border-radius: 999px; font-size: 0.68rem; text-transform: uppercase; letter-spacing: 0.04em; font-weight: 600; }
              .badge.candidate { background: var(--status-candidate-bg); color: var(--status-candidate); }
              .badge.tangled   { background: var(--status-tangled-bg); color: var(--status-tangled); }
              .badge.blocked   { background: var(--status-blocked-bg); color: var(--status-blocked); }
              .detail-meta { font-size: 0.78rem; color: var(--ink-soft); margin: 10px 0; font-variant-numeric: tabular-nums; }
              .detail-reason { font-size: 0.85rem; line-height: 1.55; margin: 0 0 14px; }
              .detail-group h3 { font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--ink-faint); margin: 0 0 6px; }
              .detail-group { margin-bottom: 12px; }
              .chip-row { display: flex; flex-wrap: wrap; gap: 6px; margin: 0; padding: 0; list-style: none; }
              .chip { font-family: var(--font-display); font-size: 0.72rem; padding: 4px 8px; border-radius: 6px; background: var(--surface-2); border: 1px solid var(--line); cursor: pointer; color: var(--ink); }
              .chip:hover, .chip:focus-visible { border-color: var(--accent); color: var(--accent); }
              .clear-btn { font-size: 0.75rem; color: var(--accent); background: none; border: none; cursor: pointer; padding: 0; text-decoration: underline; text-underline-offset: 2px; font-family: var(--font-body); }
              .candidates ol { margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 8px; }
              .candidate-item { display: flex; justify-content: space-between; gap: 8px; align-items: baseline; font-size: 0.85rem; padding: 8px 10px; border: 1px solid var(--line); border-radius: var(--radius-md); cursor: pointer; background: var(--surface-2); }
              .candidate-item:hover, .candidate-item:focus-visible { border-color: var(--status-candidate); }
              .candidate-item .name { font-family: var(--font-display); }
              .candidate-item .count { color: var(--ink-soft); font-variant-numeric: tabular-nums; font-size: 0.75rem; }
              .empty-state { font-size: 0.85rem; color: var(--ink-soft); }
              .edge { stroke: var(--line); stroke-width: 1.2; transition: stroke 0.15s ease, opacity 0.15s ease; }
              .edge.dim { opacity: 0.15; }
              .edge.active { stroke: var(--accent); stroke-width: 2; opacity: 1; }
              .arrow { fill: var(--line); transition: fill 0.15s ease, opacity 0.15s ease; }
              .arrow.dim { opacity: 0.15; }
              .arrow.active { fill: var(--accent); }
              .node { cursor: pointer; }
              .node-circle { stroke: var(--surface); stroke-width: 2; transition: opacity 0.15s ease; }
              .node-circle.dim { opacity: 0.28; }
              .node.selected .node-circle { stroke: var(--accent); stroke-width: 3; }
              .node-label { font-family: var(--font-display); font-size: 10.5px; fill: var(--ink); pointer-events: none; transition: opacity 0.15s ease; }
              .node-label.dim { opacity: 0.32; }
              .node:focus-visible { outline: none; }
              .node:focus-visible .node-circle { stroke: var(--accent); stroke-width: 3; }
              @media (prefers-reduced-motion: reduce) {
                .edge, .arrow, .node-circle, .node-label, .chip, .candidate-item { transition: none !important; }
              }
            </style>
            </head>
            <body>
            <div class="page">
              <header class="topbar">
                <div class="title-block">
                  <h1>Domain Extraction Map</h1>
                  <p class="subtitle" id="subtitle">Derived from the domain boundary analysis</p>
                </div>
                <div class="stat-row">
                  <div class="stat-tile candidate">
                    <span class="stat-value" id="statCandidate">0</span>
                    <span class="stat-label">ready to extract</span>
                  </div>
                  <div class="stat-tile tangled">
                    <span class="stat-value" id="statTangled">0</span>
                    <span class="stat-label">tangled hub</span>
                  </div>
                  <div class="stat-tile blocked">
                    <span class="stat-value" id="statBlocked">0</span>
                    <span class="stat-label">locked in a cycle</span>
                  </div>
                </div>
              </header>
              <main class="workspace">
                <section class="graph-pane">
                  <div id="graphWrap" aria-label="Force-directed graph of domain dependencies, colored by extraction readiness. Domains are focusable buttons; activate one to see its dependencies.">
                    <svg id="graph" viewBox="0 0 1100 780">
                      <rect id="bg" x="0" y="0" width="1100" height="780" fill="transparent" pointer-events="all"></rect>
                      <g id="edgeLayer"></g>
                      <g id="nodeLayer"></g>
                    </svg>
                  </div>
                  <p class="graph-hint" id="graphHint">Click a domain to trace what it depends on and what depends on it. Node size = how many other domains it touches.</p>
                </section>
                <aside class="rail">
                  <div class="panel legend">
                    <h2>Reading the map</h2>
                    <ul>
                      <li><span class="dot candidate"></span> Extraction candidate — few or no cross-domain calls, safe to pull out now</li>
                      <li><span class="dot tangled"></span> Tangled hub — touches too many domains to be a clean boundary on its own</li>
                      <li><span class="dot blocked"></span> Blocked by cycle — part of a circular dependency; needs the cycle broken first</li>
                    </ul>
                    <p class="note">Node size encodes how many domains it's connected to (in + out), not lines of code.</p>
                  </div>
                  <div class="panel detail-panel">
                    <h2>Selected domain</h2>
                    <div id="detailPanel"><p class="detail-empty">Select a domain in the map to see what depends on it, what it depends on, and why it is or isn't ready to extract.</p></div>
                  </div>
                  <div class="panel candidates">
                    <h2>Extract first</h2>
                    <ol id="candidateList"></ol>
                  </div>
                </aside>
              </main>
            </div>
            <script>
            (function () {
              var DOMAINS = __NODES_JSON__;
              var EDGES = __EDGES_JSON__;

              var STATUS_LABEL = {candidate:'extraction candidate', tangled:'tangled hub', blocked:'blocked by cycle'};
              var BADGE_LABEL = {candidate:'Extraction candidate', tangled:'Tangled hub', blocked:'Blocked by cycle'};

              function esc(s) {
                return String(s).replace(/[&<>"]/g, function (c) {
                  return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];
                });
              }

              var graphPane = document.querySelector('.graph-pane');

              if (!DOMAINS.length) {
                graphPane.innerHTML = '<p class="empty-state">No domains were found in this analysis.</p>';
                renderStats();
                return;
              }

              var W = 1100, H = 780;
              var nodesById = {};
              DOMAINS.forEach(function (d) { nodesById[d.id] = d; });

              var degree = {};
              DOMAINS.forEach(function (d) { degree[d.id] = 0; });
              EDGES.forEach(function (e) { degree[e.source] = (degree[e.source] || 0) + 1; degree[e.target] = (degree[e.target] || 0) + 1; });
              DOMAINS.forEach(function (d) { d.r = 14 + Math.sqrt(degree[d.id] || 0) * 5.5; });

              // ---- force-directed layout (synchronous, no external libs) ----
              (function layout() {
                var cx = W / 2, cy = H / 2, R = Math.min(W, H) * 0.32;
                DOMAINS.forEach(function (d, i) {
                  var a = (i / DOMAINS.length) * Math.PI * 2;
                  d.x = cx + R * Math.cos(a) + (Math.random() - 0.5) * 20;
                  d.y = cy + R * Math.sin(a) + (Math.random() - 0.5) * 20;
                  d.vx = 0; d.vy = 0;
                });

                var idealLen = 125, repulseK = 9500, springK = 0.022, centerK = 0.0022, damping = 0.85, maxV = 40;

                for (var iter = 0; iter < 450; iter++) {
                  for (var i = 0; i < DOMAINS.length; i++) {
                    for (var j = i + 1; j < DOMAINS.length; j++) {
                      var a2 = DOMAINS[i], b2 = DOMAINS[j];
                      var dx = a2.x - b2.x, dy = a2.y - b2.y;
                      var distSq = dx * dx + dy * dy;
                      if (distSq < 1) distSq = 1;
                      var dist = Math.sqrt(distSq);
                      var scale = 1 + (a2.r + b2.r) / 90;
                      var force = (repulseK * scale) / distSq;
                      var fx = (dx / dist) * force, fy = (dy / dist) * force;
                      a2.vx += fx; a2.vy += fy;
                      b2.vx -= fx; b2.vy -= fy;
                    }
                  }
                  EDGES.forEach(function (e) {
                    var a3 = nodesById[e.source], b3 = nodesById[e.target];
                    if (!a3 || !b3) return;
                    var dx = b3.x - a3.x, dy = b3.y - a3.y;
                    var dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1);
                    var diff = dist - idealLen;
                    var force = diff * springK;
                    var fx = (dx / dist) * force, fy = (dy / dist) * force;
                    a3.vx += fx; a3.vy += fy;
                    b3.vx -= fx; b3.vy -= fy;
                  });
                  DOMAINS.forEach(function (d) {
                    d.vx += (cx - d.x) * centerK;
                    d.vy += (cy - d.y) * centerK;
                  });
                  DOMAINS.forEach(function (d) {
                    d.vx *= damping; d.vy *= damping;
                    var spd = Math.hypot(d.vx, d.vy);
                    if (spd > maxV) { d.vx = d.vx / spd * maxV; d.vy = d.vy / spd * maxV; }
                    d.x += d.vx; d.y += d.vy;
                  });
                }

                var pad = 62, minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
                DOMAINS.forEach(function (d) {
                  minX = Math.min(minX, d.x); maxX = Math.max(maxX, d.x);
                  minY = Math.min(minY, d.y); maxY = Math.max(maxY, d.y);
                });
                var sx = (W - 2 * pad) / (maxX - minX || 1);
                var sy = (H - 2 * pad) / (maxY - minY || 1);
                var s = Math.min(sx, sy, 1.15);
                DOMAINS.forEach(function (d) {
                  d.x = pad + (d.x - minX) * s;
                  d.y = pad + (d.y - minY) * s;
                });
              })();

              // ---- render ----
              var svgNS = 'http://www.w3.org/2000/svg';
              function svgEl(tag, attrs, cls) {
                var e = document.createElementNS(svgNS, tag);
                for (var k in attrs) e.setAttribute(k, attrs[k]);
                if (cls) e.setAttribute('class', cls);
                return e;
              }

              function arrowPoints(tipX, tipY, ux, uy, len, width) {
                var baseX = tipX - ux * len, baseY = tipY - uy * len;
                var perpX = -uy, perpY = ux;
                return tipX + ',' + tipY + ' ' +
                  (baseX + perpX * width / 2) + ',' + (baseY + perpY * width / 2) + ' ' +
                  (baseX - perpX * width / 2) + ',' + (baseY - perpY * width / 2);
              }

              var edgeLayer = document.getElementById('edgeLayer');
              var nodeLayer = document.getElementById('nodeLayer');
              var edgeEls = [];

              EDGES.forEach(function (e) {
                var a = nodesById[e.source], b = nodesById[e.target];
                if (!a || !b) return;
                var dx = b.x - a.x, dy = b.y - a.y;
                var dist = Math.max(Math.hypot(dx, dy), 1);
                var ux = dx / dist, uy = dy / dist;
                var startX = a.x + ux * a.r, startY = a.y + uy * a.r;
                var arrowLen = 8, arrowW = 6;
                var tipX = b.x - ux * b.r, tipY = b.y - uy * b.r;
                var lineEndX = tipX - ux * arrowLen, lineEndY = tipY - uy * arrowLen;

                var line = svgEl('line', {x1: startX, y1: startY, x2: lineEndX, y2: lineEndY}, 'edge');
                var arrow = svgEl('polygon', {points: arrowPoints(tipX, tipY, ux, uy, arrowLen, arrowW)}, 'arrow');
                edgeLayer.appendChild(line);
                edgeLayer.appendChild(arrow);
                edgeEls.push({s: e.source, t: e.target, line: line, arrow: arrow});
              });

              DOMAINS.forEach(function (d) {
                var g = svgEl('g', {transform: 'translate(' + d.x + ',' + d.y + ')', tabindex: '0', role: 'button'}, 'node');
                g.setAttribute('aria-label', d.id + ', ' + (STATUS_LABEL[d.status] || d.status) + ', ' + d.classes + ' classes');
                var circle = svgEl('circle', {r: d.r}, 'node-circle');
                circle.setAttribute('fill', 'var(--status-' + d.status + ')');
                var label = svgEl('text', {y: d.r + 13, 'text-anchor': 'middle'}, 'node-label');
                label.textContent = d.id;
                g.appendChild(circle);
                g.appendChild(label);
                g.addEventListener('click', function () { selectNode(d.id); });
                g.addEventListener('keydown', function (ev) {
                  if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); selectNode(d.id); }
                });
                nodeLayer.appendChild(g);
                d.el = g;
                d.circleEl = circle;
                d.labelEl = label;
              });

              document.getElementById('bg').addEventListener('click', function () { clearSelection(); });

              var selectedId = null;

              function selectNode(id) {
                if (selectedId === id) { clearSelection(); return; }
                selectedId = id;
                var neighbors = {};
                neighbors[id] = true;
                EDGES.forEach(function (e) {
                  if (e.source === id) neighbors[e.target] = true;
                  if (e.target === id) neighbors[e.source] = true;
                });
                DOMAINS.forEach(function (d) {
                  var on = !!neighbors[d.id];
                  d.circleEl.classList.toggle('dim', !on);
                  d.labelEl.classList.toggle('dim', !on);
                  d.el.classList.toggle('selected', d.id === id);
                });
                edgeEls.forEach(function (ee) {
                  var touches = ee.s === id || ee.t === id;
                  ee.line.classList.toggle('active', touches);
                  ee.arrow.classList.toggle('active', touches);
                  ee.line.classList.toggle('dim', !touches);
                  ee.arrow.classList.toggle('dim', !touches);
                });
                renderDetail(id);
              }

              function clearSelection() {
                selectedId = null;
                DOMAINS.forEach(function (d) {
                  d.circleEl.classList.remove('dim');
                  d.labelEl.classList.remove('dim');
                  d.el.classList.remove('selected');
                });
                edgeEls.forEach(function (ee) {
                  ee.line.classList.remove('active', 'dim');
                  ee.arrow.classList.remove('active', 'dim');
                });
                renderDetail(null);
              }

              function renderDetail(id) {
                var panel = document.getElementById('detailPanel');
                if (!id) {
                  panel.innerHTML = '<p class="detail-empty">Select a domain in the map to see what depends on it, what it depends on, and why it is or isn\\'t ready to extract.</p>';
                  return;
                }
                var d = nodesById[id];
                var outs = EDGES.filter(function (e) { return e.source === id; }).map(function (e) { return e.target; });
                var ins = EDGES.filter(function (e) { return e.target === id; }).map(function (e) { return e.source; });

                var html = '';
                html += '<div class="detail-header"><span class="detail-name">' + esc(d.id) + '</span><button class="clear-btn" id="clearSel">clear</button></div>';
                html += '<span class="badge ' + esc(d.status) + '">' + esc(BADGE_LABEL[d.status] || d.status) + '</span>';
                html += '<p class="detail-meta">' + d.classes + ' classes &middot; ' + outs.length + ' outgoing &middot; ' + ins.length + ' incoming</p>';
                html += '<p class="detail-reason">' + esc(d.reason || '') + '</p>';
                if (outs.length) {
                  html += '<div class="detail-group"><h3>Depends on</h3><div class="chip-row">' +
                    outs.map(function (o) { return '<button class="chip" data-id="' + esc(o) + '">' + esc(o) + '</button>'; }).join('') +
                    '</div></div>';
                }
                if (ins.length) {
                  html += '<div class="detail-group"><h3>Depended on by</h3><div class="chip-row">' +
                    ins.map(function (o) { return '<button class="chip" data-id="' + esc(o) + '">' + esc(o) + '</button>'; }).join('') +
                    '</div></div>';
                }
                panel.innerHTML = html;
                document.getElementById('clearSel').addEventListener('click', clearSelection);
                Array.prototype.forEach.call(panel.querySelectorAll('.chip'), function (c) {
                  c.addEventListener('click', function () { selectNode(c.getAttribute('data-id')); });
                });
              }

              function renderCandidates() {
                var list = document.getElementById('candidateList');
                var candidates = DOMAINS.filter(function (d) { return d.status === 'candidate'; })
                  .sort(function (a, b) { return a.classes - b.classes; });
                if (!candidates.length) {
                  list.innerHTML = '<li class="empty-state">None - every domain here needs another domain untangled first.</li>';
                  return;
                }
                list.innerHTML = candidates.map(function (d) {
                  return '<li class="candidate-item" data-id="' + esc(d.id) + '" tabindex="0" role="button">' +
                    '<span class="name">' + esc(d.id) + '</span><span class="count">' + d.classes + ' classes</span></li>';
                }).join('');
                Array.prototype.forEach.call(list.querySelectorAll('.candidate-item'), function (li) {
                  li.addEventListener('click', function () { selectNode(li.getAttribute('data-id')); });
                  li.addEventListener('keydown', function (ev) {
                    if (ev.key === 'Enter' || ev.key === ' ') { ev.preventDefault(); selectNode(li.getAttribute('data-id')); }
                  });
                });
              }

              renderCandidates();
              renderStats();

              function renderStats() {
                var counts = {candidate: 0, tangled: 0, blocked: 0};
                DOMAINS.forEach(function (d) { counts[d.status] = (counts[d.status] || 0) + 1; });
                document.getElementById('statCandidate').textContent = counts.candidate;
                document.getElementById('statTangled').textContent = counts.tangled;
                document.getElementById('statBlocked').textContent = counts.blocked;
                document.getElementById('subtitle').textContent =
                  DOMAINS.length + ' domains · ' + EDGES.length + ' cross-domain calls · derived from the domain boundary analysis';
              }
            })();
            </script>
            </body>
            </html>
            """;

    // ==========================================
    // ARCHITECTURE INSIGHTS (graphical HTML)
    //
    // A purpose-built report answering the cross-cutting
    // questions a reader actually has when planning a
    // decomposition ("what domains exist", "which table does
    // this API touch", "what's cheapest to extract first", "which
    // methods touch multiple tables") in one page, with charts for
    // the two naturally rankable questions (shared-table usage,
    // extraction cost) rather than raw tables only. Same
    // self-contained, no-external-library approach as
    // domain-extraction-map.html - the data is embedded as JSON
    // and rendered client-side, so the file opens directly from
    // disk with nothing else to install.
    // ==========================================

    private static final Set<TriggerType> REST_TRIGGER_TYPES = Set.of(
            TriggerType.GET, TriggerType.POST, TriggerType.PUT,
            TriggerType.PATCH, TriggerType.DELETE, TriggerType.ANY,
            TriggerType.GRAPHQL_QUERY, TriggerType.GRAPHQL_MUTATION,
            TriggerType.GRAPHQL_SUBSCRIPTION, TriggerType.GRAPHQL_SCHEMA_MAPPING
    );

    private record InsightsDomainRow(String name, int classCount) {
    }

    private record InsightsEndpointRow(
            String trigger,
            String triggerType,
            String className,
            String methodName,
            String domain,
            List<String> callChain,
            List<String> tablesRead,
            List<String> tablesWritten,
            List<String> tablesCustomQuery,
            boolean truncated) {
    }

    private record InsightsTableRow(
            String table, String entity, List<String> classes, int count) {
    }

    private record InsightsExtractionRow(
            String domain,
            String verdict,
            int crossDomainEdgeCount,
            int classCount,
            String reason) {
    }

    private record InsightsTransactionRow(
            String className,
            String methodName,
            String domain,
            List<String> tables,
            List<String> entities,
            boolean spansMultipleDomains) {
    }

    private String toInsightsReportHtml(InsightsReport insights) {

        List<InsightsDomainRow> domainRows =
                insights.getDomains().stream()
                        .sorted(Comparator.comparing(DomainInfo::getName))
                        .map(domain -> new InsightsDomainRow(
                                domain.getName(), domain.getClassCount()
                        ))
                        .toList();

        List<InsightsEndpointRow> restRows = new ArrayList<>();
        List<InsightsEndpointRow> batchRows = new ArrayList<>();

        for (EndpointFlowSummary flow : insights.getEndpointFlows()) {

            InsightsEndpointRow row = new InsightsEndpointRow(
                    flow.getTriggerLabel(),
                    flow.getTriggerType().name(),
                    flow.getEntryClassName(),
                    flow.getEntryMethodName(),
                    flow.getDomain(),
                    flow.getCallChain(),
                    flow.getTablesRead(),
                    flow.getTablesWritten(),
                    flow.getTablesCustomQuery(),
                    flow.isTruncated()
            );

            if (REST_TRIGGER_TYPES.contains(flow.getTriggerType())) {
                restRows.add(row);
            } else {
                batchRows.add(row);
            }
        }

        List<InsightsTableRow> sharedTableRows =
                insights.getSharedTableRanking().stream()
                        .map(usage -> new InsightsTableRow(
                                usage.getTableName(),
                                usage.getEntityClass(),
                                usage.getTouchingClasses(),
                                usage.getTouchingClassCount()
                        ))
                        .toList();

        List<InsightsExtractionRow> extractionRows =
                insights.getExtractionRanking().stream()
                        .map(boundary -> new InsightsExtractionRow(
                                boundary.getDomainName(),
                                boundary.getVerdict().name(),
                                boundary.getCrossDomainEdgeCount(),
                                boundary.getClassCount(),
                                boundary.getReason()
                        ))
                        .toList();

        List<InsightsTransactionRow> transactionRows =
                insights.getMultiTableTransactions().stream()
                        .map(transaction -> new InsightsTransactionRow(
                                transaction.getClassName(),
                                transaction.getMethodName(),
                                transaction.getDomain(),
                                transaction.getTables(),
                                transaction.getEntities(),
                                transaction.isSpansMultipleDomains()
                        ))
                        .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("domains", domainRows);
        payload.put("restEndpoints", restRows);
        payload.put("batchJobs", batchRows);
        payload.put("tablesByDomain", insights.getTablesByDomain());
        payload.put("sharedTables", sharedTableRows);
        payload.put("extractionRanking", extractionRows);
        payload.put("multiTableTransactions", transactionRows);

        String dataJson;

        try {
            dataJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException impossible) {
            // Every field above is a plain string/int/boolean/list
            // already produced by earlier analysis stages - this
            // can't realistically fail. Fall back to an empty
            // payload rather than letting one exporter output take
            // down the rest of export().
            dataJson = "{}";
        }

        return INSIGHTS_REPORT_TEMPLATE.replace("__DATA_JSON__", dataJson);
    }

    private static final String INSIGHTS_REPORT_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <title>Architecture Insights</title>
            <style>
              :root {
                --ground: #F6F7F9;
                --surface: #FFFFFF;
                --surface-2: #FBFCFD;
                --ink: #1A212B;
                --ink-soft: #5C6675;
                --ink-faint: #8B94A1;
                --line: #DFE3E9;
                --accent: #1F6F6B;
                --write: #C24450;
                --write-bg: rgba(194,68,80,0.12);
                --read: #2E9663;
                --read-bg: rgba(46,150,99,0.12);
                --custom: #C67A1E;
                --custom-bg: rgba(198,122,30,0.14);
                --status-candidate: #2E9663;
                --status-tangled: #C67A1E;
                --status-blocked: #C24450;
                --font-display: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                --font-body: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                --radius-lg: 12px;
                --radius-md: 8px;
              }
              @media (prefers-color-scheme: dark) {
                :root {
                  --ground: #11151B;
                  --surface: #181E26;
                  --surface-2: #1D242D;
                  --ink: #E7EBF0;
                  --ink-soft: #93A0AF;
                  --ink-faint: #66707D;
                  --line: #2A323D;
                  --accent: #52C4BC;
                  --write: #E3626C;
                  --write-bg: rgba(227,98,108,0.16);
                  --read: #45C688;
                  --read-bg: rgba(69,198,136,0.16);
                  --custom: #E2963E;
                  --custom-bg: rgba(226,150,62,0.16);
                  --status-candidate: #45C688;
                  --status-tangled: #E2963E;
                  --status-blocked: #E3626C;
                }
              }
              * { box-sizing: border-box; }
              html, body { background: var(--ground); }
              body { margin: 0; background: var(--ground); color: var(--ink); font-family: var(--font-body); }
              .page { max-width: 1200px; margin: 0 auto; padding: 28px 32px 64px; display: flex; flex-direction: column; gap: 32px; }
              header.topbar h1 { font-family: var(--font-display); font-size: 1.55rem; margin: 0 0 6px; letter-spacing: -0.01em; }
              header.topbar p { margin: 0; color: var(--ink-soft); font-size: 0.875rem; }
              .stat-row { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 16px; }
              .stat-tile { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-md); padding: 10px 16px; min-width: 110px; display: flex; flex-direction: column; gap: 2px; }
              .stat-value { font-family: var(--font-display); font-size: 1.4rem; font-variant-numeric: tabular-nums; line-height: 1; color: var(--accent); }
              .stat-label { font-size: 0.68rem; color: var(--ink-soft); text-transform: uppercase; letter-spacing: 0.04em; }
              section { background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-lg); padding: 20px 22px; }
              section h2 { font-family: var(--font-display); font-size: 0.95rem; margin: 0 0 4px; letter-spacing: -0.005em; }
              section .q { color: var(--ink-soft); font-size: 0.8rem; margin: 0 0 16px; }
              input.filter { width: 100%; max-width: 340px; padding: 7px 10px; border-radius: var(--radius-md); border: 1px solid var(--line); background: var(--surface-2); color: var(--ink); font-size: 0.82rem; margin-bottom: 12px; }
              input.filter:focus { outline: none; border-color: var(--accent); }
              table { width: 100%; border-collapse: collapse; font-size: 0.82rem; }
              th, td { text-align: left; padding: 7px 10px; border-bottom: 1px solid var(--line); vertical-align: top; }
              th { color: var(--ink-soft); font-weight: 600; font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.04em; }
              tr:hover td { background: var(--surface-2); }
              td.mono, .mono { font-family: var(--font-display); font-size: 0.78rem; }
              .chip-row { display: flex; flex-wrap: wrap; gap: 4px; }
              .chip { font-family: var(--font-display); font-size: 0.7rem; padding: 2px 7px; border-radius: 5px; background: var(--surface-2); border: 1px solid var(--line); white-space: nowrap; }
              .chip.write { background: var(--write-bg); color: var(--write); border-color: transparent; }
              .chip.read { background: var(--read-bg); color: var(--read); border-color: transparent; }
              .chip.custom { background: var(--custom-bg); color: var(--custom); border-color: transparent; }
              .chip.domain { background: rgba(31,111,107,0.12); color: var(--accent); border-color: transparent; }
              .call-chain { display: flex; flex-direction: column; gap: 2px; font-family: var(--font-display); font-size: 0.72rem; color: var(--ink-soft); }
              .empty-state { color: var(--ink-soft); font-size: 0.85rem; font-style: italic; }
              .bar-chart { display: flex; flex-direction: column; gap: 8px; margin-bottom: 18px; }
              .bar-row { display: grid; grid-template-columns: 160px 1fr 42px; align-items: center; gap: 10px; font-size: 0.78rem; }
              .bar-name { font-family: var(--font-display); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
              .bar-track { background: var(--surface-2); border-radius: 5px; height: 16px; overflow: hidden; border: 1px solid var(--line); }
              .bar-fill { height: 100%; border-radius: 4px 0 0 4px; }
              .bar-fill.candidate { background: var(--status-candidate); }
              .bar-fill.tangled { background: var(--status-tangled); }
              .bar-fill.blocked { background: var(--status-blocked); }
              .bar-fill.table { background: var(--accent); }
              .bar-value { text-align: right; font-variant-numeric: tabular-nums; color: var(--ink-soft); }
              .badge { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 999px; font-size: 0.66rem; text-transform: uppercase; letter-spacing: 0.03em; font-weight: 600; }
              .badge.candidate { background: rgba(46,150,99,0.14); color: var(--status-candidate); }
              .badge.tangled { background: rgba(198,122,30,0.16); color: var(--status-tangled); }
              .badge.blocked { background: rgba(194,68,80,0.14); color: var(--status-blocked); }
              .badge.spans { background: var(--write-bg); color: var(--write); }
              .badge.contained { background: rgba(139,148,161,0.16); color: var(--ink-soft); }
              details.chain-toggle summary { cursor: pointer; color: var(--accent); font-size: 0.76rem; }
              .legend-note { font-size: 0.74rem; color: var(--ink-faint); margin: 4px 0 0; }
              .footer-note { font-size: 0.76rem; color: var(--ink-faint); }
            </style>
            </head>
            <body>
            <div class="page">
              <header class="topbar">
                <h1>Architecture Insights</h1>
                <p>Cross-cutting answers derived from the same analysis data as <code>report.json</code> - no new parsing, just new questions asked of it.</p>
                <div class="stat-row" id="statRow"></div>
              </header>

              <section id="domainsSection">
                <h2>Domains</h2>
                <p class="q">What are the different domains?</p>
                <table id="domainsTable"></table>
              </section>

              <section id="extractionSection">
                <h2>Extraction ranking</h2>
                <p class="q">Which domain has the fewest cross-domain calls - cheapest and safest to extract first?</p>
                <div class="bar-chart" id="extractionChart"></div>
                <table id="extractionTable"></table>
                <p class="legend-note">Sorted extraction-candidate-first, then by cross-domain edge count ascending. A domain blocked by a cycle stays last regardless of its raw edge count - the cycle has to be broken before extraction is possible at all.</p>
              </section>

              <section id="tablesByDomainSection">
                <h2>Domain &rarr; database</h2>
                <p class="q">What domain is connected to which database table?</p>
                <table id="tablesByDomainTable"></table>
              </section>

              <section id="sharedTablesSection">
                <h2>Shared table usage</h2>
                <p class="q">Which entity/table is shared by many different services?</p>
                <div class="bar-chart" id="sharedTablesChart"></div>
                <table id="sharedTablesTable"></table>
              </section>

              <section id="restSection">
                <h2>REST endpoint call chains</h2>
                <p class="q">Which DB table is called for a given API, and what's the full call chain controller &rarr; service &rarr; repository &rarr; table?</p>
                <input class="filter" type="text" id="restFilter" placeholder="Filter by path, class, domain, or table&hellip;">
                <table id="restTable"></table>
              </section>

              <section id="batchSection">
                <h2>Batch / scheduled job flows</h2>
                <p class="q">What does each batch job read and write, end-to-end?</p>
                <input class="filter" type="text" id="batchFilter" placeholder="Filter by class, domain, or table&hellip;">
                <table id="batchTable"></table>
              </section>

              <section id="transactionsSection">
                <h2>Multi-table transactions</h2>
                <p class="q">Are there methods whose own body touches multiple tables/domains in one call - a sign you'll need sagas after splitting?</p>
                <table id="transactionsTable"></table>
                <p class="legend-note">Scope: a method's own direct repository calls only, not <code>@Transactional</code> propagation into methods it calls in turn. Flags the multi-table fact itself, regardless of whether the method carries a transaction annotation - see LIMITATIONS.md.</p>
              </section>

              <p class="footer-note">Full underlying data: <code>report.json</code>. Domain dependency graph: <a href="domain-extraction-map.html">domain-extraction-map.html</a>. Per-endpoint sequence diagrams: <code>sequence-diagrams/*.mmd</code>.</p>
            </div>
            <script>
            (function () {
              var DATA = __DATA_JSON__;

              function esc(s) {
                return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
                  return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];
                });
              }

              function chip(label, cls) {
                return '<span class="chip' + (cls ? ' ' + cls : '') + '">' + esc(label) + '</span>';
              }

              function chipRow(values, cls) {
                if (!values || !values.length) return '<span class="empty-state">&mdash;</span>';
                return '<div class="chip-row">' + values.map(function (v) { return chip(v, cls); }).join('') + '</div>';
              }

              function verdictBadgeClass(verdict) {
                if (verdict === 'EXTRACTION_CANDIDATE') return 'candidate';
                if (verdict === 'TANGLED') return 'tangled';
                return 'blocked';
              }

              // ---- stats ----
              (function renderStats() {
                var row = document.getElementById('statRow');
                var tiles = [
                  ['Domains', DATA.domains.length],
                  ['REST endpoints', DATA.restEndpoints.length],
                  ['Batch/scheduled jobs', DATA.batchJobs.length],
                  ['Shared tables', DATA.sharedTables.filter(function (t) { return t.count > 1; }).length],
                  ['Multi-table methods', DATA.multiTableTransactions.length]
                ];
                row.innerHTML = tiles.map(function (t) {
                  return '<div class="stat-tile"><span class="stat-value">' + t[1] + '</span><span class="stat-label">' + esc(t[0]) + '</span></div>';
                }).join('');
              })();

              // ---- domains ----
              (function renderDomains() {
                var table = document.getElementById('domainsTable');
                if (!DATA.domains.length) {
                  table.outerHTML = '<p class="empty-state">No domains found.</p>';
                  return;
                }
                var rows = DATA.domains.map(function (d) {
                  return '<tr><td class="mono">' + esc(d.name) + '</td><td>' + d.classCount + '</td></tr>';
                }).join('');
                table.innerHTML = '<tr><th>Domain</th><th>Classes</th></tr>' + rows;
              })();

              // ---- extraction ranking ----
              (function renderExtraction() {
                var chart = document.getElementById('extractionChart');
                var table = document.getElementById('extractionTable');
                var rows = DATA.extractionRanking;
                if (!rows.length) {
                  chart.innerHTML = '';
                  table.outerHTML = '<p class="empty-state">No domain boundary data available.</p>';
                  return;
                }
                var maxEdges = Math.max.apply(null, rows.map(function (r) { return r.crossDomainEdgeCount; }).concat([1]));
                chart.innerHTML = rows.map(function (r) {
                  var cls = verdictBadgeClass(r.verdict);
                  var pct = Math.max(4, Math.round((r.crossDomainEdgeCount / maxEdges) * 100));
                  return '<div class="bar-row"><span class="bar-name">' + esc(r.domain) + '</span>' +
                    '<div class="bar-track"><div class="bar-fill ' + cls + '" style="width:' + pct + '%"></div></div>' +
                    '<span class="bar-value">' + r.crossDomainEdgeCount + '</span></div>';
                }).join('');
                table.innerHTML = '<tr><th>Domain</th><th>Verdict</th><th>Classes</th><th>Cross-domain edges</th><th>Reason</th></tr>' +
                  rows.map(function (r) {
                    return '<tr><td class="mono">' + esc(r.domain) + '</td><td><span class="badge ' + verdictBadgeClass(r.verdict) + '">' + esc(r.verdict.replace(/_/g, ' ')) + '</span></td>' +
                      '<td>' + r.classCount + '</td><td>' + r.crossDomainEdgeCount + '</td><td>' + esc(r.reason) + '</td></tr>';
                  }).join('');
              })();

              // ---- domain -> tables ----
              (function renderTablesByDomain() {
                var table = document.getElementById('tablesByDomainTable');
                var domains = Object.keys(DATA.tablesByDomain);
                if (!domains.length) {
                  table.outerHTML = '<p class="empty-state">No CRUD operations found.</p>';
                  return;
                }
                table.innerHTML = '<tr><th>Domain</th><th>Tables</th></tr>' +
                  domains.map(function (d) {
                    return '<tr><td class="mono">' + esc(d) + '</td><td>' + chipRow(DATA.tablesByDomain[d]) + '</td></tr>';
                  }).join('');
              })();

              // ---- shared table ranking ----
              (function renderSharedTables() {
                var chart = document.getElementById('sharedTablesChart');
                var table = document.getElementById('sharedTablesTable');
                var rows = DATA.sharedTables;
                if (!rows.length) {
                  chart.innerHTML = '';
                  table.outerHTML = '<p class="empty-state">No CRUD operations found.</p>';
                  return;
                }
                var maxCount = Math.max.apply(null, rows.map(function (r) { return r.count; }).concat([1]));
                chart.innerHTML = rows.slice(0, 12).map(function (r) {
                  var pct = Math.max(4, Math.round((r.count / maxCount) * 100));
                  return '<div class="bar-row"><span class="bar-name">' + esc(r.table) + '</span>' +
                    '<div class="bar-track"><div class="bar-fill table" style="width:' + pct + '%"></div></div>' +
                    '<span class="bar-value">' + r.count + '</span></div>';
                }).join('');
                table.innerHTML = '<tr><th>Table</th><th>Entity</th><th>Touching classes</th></tr>' +
                  rows.map(function (r) {
                    return '<tr><td class="mono">' + esc(r.table) + '</td><td class="mono">' + esc(r.entity) + '</td><td>' + chipRow(r.classes) + '</td></tr>';
                  }).join('');
              })();

              // ---- endpoint flow tables (REST + batch share this renderer) ----
              function renderEndpointTable(tableId, filterId, rows, firstColumnLabel) {
                var table = document.getElementById(tableId);
                var filter = document.getElementById(filterId);

                function rowHtml(r) {
                  var chainId = 'chain-' + tableId + '-' + Math.random().toString(36).slice(2);
                  var chainHtml = r.callChain.length
                    ? '<details class="chain-toggle"><summary>' + r.callChain.length + ' call' + (r.callChain.length === 1 ? '' : 's') + '</summary><div class="call-chain">' +
                      r.callChain.map(function (c) { return esc(c); }).join('<br>') + '</div></details>'
                    : '<span class="empty-state">no further calls</span>';
                  var truncatedNote = r.truncated ? ' <span class="badge tangled">truncated</span>' : '';
                  return '<tr data-search="' + esc((r.trigger + ' ' + r.className + ' ' + r.methodName + ' ' + r.domain + ' ' +
                      r.tablesRead.concat(r.tablesWritten, r.tablesCustomQuery).join(' ')).toLowerCase()) + '">' +
                    '<td class="mono">' + esc(r.trigger) + truncatedNote + '</td>' +
                    '<td class="mono">' + esc(r.className) + '.' + esc(r.methodName) + '</td>' +
                    '<td>' + chip(r.domain, 'domain') + '</td>' +
                    '<td>' + chainHtml + '</td>' +
                    '<td>' + chipRow(r.tablesRead, 'read') + '</td>' +
                    '<td>' + chipRow(r.tablesWritten, 'write') + '</td>' +
                    '<td>' + chipRow(r.tablesCustomQuery, 'custom') + '</td>' +
                    '</tr>';
                }

                if (!rows.length) {
                  table.outerHTML = '<p class="empty-state">None found.</p>';
                  if (filter) filter.style.display = 'none';
                  return;
                }

                var header = '<tr><th>' + firstColumnLabel + '</th><th>Entry point</th><th>Domain</th><th>Call chain</th><th>Reads</th><th>Writes</th><th>Custom query</th></tr>';
                table.innerHTML = header + rows.map(rowHtml).join('');

                if (filter) {
                  filter.addEventListener('input', function () {
                    var q = filter.value.toLowerCase();
                    Array.prototype.forEach.call(table.querySelectorAll('tr[data-search]'), function (tr) {
                      tr.style.display = tr.getAttribute('data-search').indexOf(q) === -1 ? 'none' : '';
                    });
                  });
                }
              }

              renderEndpointTable('restTable', 'restFilter', DATA.restEndpoints, 'Trigger / Path');
              renderEndpointTable('batchTable', 'batchFilter', DATA.batchJobs, 'Trigger');

              // ---- multi-table transactions ----
              (function renderTransactions() {
                var table = document.getElementById('transactionsTable');
                var rows = DATA.multiTableTransactions;
                if (!rows.length) {
                  table.outerHTML = '<p class="empty-state">No single method was found directly touching more than one table.</p>';
                  return;
                }
                table.innerHTML = '<tr><th>Method</th><th>Domain</th><th>Tables</th><th>Entities</th><th>Spans domains?</th></tr>' +
                  rows.map(function (r) {
                    return '<tr><td class="mono">' + esc(r.className) + '.' + esc(r.methodName) + '</td>' +
                      '<td>' + chip(r.domain, 'domain') + '</td>' +
                      '<td>' + chipRow(r.tables) + '</td>' +
                      '<td>' + chipRow(r.entities) + '</td>' +
                      '<td><span class="badge ' + (r.spansMultipleDomains ? 'spans' : 'contained') + '">' + (r.spansMultipleDomains ? 'yes' : 'no') + '</span></td></tr>';
                  }).join('');
              })();
            })();
            </script>
            </body>
            </html>
            """;

    // ==========================================
    // HTML SUMMARY REPORT
    // ==========================================

    private String toHtmlReport(AnalysisReport report) {

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>Architecture Analysis Report</title>\n");
        html.append("<style>").append(reportCss()).append("</style>\n");
        html.append("</head>\n<body>\n");

        html.append("<h1>Architecture Analysis Report</h1>\n");
        html.append("<p class=\"generated\">Generated ")
                .append(
                        LocalDateTime.now().format(
                                DateTimeFormatter.ofPattern(
                                        "yyyy-MM-dd HH:mm:ss"
                                )
                        )
                )
                .append("</p>\n");

        appendSummarySection(html, report);
        appendArchitectureFindingsSection(html, report.architectureFindings());
        appendPersistenceFindingsSection(html, report.persistenceFindings());
        appendBeanResolutionSection(html, report.beanResolutions());
        appendDomainBoundarySection(html, report.domainCycles(), report.domainBoundaries());
        appendDomainOverviewSection(html, report.domains());
        appendEntryPointSection(
                html, report.entryPoints(), report.entryPointBehaviors()
        );

        html.append("<p class=\"footer\">Full data: <code>report.json</code>. ")
                .append("Full dependency/domain graphs: ")
                .append("<code>dependency-graph.dot</code>/<code>.mmd</code>, ")
                .append("<code>domain-graph.dot</code>/<code>.mmd</code>. ")
                .append("Interactive, clickable version of the domain graph: ")
                .append("<a href=\"domain-extraction-map.html\">")
                .append("domain-extraction-map.html</a>. ")
                .append("Cross-cutting Q&amp;A (domains, API-to-table traces, ")
                .append("batch job flows, extraction ranking, multi-table ")
                .append("transactions): ")
                .append("<a href=\"insights-report.html\">")
                .append("insights-report.html</a>. ")
                .append("Per-entry-point call sequences: ")
                .append("<code>sequence-diagrams/*.mmd</code> ")
                .append("(paste a <code>.mmd</code> file into a GitHub markdown ")
                .append("<code>```mermaid</code> code fence, or ")
                .append("mermaid.live, to render it).</p>\n");

        html.append("</body>\n</html>\n");

        return html.toString();
    }

    private void appendSummarySection(
            StringBuilder html, AnalysisReport report) {

        long circularDependencyClassFindings =
                report.architectureFindings().stream()
                        .filter(finding ->
                                finding.getType()
                                        == ArchitectureFindingType.CIRCULAR_DEPENDENCY
                        )
                        .count();

        html.append("<h2>Summary</h2>\n<div class=\"stats\">\n");
        appendStatCard(html, "Classes", report.classes().size());
        appendStatCard(html, "Entry points", report.entryPoints().size());
        appendStatCard(html, "Domains", report.domains().size());
        appendStatCard(
                html, "Architecture findings",
                report.architectureFindings().size()
        );
        appendStatCard(
                html, "Class-level cycles",
                circularDependencyClassFindings
        );
        appendStatCard(html, "Domain-level cycles", report.domainCycles().size());
        appendStatCard(
                html, "Persistence findings",
                report.persistenceFindings().size()
        );
        appendStatCard(
                html, "Mutating entry points",
                report.entryPointBehaviors().stream()
                        .filter(behavior ->
                                behavior.getClassification()
                                        == BehaviorClassification.MUTATING
                        )
                        .count()
        );
        appendStatCard(
                html, "Ambiguous bean resolutions",
                report.beanResolutions().stream()
                        .filter(resolution ->
                                resolution.getVerdict()
                                        == BeanResolutionVerdict.AMBIGUOUS
                        )
                        .count()
        );
        html.append("</div>\n");
    }

    private void appendStatCard(StringBuilder html, String label, long value) {

        html.append("<div class=\"stat-card\"><div class=\"stat-value\">")
                .append(value)
                .append("</div><div class=\"stat-label\">")
                .append(htmlEscape(label))
                .append("</div></div>\n");
    }

    private void appendArchitectureFindingsSection(
            StringBuilder html,
            List<ArchitectureFinding> findings) {

        html.append("<h2>Architecture Findings</h2>\n");

        if (findings.isEmpty()) {
            html.append("<p class=\"empty\">None found.</p>\n");
            return;
        }

        html.append("<table>\n<tr><th>Type</th><th>Description</th></tr>\n");

        for (ArchitectureFinding finding : findings) {

            html.append("<tr><td><span class=\"badge badge-")
                    .append(finding.getType().name().toLowerCase().replace('_', '-'))
                    .append("\">")
                    .append(htmlEscape(finding.getType().name()))
                    .append("</span></td><td>")
                    .append(htmlEscape(finding.getDescription()))
                    .append("</td></tr>\n");
        }

        html.append("</table>\n");
    }

    private void appendBeanResolutionSection(
            StringBuilder html,
            List<BeanResolution> resolutions) {

        html.append("<h2>Bean Resolution</h2>\n");

        if (resolutions.isEmpty()) {
            html.append("<p class=\"empty\">")
                    .append("No interfaces with multiple implementations found.")
                    .append("</p>\n");
            return;
        }

        html.append("<table>\n<tr><th>Interface</th><th>Verdict</th>")
                .append("<th>Description</th></tr>\n");

        for (BeanResolution resolution : resolutions) {

            html.append("<tr><td>")
                    .append(htmlEscape(resolution.getInterfaceName()))
                    .append("</td><td><span class=\"badge badge-")
                    .append(resolution.getVerdict().name().toLowerCase().replace('_', '-'))
                    .append("\">")
                    .append(htmlEscape(resolution.getVerdict().name()))
                    .append("</span></td><td>")
                    .append(htmlEscape(resolution.getDescription()))
                    .append("</td></tr>\n");
        }

        html.append("</table>\n");
    }

    private void appendPersistenceFindingsSection(
            StringBuilder html,
            List<PersistenceFinding> findings) {

        html.append("<h2>Persistence Findings</h2>\n");

        if (findings.isEmpty()) {
            html.append("<p class=\"empty\">None found.</p>\n");
            return;
        }

        html.append("<table>\n<tr><th>Type</th><th>Description</th></tr>\n");

        for (PersistenceFinding finding : findings) {

            html.append("<tr><td><span class=\"badge badge-")
                    .append(finding.getType().name().toLowerCase().replace('_', '-'))
                    .append("\">")
                    .append(htmlEscape(finding.getType().name()))
                    .append("</span></td><td>")
                    .append(htmlEscape(finding.getDescription()))
                    .append("</td></tr>\n");
        }

        html.append("</table>\n");
    }

    private void appendDomainBoundarySection(
            StringBuilder html,
            List<DomainCycle> domainCycles,
            List<DomainBoundaryInfo> domainBoundaries) {

        html.append("<h2>Domain Boundary Analysis</h2>\n");

        if (!domainCycles.isEmpty()) {

            html.append("<p><strong>Domain cycles:</strong></p>\n<ul>\n");

            for (DomainCycle cycle : domainCycles) {

                html.append("<li>")
                        .append(htmlEscape(cycle.getDescription()))
                        .append("</li>\n");
            }

            html.append("</ul>\n");
        }

        html.append("<table>\n<tr><th>Domain</th><th>Verdict</th>")
                .append("<th>Classes</th><th>Outgoing</th>")
                .append("<th>Incoming</th><th>Reason</th></tr>\n");

        for (DomainBoundaryInfo boundary : domainBoundaries) {

            html.append("<tr><td>")
                    .append(htmlEscape(boundary.getDomainName()))
                    .append("</td><td><span class=\"badge badge-")
                    .append(boundary.getVerdict().name().toLowerCase().replace('_', '-'))
                    .append("\">")
                    .append(htmlEscape(boundary.getVerdict().name()))
                    .append("</span></td><td>")
                    .append(boundary.getClassCount())
                    .append("</td><td>")
                    .append(boundary.getOutgoingDomainDependencies())
                    .append("</td><td>")
                    .append(boundary.getIncomingDomainDependencies())
                    .append("</td><td>")
                    .append(htmlEscape(boundary.getReason()))
                    .append("</td></tr>\n");
        }

        html.append("</table>\n");
    }

    private void appendDomainOverviewSection(
            StringBuilder html, List<DomainInfo> domains) {

        html.append("<h2>Domains</h2>\n");
        html.append("<table>\n<tr><th>Domain</th><th>Classes</th></tr>\n");

        List<DomainInfo> sorted = domains.stream()
                .sorted(Comparator.comparing(DomainInfo::getName))
                .toList();

        for (DomainInfo domain : sorted) {

            html.append("<tr><td>")
                    .append(htmlEscape(domain.getName()))
                    .append("</td><td>")
                    .append(domain.getClassCount())
                    .append("</td></tr>\n");
        }

        html.append("</table>\n");
    }

    /*
     * entryPoints and entryPointBehaviors are always the same
     * size and in the same order - FlowEngine produces exactly
     * one FlowPath per entry point, in input order, and
     * EntryPointBehaviorAnalyzer is a 1:1 map over those flows -
     * so they're zipped here by index rather than needing
     * EntryPointInfo to support equality/lookup.
     */
    private void appendEntryPointSection(
            StringBuilder html,
            List<EntryPointInfo> entryPoints,
            List<EntryPointBehavior> entryPointBehaviors) {

        html.append("<h2>Entry Points</h2>\n");

        if (entryPoints.isEmpty()) {
            html.append("<p class=\"empty\">None found.</p>\n");
            return;
        }

        html.append("<table>\n<tr><th>Trigger</th><th>Path</th>")
                .append("<th>Class.Method</th><th>Domain</th>")
                .append("<th>Behavior</th></tr>\n");

        for (int i = 0; i < entryPoints.size(); i++) {

            EntryPointInfo entryPoint = entryPoints.get(i);

            BehaviorClassification classification =
                    i < entryPointBehaviors.size()
                            ? entryPointBehaviors.get(i).getClassification()
                            : null;

            html.append("<tr><td>")
                    .append(htmlEscape(entryPoint.getTriggerType().name()))
                    .append("</td><td>")
                    .append(
                            entryPoint.getPath() != null
                                    ? htmlEscape(entryPoint.getPath())
                                    : "&#8212;"
                    )
                    .append("</td><td>")
                    .append(htmlEscape(entryPoint.getClassName()))
                    .append(".")
                    .append(htmlEscape(entryPoint.getMethodName()))
                    .append("</td><td>")
                    .append(htmlEscape(entryPoint.getDomain()))
                    .append("</td><td>")
                    .append(
                            classification != null
                                    ? "<span class=\"badge badge-"
                                            + classification.name()
                                                    .toLowerCase()
                                                    .replace('_', '-')
                                            + "\">"
                                            + htmlEscape(classification.name())
                                            + "</span>"
                                    : "&#8212;"
                    )
                    .append("</td></tr>\n");
        }

        html.append("</table>\n");
    }

    private String htmlEscape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String reportCss() {

        return """
                body { font-family: -apple-system, Segoe UI, Roboto, \
                Helvetica, Arial, sans-serif; margin: 2rem; color: #1a1a1a; \
                background: #ffffff; }
                h1 { margin-bottom: 0.25rem; }
                h2 { margin-top: 2.5rem; border-bottom: 2px solid #e0e0e0; \
                padding-bottom: 0.25rem; }
                p.generated { color: #666; margin-top: 0; }
                p.empty { color: #666; font-style: italic; }
                p.footer { color: #666; font-size: 0.85rem; margin-top: 3rem; \
                border-top: 1px solid #e0e0e0; padding-top: 1rem; }
                table { border-collapse: collapse; width: 100%; margin-top: 1rem; }
                th, td { text-align: left; padding: 0.5rem 0.75rem; \
                border-bottom: 1px solid #eee; vertical-align: top; }
                th { background: #fafafa; position: sticky; top: 0; }
                tr:hover { background: #fafafa; }
                code { background: #f4f4f4; padding: 0.1rem 0.3rem; \
                border-radius: 3px; font-size: 0.9em; }
                .stats { display: flex; flex-wrap: wrap; gap: 1rem; \
                margin-top: 1rem; }
                .stat-card { border: 1px solid #e0e0e0; border-radius: 8px; \
                padding: 1rem 1.5rem; min-width: 8rem; }
                .stat-value { font-size: 1.75rem; font-weight: 600; }
                .stat-label { color: #666; font-size: 0.85rem; }
                .badge { display: inline-block; padding: 0.15rem 0.5rem; \
                border-radius: 4px; font-size: 0.8rem; font-weight: 600; \
                white-space: nowrap; }
                .badge-circular-dependency { background: #ffcdd2; color: #7f0000; }
                .badge-god-class { background: #ffe0b2; color: #7a3c00; }
                .badge-repository-bypass { background: #ffe0b2; color: #7a3c00; }
                .badge-dead-component { background: #e0e0e0; color: #424242; }
                .badge-extraction-candidate { background: #c8e6c9; color: #1b5e20; }
                .badge-tangled { background: #ffcdd2; color: #7f0000; }
                .badge-blocked-by-cycle { background: #ffe0b2; color: #7a3c00; }
                .badge-n-plus-one-query-risk { background: #ffcdd2; color: #7f0000; }
                .badge-shared-entity-hotspot { background: #ffe0b2; color: #7a3c00; }
                .badge-read-only { background: #c8e6c9; color: #1b5e20; }
                .badge-mutating { background: #ffe0b2; color: #7a3c00; }
                .badge-resolved-by-primary { background: #c8e6c9; color: #1b5e20; }
                .badge-ambiguous { background: #ffe0b2; color: #7a3c00; }
                """;
    }
}

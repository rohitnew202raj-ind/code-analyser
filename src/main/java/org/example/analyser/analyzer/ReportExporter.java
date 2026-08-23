package org.example.analyser.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.ArchitectureFindingType;
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
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.FlowPath;
import org.example.analyser.model.MethodCallInfo;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

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
 * The HTML report is a summary, not a full data dump: it covers
 * findings, domain boundaries, and the entry-point inventory -
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
            List<DomainBoundaryInfo> domainBoundaries) {
    }

    public void export(
            Path outputDirectory,
            AnalysisReport report,
            DependencyGraph dependencyGraph) throws IOException {

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
        appendDomainBoundarySection(html, report.domainCycles(), report.domainBoundaries());
        appendDomainOverviewSection(html, report.domains());
        appendEntryPointSection(html, report.entryPoints());

        html.append("<p class=\"footer\">Full data: <code>report.json</code>. ")
                .append("Full dependency/domain graphs: ")
                .append("<code>dependency-graph.dot</code>/<code>.mmd</code>, ")
                .append("<code>domain-graph.dot</code>/<code>.mmd</code> ")
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

    private void appendEntryPointSection(
            StringBuilder html, List<EntryPointInfo> entryPoints) {

        html.append("<h2>Entry Points</h2>\n");

        if (entryPoints.isEmpty()) {
            html.append("<p class=\"empty\">None found.</p>\n");
            return;
        }

        html.append("<table>\n<tr><th>Trigger</th><th>Path</th>")
                .append("<th>Class.Method</th><th>Domain</th></tr>\n");

        for (EntryPointInfo entryPoint : entryPoints) {

            html.append("<tr><td>")
                    .append(htmlEscape(entryPoint.getTriggerType()))
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
                """;
    }
}

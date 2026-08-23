package org.example.analyser.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.analyser.model.ClassCouplingInfo;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.DependencyGraph;
import org.example.analyser.model.DependencyInfo;
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
import java.util.List;

/**
 * Writes the analysis out as structured JSON and Graphviz DOT
 * files, alongside the existing console report.
 *
 * The console-only output was fine for eyeballing a small
 * test project, but for an actual migration project you want
 * this feeding a spreadsheet, a graph-visualization tool, or
 * some other downstream process - not just scrollback.
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
            List<FlowPath> flows) {
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
}

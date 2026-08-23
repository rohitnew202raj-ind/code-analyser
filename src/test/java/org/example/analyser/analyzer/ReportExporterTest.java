package org.example.analyser.analyzer;

import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.ArchitectureFindingType;
import org.example.analyser.model.BehaviorClassification;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DependencyGraph;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DependencyType;
import org.example.analyser.model.DomainBoundaryInfo;
import org.example.analyser.model.DomainBoundaryVerdict;
import org.example.analyser.model.DomainCycle;
import org.example.analyser.model.DomainDependency;
import org.example.analyser.model.DomainInfo;
import org.example.analyser.model.EntryPointBehavior;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.FlowPath;
import org.example.analyser.model.MethodCallInfo;
import org.example.analyser.model.PersistenceFinding;
import org.example.analyser.model.PersistenceFindingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExporterTest {

    private final ReportExporter reportExporter = new ReportExporter();

    @Test
    void writesAllExpectedFiles(@TempDir Path outputDirectory) throws Exception {

        reportExporter.export(
                outputDirectory, minimalReport(), minimalGraph()
        );

        assertThat(outputDirectory.resolve("report.json")).exists();
        assertThat(outputDirectory.resolve("dependency-graph.dot")).exists();
        assertThat(outputDirectory.resolve("domain-graph.dot")).exists();
        assertThat(outputDirectory.resolve("dependency-graph.mmd")).exists();
        assertThat(outputDirectory.resolve("domain-graph.mmd")).exists();
        assertThat(outputDirectory.resolve("report.html")).exists();
        assertThat(outputDirectory.resolve("sequence-diagrams")).isDirectory();
    }

    @Test
    void dependencyMermaidContainsNodesAndEdges(
            @TempDir Path outputDirectory) throws Exception {

        reportExporter.export(
                outputDirectory, minimalReport(), minimalGraph()
        );

        String mermaid = Files.readString(
                outputDirectory.resolve("dependency-graph.mmd")
        );

        assertThat(mermaid).startsWith("flowchart LR");
        assertThat(mermaid).contains("OrderController");
        assertThat(mermaid).contains("OrderService");
        assertThat(mermaid)
                .contains("OrderController -->|SERVICE_DEPENDENCY| OrderService");
    }

    @Test
    void domainMermaidColorsNodesByBoundaryVerdict(
            @TempDir Path outputDirectory) throws Exception {

        reportExporter.export(
                outputDirectory, minimalReport(), minimalGraph()
        );

        String mermaid = Files.readString(
                outputDirectory.resolve("domain-graph.mmd")
        );

        assertThat(mermaid).contains("classDef extractionCandidate");
        assertThat(mermaid).contains("class order extractionCandidate;");
    }

    @Test
    void htmlReportEscapesSpecialCharactersInDescriptions() throws Exception {

        Path outputDirectory = Files.createTempDirectory("report-exporter-test");

        try {

            ArchitectureFinding finding = new ArchitectureFinding(
                    ArchitectureFindingType.GOD_CLASS,
                    List.of("Order<Impl>"),
                    "Order<Impl> depends on \"too many\" & other classes"
            );

            ReportExporter.AnalysisReport report = new ReportExporter.AnalysisReport(
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(finding), List.of(), List.of(), List.of(), List.of()
            );

            reportExporter.export(
                    outputDirectory, report, new DependencyGraph(List.of(), List.of())
            );

            String html = Files.readString(
                    outputDirectory.resolve("report.html")
            );

            assertThat(html).doesNotContain("<Impl>");
            assertThat(html).contains("Order&lt;Impl&gt;");
            assertThat(html).contains("&amp;");
            assertThat(html).contains("&quot;too many&quot;");

        } finally {

            Files.walk(outputDirectory)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void htmlReportListsArchitectureFindingsAndEntryPoints(
            @TempDir Path outputDirectory) throws Exception {

        reportExporter.export(
                outputDirectory, minimalReport(), minimalGraph()
        );

        String html = Files.readString(
                outputDirectory.resolve("report.html")
        );

        assertThat(html).contains("GOD_CLASS");
        assertThat(html).contains("OrderController.create");
        assertThat(html).contains("EXTRACTION_CANDIDATE");
    }

    @Test
    void htmlReportListsPersistenceFindings(
            @TempDir Path outputDirectory) throws Exception {

        reportExporter.export(
                outputDirectory, minimalReport(), minimalGraph()
        );

        String html = Files.readString(
                outputDirectory.resolve("report.html")
        );

        assertThat(html).contains("N_PLUS_ONE_QUERY_RISK");
        assertThat(html).contains("OrderService.processAll");
    }

    @Test
    void writesASequenceDiagramPerEntryPoint(
            @TempDir Path outputDirectory) throws Exception {

        reportExporter.export(
                outputDirectory, minimalReport(), minimalGraph()
        );

        Path sequenceDiagram = outputDirectory
                .resolve("sequence-diagrams")
                .resolve("OrderController-create.mmd");

        assertThat(sequenceDiagram).exists();

        String mermaid = Files.readString(sequenceDiagram);

        assertThat(mermaid).startsWith("sequenceDiagram");
        assertThat(mermaid).contains("participant OrderController");
        assertThat(mermaid).contains("participant OrderService");
        assertThat(mermaid)
                .contains("OrderController->>OrderService: placeOrder()");
    }

    @Test
    void htmlReportShowsBehaviorClassificationForEntryPoints(
            @TempDir Path outputDirectory) throws Exception {

        reportExporter.export(
                outputDirectory, minimalReport(), minimalGraph()
        );

        String html = Files.readString(
                outputDirectory.resolve("report.html")
        );

        assertThat(html).contains("badge-mutating");
        assertThat(html).contains("MUTATING");
    }

    private DependencyGraph minimalGraph() {

        ClassInfo controller = new ClassInfo();
        controller.setName("OrderController");
        controller.setType("CONTROLLER");

        ClassInfo service = new ClassInfo();
        service.setName("OrderService");
        service.setType("SERVICE");

        return new DependencyGraph(
                List.of(controller, service),
                List.of(
                        new DependencyInfo(
                                "OrderController", "OrderService",
                                "service", DependencyType.SERVICE_DEPENDENCY
                        )
                )
        );
    }

    private ReportExporter.AnalysisReport minimalReport() {

        DomainInfo orderDomain = new DomainInfo("order");

        EntryPointInfo entryPoint = new EntryPointInfo(
                "OrderController", "com.acme.order", "create",
                "POST", "/orders", "order"
        );

        ArchitectureFinding finding = new ArchitectureFinding(
                ArchitectureFindingType.GOD_CLASS,
                List.of("OrderController"),
                "OrderController depends on too many classes"
        );

        DomainBoundaryInfo boundary = new DomainBoundaryInfo(
                "order", 1, 0, 0,
                0, DomainBoundaryVerdict.EXTRACTION_CANDIDATE,
                "order has no cross-domain dependencies"
        );

        PersistenceFinding persistenceFinding = new PersistenceFinding(
                PersistenceFindingType.N_PLUS_ONE_QUERY_RISK,
                List.of("OrderService"),
                "OrderService.processAll calls OrderRepository.findById "
                        + "inside a loop - potential N+1 query pattern"
        );

        MethodCallInfo step = new MethodCallInfo(
                "OrderController", "create", "OrderService", "placeOrder"
        );

        FlowPath flow = new FlowPath(
                entryPoint, List.of(step), List.of(), List.of(), false
        );

        EntryPointBehavior behavior = new EntryPointBehavior(
                entryPoint, BehaviorClassification.MUTATING, 1
        );

        return new ReportExporter.AnalysisReport(
                List.of(),
                List.of(),
                List.of(),
                List.of(orderDomain),
                List.of(),
                List.of(entryPoint),
                List.of(),
                List.of(),
                List.of(),
                List.of(flow),
                List.of(finding),
                List.<DomainCycle>of(),
                List.of(boundary),
                List.of(persistenceFinding),
                List.of(behavior)
        );
    }
}

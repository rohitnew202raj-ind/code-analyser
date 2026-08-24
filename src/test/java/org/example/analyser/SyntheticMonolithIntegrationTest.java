package org.example.analyser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the full analyzer pipeline against a committed, versioned
 * synthetic-monolith fixture (src/test/resources/fixtures/synthetic-monolith)
 * - a small but multi-domain Spring project deliberately constructed
 * to trip every finding type built across every phase of this tool.
 *
 * {@code AnalyzerRunner.run(...)} is invoked directly on the real
 * Spring-wired bean rather than relying on Spring Boot to invoke it
 * as a {@code CommandLineRunner} during context startup - confirmed
 * empirically that {@code @SpringBootTest} does not do that on this
 * Spring Boot version, so calling it explicitly is both simpler and
 * doesn't depend on that behavior at all.
 *
 * WHY THIS EXISTS: every phase up to now was verified by hand - build
 * the jar, write a one-off throwaway sample project in a scratch
 * directory, run it, eyeball the console output, delete everything.
 * That caught real bugs (see LIMITATIONS.md's "Also fixed after
 * real-world testing" notes), but none of it runs again on the next
 * change - a regression that silently stopped, say,
 * DeadComponentAnalyzer from firing would go unnoticed. This test is
 * the automated, rerunnable version of that same manual step, wired
 * into {@code mvn test} (and therefore CI) instead of living only in
 * session history.
 *
 * SCOPE (documented, not a bug): this asserts on *presence* of each
 * finding type/classification/verdict the fixture was built to
 * trigger, not on exact counts or exact wording - the goal is "did
 * this category of detection stop firing entirely," not pinning
 * every analyzer's output text in a second place that would need
 * updating every time a message changes for unrelated reasons.
 */
@SpringBootTest(
        classes = ArchitectureAnalyzerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyntheticMonolithIntegrationTest {

    private static final String FIXTURE_PATH =
            "src/test/resources/fixtures/synthetic-monolith";

    @Autowired
    private AnalyzerRunner analyzerRunner;

    private Path outputDirectory;
    private JsonNode report;

    @BeforeAll
    void analyzeFixtureOnce() throws Exception {

        outputDirectory =
                Files.createTempDirectory("synthetic-monolith-output");

        analyzerRunner.run(FIXTURE_PATH, outputDirectory.toString());

        report = new ObjectMapper().readTree(
                outputDirectory.resolve("report.json").toFile()
        );
    }

    @AfterAll
    void cleanUpOutputDirectory() throws IOException {

        if (outputDirectory == null || !Files.exists(outputDirectory)) {
            return;
        }

        try (var paths = Files.walk(outputDirectory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> path.toFile().delete());
        }
    }

    @Test
    void writesAllExpectedOutputFiles() {

        assertThat(outputDirectory.resolve("report.json")).exists();
        assertThat(outputDirectory.resolve("dependency-graph.dot")).exists();
        assertThat(outputDirectory.resolve("domain-graph.dot")).exists();
        assertThat(outputDirectory.resolve("dependency-graph.mmd")).exists();
        assertThat(outputDirectory.resolve("domain-graph.mmd")).exists();
        assertThat(outputDirectory.resolve("report.html")).exists();
        assertThat(outputDirectory.resolve("sequence-diagrams")).isDirectory();
    }

    @Test
    void discoversAllFourDomainsFromDistinctPackages() {

        // Guards against PackageDomainExtractor collapsing every
        // class into one domain, as happened on several flatter
        // hand-written phase fixtures earlier in this project.
        assertThat(fieldValues(report.get("domains"), "name"))
                .containsExactlyInAnyOrder(
                        "common", "order", "payment", "inventory"
                );
    }

    @Test
    void findsAllFourArchitectureFindingTypes() {

        assertThat(fieldValues(report.get("architectureFindings"), "type"))
                .contains(
                        "CIRCULAR_DEPENDENCY",
                        "GOD_CLASS",
                        "REPOSITORY_BYPASS",
                        "DEAD_COMPONENT"
                );
    }

    @Test
    void flagsTheDeliberatelyUnreferencedOrphanServiceAsDead() {

        // The exact shape of the Phase 3 bug: a test fixture that
        // accidentally gives a "should be dead" class an incoming
        // edge silently defeats this check without failing loudly.
        boolean orphanFlagged =
                StreamSupport.stream(
                                report.get("architectureFindings")
                                        .spliterator(), false
                        )
                        .filter(finding ->
                                "DEAD_COMPONENT".equals(
                                        finding.get("type").asText()
                                )
                        )
                        .flatMap(finding ->
                                StreamSupport.stream(
                                        finding.get("classes")
                                                .spliterator(), false
                                )
                        )
                        .anyMatch(name ->
                                "OrphanService".equals(name.asText())
                        );

        assertThat(orphanFlagged).isTrue();
    }

    @Test
    void findsBothPersistenceFindingTypes() {

        assertThat(fieldValues(report.get("persistenceFindings"), "type"))
                .contains("N_PLUS_ONE_QUERY_RISK", "SHARED_ENTITY_HOTSPOT");
    }

    @Test
    void findsTheOrderPaymentDomainCycle() {

        assertThat(report.get("domainCycles")).isNotEmpty();

        List<String> domainsInCycle = new ArrayList<>();

        report.get("domainCycles").get(0).get("domains")
                .forEach(node -> domainsInCycle.add(node.asText()));

        assertThat(domainsInCycle)
                .containsExactlyInAnyOrder("order", "payment");
    }

    @Test
    void findsAllThreeDomainBoundaryVerdicts() {

        assertThat(fieldValues(report.get("domainBoundaries"), "verdict"))
                .contains(
                        "TANGLED", "BLOCKED_BY_CYCLE", "EXTRACTION_CANDIDATE"
                );
    }

    @Test
    void findsBothBeanResolutionVerdicts() {

        assertThat(fieldValues(report.get("beanResolutions"), "verdict"))
                .contains("RESOLVED_BY_PRIMARY", "AMBIGUOUS");
    }

    @Test
    void classifiesBothReadOnlyAndMutatingEntryPoints() {

        assertThat(fieldValues(report.get("entryPointBehaviors"), "classification"))
                .contains("READ_ONLY", "MUTATING");
    }

    @Test
    void discoversRestAndScheduledEntryPointsTogether() {

        assertThat(fieldValues(report.get("entryPoints"), "triggerType"))
                .contains("GET", "POST", "SCHEDULED");
    }

    private List<String> fieldValues(JsonNode array, String fieldName) {

        List<String> values = new ArrayList<>();

        array.forEach(node ->
                values.add(node.get(fieldName).asText())
        );

        return values;
    }
}

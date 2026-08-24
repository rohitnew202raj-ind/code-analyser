package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.DomainExtractionResult;
import org.example.analyser.model.DomainInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DomainAnalyzerTest {

    private final DomainAnalyzer domainAnalyzer = new DomainAnalyzer();

    @Test
    void packageBasedStrategyWinsWhenPackagesAlreadyEncodeRealDomains() {

        List<ClassInfo> classes = List.of(
                classInfo("OrderWeb", "com.acme.orders.web"),
                classInfo("OrderFlow", "com.acme.orders.batch"),
                classInfo("ReportRunner", "com.acme.reporting.batch")
        );

        DomainExtractionResult result =
                domainAnalyzer.analyze(classes, List.of());

        assertThat(result.getStrategy()).isEqualTo("package-based");

        assertThat(result.getDomains())
                .extracting(DomainInfo::getName)
                .containsExactlyInAnyOrder("orders", "reporting");
    }

    @Test
    void classNameStrategyWinsWhenPackagesAreLayeredButNamesEncodeTheDomain() {

        List<ClassInfo> classes = List.of(
                classInfo("PatientController", "com.acme.controller"),
                classInfo("PatientService", "com.acme.service"),
                classInfo("PatientRepository", "com.acme.repository"),
                classInfo("BillingController", "com.acme.controller"),
                classInfo("BillingService", "com.acme.service")
        );

        DomainExtractionResult result =
                domainAnalyzer.analyze(classes, List.of());

        assertThat(result.getStrategy()).isEqualTo("class-name");

        assertThat(result.getDomains())
                .extracting(DomainInfo::getName)
                .containsExactlyInAnyOrder("Patient", "Billing");
    }

    @Test
    void entityUsageStrategyWinsWhenNeitherPackagesNorNamesCarryASignal() {

        List<ClassInfo> classes = List.of(
                classInfo("Zenith", "com.acme.service"),
                classInfo("Orbit", "com.acme.handler"),
                classInfo("Nimbus", "com.acme.repository"),
                classInfo("PatientRecord", "com.acme.entity")
        );

        List<CrudOperationInfo> crudOperations = List.of(
                new CrudOperationInfo(
                        "Zenith", "run",
                        "Nimbus", "save",
                        "CREATE_OR_UPDATE", "PatientRecord", "patient_record"
                ),
                new CrudOperationInfo(
                        "Orbit", "run",
                        "Nimbus", "findById",
                        "READ", "PatientRecord", "patient_record"
                )
        );

        DomainExtractionResult result =
                domainAnalyzer.analyze(classes, crudOperations);

        assertThat(result.getStrategy()).isEqualTo("entity-usage");

        assertThat(result.getDomains())
                .extracting(DomainInfo::getName)
                .containsExactly("PatientRecord");

        assertThat(result.getDomains().get(0).getClassCount()).isEqualTo(4);
    }

    @Test
    void classesUncoveredByTheWinningStrategyFallBackToCore() {

        List<ClassInfo> classes = List.of(
                classInfo("PatientController", "com.acme.controller"),
                classInfo("PatientService", "com.acme.service"),
                classInfo("UnrelatedThing", "com.acme.misc")
        );

        DomainExtractionResult result =
                domainAnalyzer.analyze(classes, List.of());

        assertThat(result.getStrategy()).isEqualTo("class-name");

        DomainInfo coreDomain =
                result.getDomains().stream()
                        .filter(domain -> domain.getName().equals("core"))
                        .findFirst()
                        .orElseThrow();

        assertThat(coreDomain.getClasses())
                .extracting(ClassInfo::getName)
                .containsExactly("UnrelatedThing");
    }

    @Test
    void exposesConfidenceScoresForAllThreeStrategies() {

        List<ClassInfo> classes = List.of(
                classInfo("OrderWeb", "com.acme.orders.web"),
                classInfo("OrderFlow", "com.acme.orders.batch")
        );

        DomainExtractionResult result =
                domainAnalyzer.analyze(classes, List.of());

        Map<String, Double> scores = result.getStrategyConfidence();

        assertThat(scores).containsKeys(
                "package-based", "class-name", "entity-usage"
        );

        assertThat(scores.get("package-based")).isEqualTo(1.0);
    }

    private ClassInfo classInfo(String name, String packageName) {

        ClassInfo classInfo = new ClassInfo();
        classInfo.setName(name);
        classInfo.setPackageName(packageName);
        return classInfo;
    }
}

package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EntityUsageDomainExtractorTest {

    @Test
    void clustersControllerServiceAndRepositoryAroundTheEntityTheyShare() {

        List<ClassInfo> classes =
                classesNamed(
                        "PatientController", "PatientService",
                        "PatientRepository", "PatientEntity"
                );

        List<CrudOperationInfo> crudOperations = List.of(
                crudOp("PatientService", "PatientRepository", "PatientEntity"),
                crudOp("PatientController", "PatientRepository", "PatientEntity")
        );

        EntityUsageDomainExtractor extractor =
                EntityUsageDomainExtractor.fit(classes, crudOperations);

        assertThat(extractor.domainOf("PatientController")).isEqualTo("Patient");
        assertThat(extractor.domainOf("PatientService")).isEqualTo("Patient");
        assertThat(extractor.domainOf("PatientRepository")).isEqualTo("Patient");
        assertThat(extractor.domainOf("PatientEntity")).isEqualTo("Patient");

        assertThat(extractor.coverage()).isCloseTo(1.0, within(0.001));
    }

    @Test
    void assignsAClassTouchingMultipleEntitiesToTheMostFrequentOne() {

        List<ClassInfo> classes =
                classesNamed("OrchestratorService", "OrderEntity", "PaymentEntity");

        List<CrudOperationInfo> crudOperations = List.of(
                crudOp("OrchestratorService", "OrderRepository", "OrderEntity"),
                crudOp("OrchestratorService", "OrderRepository", "OrderEntity"),
                crudOp("OrchestratorService", "PaymentRepository", "PaymentEntity")
        );

        EntityUsageDomainExtractor extractor =
                EntityUsageDomainExtractor.fit(classes, crudOperations);

        assertThat(extractor.domainOf("OrchestratorService")).isEqualTo("Order");
    }

    @Test
    void classesWithNoCrudInvolvementGetNoAssignmentAndLowerCoverage() {

        List<ClassInfo> classes =
                classesNamed("AppConfig", "OrderEntity", "OrderService");

        List<CrudOperationInfo> crudOperations = List.of(
                crudOp("OrderService", "OrderRepository", "OrderEntity")
        );

        EntityUsageDomainExtractor extractor =
                EntityUsageDomainExtractor.fit(classes, crudOperations);

        assertThat(extractor.domainOf("AppConfig")).isNull();
        assertThat(extractor.coverage()).isCloseTo(2.0 / 3.0, within(0.001));
    }

    @Test
    void emptyCrudOperationsYieldsZeroCoverage() {

        List<ClassInfo> classes = classesNamed("Foo", "Bar");

        EntityUsageDomainExtractor extractor =
                EntityUsageDomainExtractor.fit(classes, List.of());

        assertThat(extractor.coverage()).isZero();
        assertThat(extractor.domainOf("Foo")).isNull();
    }

    private CrudOperationInfo crudOp(
            String sourceClass,
            String repositoryClass,
            String entityClass) {

        return new CrudOperationInfo(
                sourceClass,
                "someMethod",
                repositoryClass,
                "save",
                "CREATE_OR_UPDATE",
                entityClass,
                "some_table"
        );
    }

    private List<ClassInfo> classesNamed(String... names) {

        return List.of(names).stream()
                .map(name -> {
                    ClassInfo classInfo = new ClassInfo();
                    classInfo.setName(name);
                    classInfo.setPackageName("com.acme");
                    return classInfo;
                })
                .toList();
    }
}

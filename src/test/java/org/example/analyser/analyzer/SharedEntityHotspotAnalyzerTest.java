package org.example.analyser.analyzer;

import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.PersistenceFinding;
import org.example.analyser.model.PersistenceFindingType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SharedEntityHotspotAnalyzerTest {

    private final SharedEntityHotspotAnalyzer analyzer =
            new SharedEntityHotspotAnalyzer();

    @Test
    void flagsAnEntityTouchedByAtLeastTheThresholdNumberOfClasses() {

        List<CrudOperationInfo> operations = List.of(
                crudOperation("OrderService"),
                crudOperation("InvoiceService"),
                crudOperation("ReportingService")
        );

        List<PersistenceFinding> findings =
                analyzer.analyze(operations);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getType())
                .isEqualTo(PersistenceFindingType.SHARED_ENTITY_HOTSPOT);
        assertThat(findings.get(0).getClasses())
                .containsExactlyInAnyOrder(
                        "OrderService", "InvoiceService", "ReportingService"
                );
        assertThat(findings.get(0).getDescription())
                .contains("Order")
                .contains("3 different classes");
    }

    @Test
    void doesNotFlagAnEntityBelowTheThreshold() {

        List<CrudOperationInfo> operations = List.of(
                crudOperation("OrderService"),
                crudOperation("InvoiceService")
        );

        List<PersistenceFinding> findings =
                analyzer.analyze(operations);

        assertThat(findings).isEmpty();
    }

    @Test
    void countsDistinctClassesNotDistinctCalls() {

        // Same class calling the repository three times should
        // not count as three distinct touching classes.
        List<CrudOperationInfo> operations = List.of(
                crudOperation("OrderService"),
                crudOperation("OrderService"),
                crudOperation("OrderService")
        );

        List<PersistenceFinding> findings =
                analyzer.analyze(operations);

        assertThat(findings).isEmpty();
    }

    @Test
    void ignoresOperationsWithNoResolvedEntityClass() {

        CrudOperationInfo unresolved = new CrudOperationInfo(
                "MysteryService", "doStuff",
                "MysteryRepository", "save",
                "CREATE_OR_UPDATE", null, null
        );

        List<PersistenceFinding> findings =
                analyzer.analyze(List.of(unresolved));

        assertThat(findings).isEmpty();
    }

    private CrudOperationInfo crudOperation(String sourceClass) {

        return new CrudOperationInfo(
                sourceClass,
                "run",
                "OrderRepository",
                "save",
                "CREATE_OR_UPDATE",
                "Order",
                "order"
        );
    }
}

package org.example.analyser.analyzer;

import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.PersistenceFinding;
import org.example.analyser.model.PersistenceFindingType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NPlusOneQueryAnalyzerTest {

    private final NPlusOneQueryAnalyzer analyzer =
            new NPlusOneQueryAnalyzer();

    @Test
    void flagsAReadCallMadeInsideALoop() {

        CrudOperationInfo operation = crudOperation(
                "READ", true
        );

        List<PersistenceFinding> findings =
                analyzer.analyze(List.of(operation));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getType())
                .isEqualTo(PersistenceFindingType.N_PLUS_ONE_QUERY_RISK);
        assertThat(findings.get(0).getDescription())
                .contains("OrderService.processAll")
                .contains("OrderRepository.findById")
                .contains("inside a loop");
    }

    @Test
    void doesNotFlagAReadCallOutsideALoop() {

        CrudOperationInfo operation = crudOperation(
                "READ", false
        );

        List<PersistenceFinding> findings =
                analyzer.analyze(List.of(operation));

        assertThat(findings).isEmpty();
    }

    @Test
    void doesNotFlagAWriteCallInsideALoop() {

        CrudOperationInfo operation = crudOperation(
                "CREATE_OR_UPDATE", true
        );

        List<PersistenceFinding> findings =
                analyzer.analyze(List.of(operation));

        assertThat(findings).isEmpty();
    }

    private CrudOperationInfo crudOperation(
            String operationType, boolean insideLoop) {

        CrudOperationInfo operation = new CrudOperationInfo(
                "OrderService",
                "processAll",
                "OrderRepository",
                "findById",
                operationType,
                "Order",
                "order"
        );

        operation.setInsideLoop(insideLoop);
        return operation;
    }
}

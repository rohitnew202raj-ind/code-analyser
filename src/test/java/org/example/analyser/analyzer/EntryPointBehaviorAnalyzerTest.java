package org.example.analyser.analyzer;

import org.example.analyser.model.BehaviorClassification;
import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.EntityMutationInfo;
import org.example.analyser.model.EntryPointBehavior;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.FlowPath;
import org.example.analyser.model.TriggerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntryPointBehaviorAnalyzerTest {

    private final EntryPointBehaviorAnalyzer analyzer =
            new EntryPointBehaviorAnalyzer();

    @Test
    void classifiesAFlowWithOnlyReadsAsReadOnly() {

        FlowPath flow = flow(
                List.of(crudOperation("READ")),
                List.of()
        );

        List<EntryPointBehavior> behaviors =
                analyzer.analyze(List.of(flow));

        assertThat(behaviors).hasSize(1);
        assertThat(behaviors.get(0).getClassification())
                .isEqualTo(BehaviorClassification.READ_ONLY);
        assertThat(behaviors.get(0).getWriteOperationCount()).isZero();
    }

    @Test
    void classifiesAFlowWithAWriteOperationAsMutating() {

        FlowPath flow = flow(
                List.of(
                        crudOperation("READ"),
                        crudOperation("CREATE_OR_UPDATE")
                ),
                List.of()
        );

        List<EntryPointBehavior> behaviors =
                analyzer.analyze(List.of(flow));

        assertThat(behaviors.get(0).getClassification())
                .isEqualTo(BehaviorClassification.MUTATING);
        assertThat(behaviors.get(0).getWriteOperationCount()).isEqualTo(1);
    }

    @Test
    void classifiesAFlowWithAnEntityMutationAsMutatingEvenWithNoWriteOperations() {

        EntityMutationInfo mutation = new EntityMutationInfo(
                "OrderService", "cancel", "Order",
                "status", "SET", "order"
        );

        FlowPath flow = flow(
                List.of(crudOperation("READ")),
                List.of(mutation)
        );

        List<EntryPointBehavior> behaviors =
                analyzer.analyze(List.of(flow));

        assertThat(behaviors.get(0).getClassification())
                .isEqualTo(BehaviorClassification.MUTATING);
        assertThat(behaviors.get(0).getWriteOperationCount()).isZero();
    }

    @Test
    void treatsAnUnrecognizedCustomQueryAsMutatingConservatively() {

        FlowPath flow = flow(
                List.of(crudOperation("CUSTOM_QUERY")),
                List.of()
        );

        List<EntryPointBehavior> behaviors =
                analyzer.analyze(List.of(flow));

        assertThat(behaviors.get(0).getClassification())
                .isEqualTo(BehaviorClassification.MUTATING);
    }

    @Test
    void classifiesAFlowWithNoDatabaseActivityAsReadOnly() {

        FlowPath flow = flow(List.of(), List.of());

        List<EntryPointBehavior> behaviors =
                analyzer.analyze(List.of(flow));

        assertThat(behaviors.get(0).getClassification())
                .isEqualTo(BehaviorClassification.READ_ONLY);
    }

    private FlowPath flow(
            List<CrudOperationInfo> databaseOperations,
            List<EntityMutationInfo> entityMutations) {

        EntryPointInfo entryPoint = new EntryPointInfo(
                "OrderController", "com.acme.order", "create",
                TriggerType.POST, "/orders", "order"
        );

        return new FlowPath(
                entryPoint, List.of(), databaseOperations,
                entityMutations, false
        );
    }

    private CrudOperationInfo crudOperation(String operation) {

        return new CrudOperationInfo(
                "OrderService", "run", "OrderRepository", "call",
                operation, "Order", "order"
        );
    }
}

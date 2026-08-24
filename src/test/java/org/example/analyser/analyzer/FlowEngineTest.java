package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.EntityMutationInfo;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.FlowPath;
import org.example.analyser.model.MethodCallInfo;
import org.example.analyser.model.TriggerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class FlowEngineTest {

    private final FlowEngine flowEngine = new FlowEngine();

    @Test
    void walksCallChainFromControllerToDatabaseOperation() {

        EntryPointInfo entryPoint =
                new EntryPointInfo(
                        "OrderController", "com.acme.order", "create",
                        TriggerType.POST, "/orders", "order"
                );

        List<MethodCallInfo> calls = List.of(
                new MethodCallInfo(
                        "OrderController", "create",
                        "OrderServiceImpl", "create"
                ),
                new MethodCallInfo(
                        "OrderServiceImpl", "create",
                        "OrderValidator", "validateCreate"
                )
        );

        List<CrudOperationInfo> crud = List.of(
                new CrudOperationInfo(
                        "OrderServiceImpl", "create",
                        "OrderRepository", "save",
                        "CREATE_OR_UPDATE", "Order", "order_tbl"
                )
        );

        List<FlowPath> flows =
                flowEngine.analyze(
                        List.of(entryPoint), calls, crud, List.of(), List.of()
                );

        assertThat(flows).hasSize(1);

        FlowPath flow = flows.get(0);

        assertThat(flow.getEntryPoint()).isEqualTo(entryPoint);
        assertThat(flow.isTruncated()).isFalse();

        assertThat(flow.getSteps())
                .extracting(
                        MethodCallInfo::getSourceClass,
                        MethodCallInfo::getTargetClass
                )
                .containsExactlyInAnyOrder(
                        tuple("OrderController", "OrderServiceImpl"),
                        tuple("OrderServiceImpl", "OrderValidator")
                );

        assertThat(flow.getDatabaseOperations())
                .containsExactly(crud.get(0));
    }

    @Test
    void bridgesAnInterfaceBoundaryWhenTheImplementationIsKnown() {

        // Same shape as the real-world case documented on
        // FlowEngine: a call is recorded against the field's
        // declared (interface) type, but the callee's own
        // outgoing calls are recorded under its implementation
        // class's name. Given the ClassInfo that says
        // OrderServiceImpl implements OrderService, the walk
        // should continue into the implementation's own calls
        // (and reach the CRUD operation recorded there) instead
        // of dead-ending at the interface node.
        EntryPointInfo entryPoint =
                new EntryPointInfo(
                        "OrderController", "com.acme.order", "create",
                        TriggerType.POST, "/orders", "order"
                );

        List<MethodCallInfo> calls = List.of(
                new MethodCallInfo(
                        "OrderController", "create",
                        "OrderService", "create"
                ),
                new MethodCallInfo(
                        "OrderServiceImpl", "create",
                        "OrderRepository", "save"
                )
        );

        List<CrudOperationInfo> crud = List.of(
                new CrudOperationInfo(
                        "OrderServiceImpl", "create",
                        "OrderRepository", "save",
                        "CREATE_OR_UPDATE", "Order", "order_tbl"
                )
        );

        ClassInfo orderServiceImpl = new ClassInfo();
        orderServiceImpl.setName("OrderServiceImpl");
        orderServiceImpl.getImplementedTypes().add("OrderService");

        List<FlowPath> flows =
                flowEngine.analyze(
                        List.of(entryPoint), calls, crud, List.of(),
                        List.of(orderServiceImpl)
                );

        FlowPath flow = flows.get(0);

        assertThat(flow.getSteps())
                .extracting(
                        MethodCallInfo::getSourceClass,
                        MethodCallInfo::getTargetClass
                )
                .containsExactlyInAnyOrder(
                        tuple("OrderController", "OrderService"),
                        tuple("OrderServiceImpl", "OrderRepository")
                );

        assertThat(flow.getDatabaseOperations())
                .containsExactly(crud.get(0));
    }

    @Test
    void stillDeadEndsAtAnInterfaceWithNoKnownImplementationInTheAnalyzedSource() {

        // The remaining, documented limitation: when the
        // implementing class isn't part of the analyzed source
        // set (e.g. it lives in a dependency jar), there's no
        // ClassInfo to bridge from, so the walk still dead-ends
        // at the interface node rather than guessing.
        EntryPointInfo entryPoint =
                new EntryPointInfo(
                        "OrderController", "com.acme.order", "create",
                        TriggerType.POST, "/orders", "order"
                );

        List<MethodCallInfo> calls = List.of(
                new MethodCallInfo(
                        "OrderController", "create",
                        "OrderService", "create"
                ),
                new MethodCallInfo(
                        "OrderServiceImpl", "create",
                        "OrderRepository", "save"
                )
        );

        List<FlowPath> flows =
                flowEngine.analyze(
                        List.of(entryPoint), calls, List.of(), List.of(),
                        List.of()
                );

        FlowPath flow = flows.get(0);

        assertThat(flow.getSteps()).hasSize(1);
        assertThat(flow.getSteps().get(0).getTargetClass())
                .isEqualTo("OrderService");
    }

    @Test
    void collectsDatabaseOperationsAndEntityMutationsReachableFromEntryPoint() {

        EntryPointInfo entryPoint =
                new EntryPointInfo(
                        "OrderServiceImpl", "com.acme.order", "create",
                        TriggerType.POST, "/orders", "order"
                );

        List<MethodCallInfo> calls = List.of(
                new MethodCallInfo(
                        "OrderServiceImpl", "create",
                        "OrderMapper", "toEntity"
                )
        );

        List<CrudOperationInfo> crud = List.of(
                new CrudOperationInfo(
                        "OrderServiceImpl", "create",
                        "OrderRepository", "save",
                        "CREATE_OR_UPDATE", "Order", "order_tbl"
                )
        );

        List<EntityMutationInfo> mutations = List.of(
                new EntityMutationInfo(
                        "OrderMapper", "toEntity",
                        "Order", "status",
                        "FIELD_MUTATION", "order_tbl"
                )
        );

        List<FlowPath> flows =
                flowEngine.analyze(
                        List.of(entryPoint), calls, crud, mutations, List.of()
                );

        FlowPath flow = flows.get(0);

        assertThat(flow.getDatabaseOperations())
                .containsExactly(crud.get(0));

        assertThat(flow.getEntityMutations())
                .containsExactly(mutations.get(0));
    }

    @Test
    void doesNotLoopForeverOnACallCycle() {

        EntryPointInfo entryPoint =
                new EntryPointInfo(
                        "A", "com.acme", "run",
                        TriggerType.SCHEDULED, null, "core"
                );

        List<MethodCallInfo> calls = List.of(
                new MethodCallInfo("A", "run", "B", "step"),
                new MethodCallInfo("B", "step", "A", "run")
        );

        List<FlowPath> flows =
                flowEngine.analyze(
                        List.of(entryPoint), calls, List.of(), List.of(),
                        List.of()
                );

        FlowPath flow = flows.get(0);

        assertThat(flow.isTruncated()).isFalse();
        assertThat(flow.getSteps()).hasSize(2);
    }

    @Test
    void producesEmptyFlowWhenEntryPointHasNoOutgoingCalls() {

        EntryPointInfo entryPoint =
                new EntryPointInfo(
                        "LonelyController", "com.acme", "ping",
                        TriggerType.GET, "/ping", "core"
                );

        List<FlowPath> flows =
                flowEngine.analyze(
                        List.of(entryPoint), List.of(), List.of(), List.of(),
                        List.of()
                );

        FlowPath flow = flows.get(0);

        assertThat(flow.getSteps()).isEmpty();
        assertThat(flow.getDatabaseOperations()).isEmpty();
        assertThat(flow.getEntityMutations()).isEmpty();
        assertThat(flow.isTruncated()).isFalse();
    }
}

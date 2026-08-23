package org.example.analyser.analyzer;

import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.EntityMutationInfo;
import org.example.analyser.model.EntryPointInfo;
import org.example.analyser.model.FlowPath;
import org.example.analyser.model.MethodCallInfo;
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
                        "POST", "/orders", "order"
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
                        List.of(entryPoint), calls, crud, List.of()
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
    void stopsAtAnInterfaceBoundaryWhenTheCallGraphResolvesToTheInterfaceNotTheImpl() {

        // Same shape as the real-world case documented on
        // FlowEngine: a call is recorded against the field's
        // declared (interface) type, but the callee's own
        // outgoing calls are recorded under its implementation
        // class's name. The walk can't bridge that gap - it's a
        // limitation of the underlying call-graph data, not
        // something FlowEngine can resolve on its own.
        EntryPointInfo entryPoint =
                new EntryPointInfo(
                        "OrderController", "com.acme.order", "create",
                        "POST", "/orders", "order"
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
                        List.of(entryPoint), calls, List.of(), List.of()
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
                        "POST", "/orders", "order"
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
                        "UPDATE", "order_tbl"
                )
        );

        List<FlowPath> flows =
                flowEngine.analyze(
                        List.of(entryPoint), calls, crud, mutations
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
                        "SCHEDULED", null, "core"
                );

        List<MethodCallInfo> calls = List.of(
                new MethodCallInfo("A", "run", "B", "step"),
                new MethodCallInfo("B", "step", "A", "run")
        );

        List<FlowPath> flows =
                flowEngine.analyze(
                        List.of(entryPoint), calls, List.of(), List.of()
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
                        "GET", "/ping", "core"
                );

        List<FlowPath> flows =
                flowEngine.analyze(
                        List.of(entryPoint), List.of(), List.of(), List.of()
                );

        FlowPath flow = flows.get(0);

        assertThat(flow.getSteps()).isEmpty();
        assertThat(flow.getDatabaseOperations()).isEmpty();
        assertThat(flow.getEntityMutations()).isEmpty();
        assertThat(flow.isTruncated()).isFalse();
    }
}

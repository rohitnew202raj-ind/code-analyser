package org.example.analyser.analyzer;

import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.CrudOperationInfo;
import org.example.analyser.model.MethodCallInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrudAnalyzerTest {

    private final CrudAnalyzer crudAnalyzer = new CrudAnalyzer();

    @Test
    void existsByIdIsClassifiedAsReadNotDropped() {

        ClassInfo repository = repository("OrderRepository", "OrderEntity");

        List<CrudOperationInfo> operations =
                crudAnalyzer.analyze(
                        List.of(
                                new MethodCallInfo(
                                        "OrderService",
                                        "cancel",
                                        "OrderRepository",
                                        "existsById"
                                )
                        ),
                        List.of(repository)
                );

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).getOperation()).isEqualTo("READ");
    }

    @Test
    void unrecognizedRepositoryMethodBecomesCustomQueryInsteadOfDropped() {

        ClassInfo repository = repository("OrderRepository", "OrderEntity");

        List<CrudOperationInfo> operations =
                crudAnalyzer.analyze(
                        List.of(
                                new MethodCallInfo(
                                        "OrderService",
                                        "cancel",
                                        "OrderRepository",
                                        "markAsShipped"
                                )
                        ),
                        List.of(repository)
                );

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).getOperation())
                .isEqualTo("CUSTOM_QUERY");
    }

    @Test
    void prefersResolvedRepositoryEntityTypeOverNameGuessing() {

        // Repository name would guess "Orders" (not a real
        // entity), but repositoryEntityType was already
        // resolved correctly upstream.
        ClassInfo repository =
                repository("OrdersDao", "OrderEntity");

        List<CrudOperationInfo> operations =
                crudAnalyzer.analyze(
                        List.of(
                                new MethodCallInfo(
                                        "OrderService",
                                        "place",
                                        "OrdersDao",
                                        "save"
                                )
                        ),
                        List.of(repository)
                );

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).getEntityClass())
                .isEqualTo("OrderEntity");
    }

    @Test
    void propagatesInsideLoopFromTheOriginatingMethodCall() {

        ClassInfo repository = repository("OrderRepository", "OrderEntity");

        MethodCallInfo call = new MethodCallInfo(
                "OrderService", "processAll", "OrderRepository", "findById"
        );
        call.setInsideLoop(true);

        List<CrudOperationInfo> operations =
                crudAnalyzer.analyze(List.of(call), List.of(repository));

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).isInsideLoop()).isTrue();
    }

    private ClassInfo repository(String name, String entityType) {

        ClassInfo repository = new ClassInfo();
        repository.setName(name);
        repository.setType("REPOSITORY");
        repository.setRepositoryEntityType(entityType);
        return repository;
    }
}

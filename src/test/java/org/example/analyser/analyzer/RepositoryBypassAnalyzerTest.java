package org.example.analyser.analyzer;

import org.example.analyser.model.ArchitectureFinding;
import org.example.analyser.model.ArchitectureFindingType;
import org.example.analyser.model.ClassInfo;
import org.example.analyser.model.DependencyInfo;
import org.example.analyser.model.DependencyType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryBypassAnalyzerTest {

    private final RepositoryBypassAnalyzer analyzer =
            new RepositoryBypassAnalyzer();

    @Test
    void flagsAControllerDependingDirectlyOnARepository() {

        ClassInfo controller = new ClassInfo();
        controller.setName("OrderInternalController");
        controller.getRoles().add("REST_CONTROLLER");

        DependencyInfo dependency = new DependencyInfo(
                "OrderInternalController", "OrderRepository",
                "repository", DependencyType.REPOSITORY_DEPENDENCY
        );

        List<ArchitectureFinding> findings =
                analyzer.analyze(
                        List.of(dependency), List.of(controller)
                );

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getType())
                .isEqualTo(ArchitectureFindingType.REPOSITORY_BYPASS);
        assertThat(findings.get(0).getClasses())
                .containsExactly(
                        "OrderInternalController", "OrderRepository"
                );
    }

    @Test
    void doesNotFlagAServiceDependingOnARepository() {

        ClassInfo service = new ClassInfo();
        service.setName("OrderServiceImpl");
        service.getRoles().add("SERVICE");

        DependencyInfo dependency = new DependencyInfo(
                "OrderServiceImpl", "OrderRepository",
                "repository", DependencyType.REPOSITORY_DEPENDENCY
        );

        List<ArchitectureFinding> findings =
                analyzer.analyze(
                        List.of(dependency), List.of(service)
                );

        assertThat(findings).isEmpty();
    }

    @Test
    void doesNotFlagAControllerDependingOnAService() {

        ClassInfo controller = new ClassInfo();
        controller.setName("OrderController");
        controller.getRoles().add("REST_CONTROLLER");

        DependencyInfo dependency = new DependencyInfo(
                "OrderController", "OrderService",
                "service", DependencyType.SERVICE_DEPENDENCY
        );

        List<ArchitectureFinding> findings =
                analyzer.analyze(
                        List.of(dependency), List.of(controller)
                );

        assertThat(findings).isEmpty();
    }
}

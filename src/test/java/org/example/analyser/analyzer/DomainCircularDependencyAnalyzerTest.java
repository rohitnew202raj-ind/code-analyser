package org.example.analyser.analyzer;

import org.example.analyser.model.DependencyType;
import org.example.analyser.model.DomainCycle;
import org.example.analyser.model.DomainDependency;
import org.example.analyser.model.DomainInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DomainCircularDependencyAnalyzerTest {

    private final DomainCircularDependencyAnalyzer analyzer =
            new DomainCircularDependencyAnalyzer();

    @Test
    void detectsATwoDomainCycle() {

        List<DomainInfo> domains =
                List.of(new DomainInfo("address"), new DomainInfo("category"));

        List<DomainDependency> dependencies = List.of(
                new DomainDependency(
                        "address", "category", DependencyType.SERVICE_DEPENDENCY
                ),
                new DomainDependency(
                        "category", "address", DependencyType.SERVICE_DEPENDENCY
                )
        );

        List<DomainCycle> cycles =
                analyzer.analyze(domains, dependencies);

        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0).getDomains())
                .containsExactlyInAnyOrder("address", "category");
    }

    @Test
    void doesNotFlagALinearDomainChainAsCircular() {

        List<DomainInfo> domains = List.of(
                new DomainInfo("order"),
                new DomainInfo("payment"),
                new DomainInfo("inventory")
        );

        List<DomainDependency> dependencies = List.of(
                new DomainDependency(
                        "order", "payment", DependencyType.SERVICE_DEPENDENCY
                ),
                new DomainDependency(
                        "payment", "inventory", DependencyType.SERVICE_DEPENDENCY
                )
        );

        List<DomainCycle> cycles =
                analyzer.analyze(domains, dependencies);

        assertThat(cycles).isEmpty();
    }
}
